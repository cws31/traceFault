# TraceFault — Architecture & Reliability Design

## 1. System Overview

**TraceFault** is an engineering prototype designed to reproduce, investigate, correct, and verify a classic distributed system reliability failure: **duplicate transaction processing caused by post-commit response loss followed by a client retry**.

In distributed payment workflows, network boundaries separate the payment consumer (client/checkout service) and the payment processor (server). When a server completes business logic and commits state to a database, any subsequent network degradation, firewall drop, or server delay before the HTTP response reaches the client results in a **client-side timeout**. Because the client cannot distinguish between a server failure *before* processing and a response failure *after* processing, it retries the request, leading to duplicate debits if idempotency is absent.

```mermaid
flowchart TD
    subgraph Client["HTTP Client / Checkout UI"]
        C1["Initiate Payment (POST /api/payments)"]
        C2["Client Timeout (e.g. 1000ms)"]
        C3["Retry Same Request"]
    end

    subgraph TraceFault["TraceFault Payment Service"]
        P1["PaymentController"]
        P2["Idempotency Protection Layer"]
        P3["PaymentPersistenceService (REQUIRES_NEW)"]
        P4["FailureSimulationService (Post-Commit Delay)"]
    end

    subgraph Database["Embedded H2 Database"]
        DB[("payments table<br/>Unique Constraint: uk_payment_idempotency_key")]
    end

    C1 -->|"1. POST (Idempotency-Key: KEY-01)"| P1
    P1 --> P2
    P2 -->|"2. Check / Persist"| P3
    P3 -->|"3. Commit Row"| DB
    P3 -->|"4. Transaction Committed"| P4
    P4 -.->|"5. Sleep (e.g. 3000ms)"| P4
    C2 -.->|"6. Timeout Reached (1000ms)"| C2
    C3 -->|"7. Retry (Idempotency-Key: KEY-01)"| P1
```

---

## 2. Layered Component Architecture

TraceFault follows clean architectural separation of concerns:

```
com.tracefault/
├── config/
│   ├── TraceFaultProperties.java     # Configuration binding (tracefault.idempotency.enabled)
│   └── DatabaseIndexManager.java     # Synchronizes H2 unique index with active mode
├── controller/
│   ├── PaymentController.java        # REST endpoints for payment submission, list, stats
│   └── DemoController.java           # Demo reset, mode toggling (!prod profile only)
├── dto/
│   ├── PaymentRequest.java           # Inbound payload validation (Jakarta Validation)
│   ├── PaymentResponse.java          # Outbound representation with duplicate flag
│   ├── PaymentStatisticsResponse.java# Aggregated transaction metrics
│   └── ErrorResponse.java            # Standardized RFC 7807-inspired error payload
├── entity/
│   ├── Payment.java                  # JPA Entity (payments table)
│   └── PaymentStatus.java            # Transaction status enum (SUCCESS, PENDING, FAILED)
├── exception/
│   ├── GlobalExceptionHandler.java   # Centralized RestControllerAdvice
│   ├── PaymentConflictException.java # HTTP 409 for payload mismatches
│   ├── MissingIdempotencyKeyException.java # HTTP 400 for omitted keys
│   └── PaymentNotFoundException.java # HTTP 404 for missing records
├── repository/
│   └── PaymentRepository.java        # Spring Data JPA repository with aggregation queries
└── service/
    ├── PaymentService.java           # Orchestrator: Idempotency enforcement & business logic
    ├── PaymentPersistenceService.java# Dedicated transactional boundary (REQUIRES_NEW)
    └── FailureSimulationService.java # Post-commit delay injection
```

---

## 3. Transaction Boundary & Controlled Failure Injection

A critical requirement of reproducing this distributed failure is ensuring that:
1. The payment record is **fully committed** to the database.
2. The delay or failure occurs **after** commit, not before.

If `Thread.sleep()` were executed inside a `@Transactional` boundary, the database transaction would remain uncommitted during the delay, and any connection drop would trigger a rollback.

TraceFault solves this cleanly by decoupling transaction persistence from response generation:

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as PaymentController
    participant Service as PaymentService
    participant Persistence as PaymentPersistenceService
    participant DB as H2 Database (payments)
    participant Simulation as FailureSimulationService

    Client->>Controller: POST /api/payments<br/>X-Demo-Delay-Response-Ms: 3000<br/>Timeout: 1000ms
    Controller->>Service: processPayment(request, key, delayMs)
    Service->>Persistence: createAndPersistPayment(request, key)
    activate Persistence
    Note over Persistence,DB: @Transactional(REQUIRES_NEW)
    Persistence->>DB: INSERT into payments + flush
    Persistence-->>Service: Payment Entity (COMMITTED)
    deactivate Persistence

    Service->>Simulation: simulateDelayIfNeeded(3000ms)
    activate Simulation
    Note over Simulation: Post-Commit Delay Active
    Note over Client: Client Timeout (1000ms expired!)
    Client--xController: HTTP Client Connection Timeout
    Simulation-->>Service: Delay Complete (3000ms)
    deactivate Simulation

    Service-->>Controller: PaymentResponse
    Controller-->>Client: HTTP 200 (Dropped / Lost by timed-out client)
```

---

## 4. Before-Fix Flow: Vulnerable Mode (`tracefault.idempotency.enabled=false`)

In vulnerable mode:
1. No unique database index exists on `idempotency_key`.
2. The service performs no idempotency lookup.
3. Every incoming request executes `PaymentPersistenceService.createAndPersistPayment()`.
4. When the client times out on request #1 and retries as request #2, **two distinct payment records** are persisted for one customer order.
5. Total processed amount doubles.

```mermaid
flowchart TD
    R1["Request #1: Idempotency-Key: KEY-01"] --> P1["Persist Payment (ID: 1, Amount: 1000.00)"]
    P1 --> C1["Transaction Committed to H2"]
    C1 --> D1["Delay 3000ms (Client times out after 1000ms)"]
    D1 -.-> R2["Client Retries: Request #2 (Same Key: KEY-01)"]
    R2 --> P2["Persist Payment (ID: 2, Amount: 1000.00)"]
    P2 --> C2["Transaction Committed to H2"]
    C2 --> BUG["INCIDENT: 2 Database Records for 1 Order<br/>Customer Billed Twice: 2000.00 INR"]
```

---

## 5. After-Fix Flow: Corrected Mode (`tracefault.idempotency.enabled=true`)

The corrected architecture implements a **two-tier idempotency defense**:

1. **Application-Level Tier (Read & Match)**:
   - Queries `paymentRepository.findByIdempotencyKey(key)`.
   - If an existing payment is found:
     - **Payload Validation**: Verifies `orderId`, `amount`, and `currency` match the original transaction.
     - If payload matches: Returns existing payment response immediately with flag `duplicate: true`.
     - If payload differs: Rejects with **HTTP 409 Conflict** (`PaymentConflictException`).

2. **Database-Level Tier (Unique Index & Concurrency Defense)**:
   - A unique constraint `uk_payment_idempotency_key` is enforced on table `payments (idempotency_key)`.
   - If two identical requests race concurrently across worker threads:
     - The first thread acquires the insert lock and commits.
     - The second thread receives `DataIntegrityViolationException`.
     - `PaymentService` catches this violation, re-queries the committed transaction, verifies payload match, and returns the existing record.

```mermaid
flowchart TD
    Req["Incoming Payment Request (Idempotency-Key: KEY)"] --> CheckHeader{"Key present?"}
    CheckHeader -- No --> Err400["HTTP 400 Bad Request<br/>Idempotency-Key header is required"]
    CheckHeader -- Yes --> Lookup{"Record exists for KEY?"}

    Lookup -- Yes --> ValidatePayload{"Does payload match?<br/>orderId, amount, currency"}
    ValidatePayload -- No --> Err409["HTTP 409 Conflict<br/>Key reused with different payload"]
    ValidatePayload -- Yes --> ReturnCached["HTTP 200 OK<br/>Return existing record (duplicate: true)"]

    Lookup -- No --> Insert["Persist to Database"]
    Insert --> CatchRace{"Race Condition?<br/>Unique Constraint Caught"}
    CatchRace -- Yes --> QueryAgain["Fetch winning transaction & return"]
    CatchRace -- No --> Delay{"Delay header present?"}
    Delay -- Yes --> Sleep["Simulate Post-Commit Delay"]
    Delay -- No --> ReturnFresh["HTTP 200 OK<br/>Return fresh record (duplicate: false)"]
```
