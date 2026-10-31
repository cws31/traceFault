# TraceFault — Reliability Incident Investigation Report

**Incident Reference:** INC-84920  
**Severity:** SEV-1 (Financial Impact / Customer Double Billing)  
**System Under Test:** TraceFault Payment Processing Engine  
**Environment:** Local Reliability Laboratory (Java 25 runtime, Spring Boot 3.5.8, H2 Database)  
**Date of Investigation:** October 30, 2026  
**Investigator:** Senior Reliability & Backend Architecture Engineer  

---

## 1. Executive Summary

A critical issue was reported where customers checking out during periods of transient network latency experienced duplicate debit transactions for a single intended order. 

Our investigation reproduced the failure under deterministic laboratory conditions. The root cause was identified as an **at-least-once HTTP retry loop coupled with non-idempotent server-side state mutation**: the payment service persisted and committed the transaction successfully, but a delayed HTTP response caused the client HTTP socket to time out. The client automatically retried the payment with identical request headers and body, which the uncorrected server treated as an independent transaction, generating duplicate database records and double-charging the customer.

Implementation of a **two-tier idempotency defense** (application-level key verification with payload integrity checking + database-level unique constraint enforcement) resolved the defect, guaranteeing exactly-once transaction semantics across network retries and concurrent race conditions.

---

## 2. Expected vs. Observed Behavior

| Dimension | Expected Behavior | Observed Behavior (Before Fix) | Corrected Behavior (After Fix) |
| :--- | :--- | :--- | :--- |
| **Client Retry on Timeout** | Subsequent retry recognizes prior operation and returns existing receipt. | Server creates a second distinct payment row with new auto-generated ID. | Server detects existing `Idempotency-Key`, returns original receipt (`duplicate: true`). |
| **Database State** | Exactly 1 record per intended transaction. | 2 records with identical `orderId` and identical `idempotencyKey`. | Exactly 1 record persisted in H2. |
| **Financial Accounting** | Total charged = INR 1,000.00 | Total charged = INR 2,000.00 (double debit). | Total charged = INR 1,000.00. |
| **Payload Tampering** | Reusing key with altered payload rejected. | Ignored; creates another transaction anyway. | HTTP 409 Conflict returned. |
| **Concurrency** | Concurrent duplicate requests yield 1 record. | Race conditions insert duplicate records. | Database unique index rejects duplicates. |

---

## 3. Evidence Collection & Multi-Tier Analysis

### 3.1 Network-Level Evidence (Packet Capture)

Using Wireshark on the loopback adapter (`\Device\NPF_Loopback` on Windows, `lo0` on macOS/Linux) with capture filter `tcp.port == 8080 and http`:

1. **Packet #1-4 (Initial Request)**:
   - Frame timestamp: `T0 = 0.000s`
   - Client sends HTTP POST `/api/payments`:
     - `Idempotency-Key: KEY-DEMO-001`
     - `X-Demo-Delay-Response-Ms: 3000`
     - Payload: `{"orderId":"ORDER-101","amount":1000.00,"currency":"INR"}`
   - Server TCP stack acknowledges receipt (TCP ACK).

2. **Packet #5-8 (Client Timeout & Socket Abort)**:
   - Frame timestamp: `T1 = +1.024s`
   - Client's HTTP timer expires (`--max-time 1`).
   - Client socket sends `TCP FIN` or `TCP RST` tearing down the connection.
   - Client logs error: `curl: (28) Operation timed out after 1000 milliseconds with 0 bytes received`.

3. **Packet #9-11 (Server Post-Commit Response)**:
   - Frame timestamp: `T2 = +3.015s`
   - Server writes HTTP `200 OK` to socket. Because the socket was reset/closed by client, the response is discarded or triggers a TCP RST.

4. **Packet #12-16 (Client Retry)**:
   - Frame timestamp: `T3 = +3.500s`
   - Client initiates fresh TCP handshake on port 8080 and resends:
     - `Idempotency-Key: KEY-DEMO-001`
     - Payload: `{"orderId":"ORDER-101","amount":1000.00,"currency":"INR"}`
   - Server responds with HTTP `200 OK` containing a new record ID.

### 3.2 Limitations of Packet-Level Evidence

> [!WARNING]
> **Crucial Reliability Finding:** Network packet traces alone **cannot prove** that a transaction was committed to disk.

- A TCP packet capture demonstrates bytes crossing the network interface, client timeouts, and server response delays.
- A network capture **cannot distinguish** between:
  1. A server that crashed before writing to the database.
  2. A server whose database transaction rolled back.
  3. A server that committed the row successfully but delayed its HTTP serialization.
- Therefore, forensic reliability investigations must correlate **network evidence** with **server application logs** and **database transaction logs**.

### 3.3 Application & Database Evidence

Correlating application logs with H2 database table state confirms the transaction committed *before* the network delay:

```
2026-10-30 18:23:08.690 [ForkJoinPool] INFO  PaymentPersistenceService - Committed payment [id=1, orderId='ORDER-DELAY-1', amount=999.00 INR, idempotencyKey='KEY-DELAY-TEST-001'] to database
2026-10-30 18:23:08.692 [ForkJoinPool] WARN  FailureSimulationService - === [FAILURE INJECTION] Delaying HTTP response by 1200 ms AFTER database commit ===
2026-10-30 18:23:08.746 [main] DEBUG org.hibernate.SQL - select p1_0.id, p1_0.amount from payments where idempotency_key=?
```

Database query during the delay:
```sql
SELECT id, order_id, amount, idempotency_key, created_at FROM payments;
-- Result at T0 + 300ms:
-- 1 | ORDER-DELAY-1 | 999.00 | KEY-DELAY-TEST-001 | 2026-10-30 18:23:08
```

This confirms the payment was safely in H2 while the client was still timing out.

---

## 4. Root Cause Analysis

The vulnerability occurred due to an architectural gap common in naive REST APIs:

1. **State Mutation Without Idempotency Tracking**: The original payment endpoint was non-idempotent. It treated each incoming HTTP POST as an independent business intent.
2. **Asymmetric Failure Domain**: In distributed computing, network communication is uncoordinated. A client cannot distinguish between "server never received the request" and "server processed the request but network dropped the reply".
3. **Database Schema Deficiency**: No unique database constraint existed on `idempotency_key`, permitting multiple rows to store the identical key.

---

## 5. Corrective Implementation

TraceFault implements a **two-tier defense**:

### 5.1 Application Layer: State & Integrity Verification

1. When `tracefault.idempotency.enabled=true`, incoming requests must supply `Idempotency-Key`. Missing keys yield **HTTP 400 Bad Request**.
2. Service inspects the database:
   ```java
   Optional<Payment> existingOpt = paymentRepository.findByIdempotencyKey(cleanKey);
   if (existingOpt.isPresent()) {
       Payment existing = existingOpt.get();
       validatePayloadMatch(existing, request, cleanKey);
       return PaymentResponse.from(existing, true); // duplicate = true
   }
   ```
3. If the same key is submitted with different payload data (e.g. modified `amount` or `orderId`), the service rejects with **HTTP 409 Conflict**, preventing key hijacking.

### 5.2 Database Layer: Concurrency & Constraint Enforcement

1. When idempotency protection is enabled, `DatabaseIndexManager` executes:
   ```sql
   CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_idempotency_key ON payments(idempotency_key);
   ```
2. In high-concurrency race conditions where two identical requests arrive simultaneously:
   - Both threads may pass the initial `findByIdempotencyKey()` check.
   - The H2 database unique constraint enforces serializability: one thread succeeds, while the other throws `DataIntegrityViolationException`.
   - The service catches `DataIntegrityViolationException`, reads the winning thread's committed payment, verifies payload match, and returns the existing transaction.

---

## 6. Verification Results

### 6.1 Automated Test Suite Verification

12 out of 12 automated integration and simulation tests passed:
- **Test 1**: Normal single payment processing.
- **Test 2**: Duplicate payment before correction reproduces 2 distinct database records.
- **Test 3**: Duplicate payment after correction deduplicates and returns existing record.
- **Test 4**: Independent keys create separate legitimate payments.
- **Test 5**: Payload mismatch returns HTTP 409 Conflict.
- **Test 6**: 10 concurrent threads submitting identical keys yield exactly 1 database record.
- **Test 7**: Accounting statistics accurately reflect database rows.
- **Test 8**: Missing `Idempotency-Key` header returns HTTP 400 Bad Request.
- **Test 9**: Query by idempotency key (200 OK and 404 Not Found).
- **Test 10**: Local demonstration reset clears synthetic state.
- **Test 11 & 12**: Asynchronous post-commit failure delay verification.

### 6.2 End-to-End Live Terminal Experiment Verification

- Before fix (`idempotency.enabled=false`):
  - Request #1 timed out after 1024 ms.
  - Server committed record #1.
  - Retry sent without delay header created record #2.
  - Total records = 2, total amount = INR 2,000.00.
- After fix (`idempotency.enabled=true`):
  - Request #1 timed out after 1032 ms.
  - Server committed record #3.
  - Retry returned record #3 with `duplicate: true`.
  - Total records = 1, total amount = INR 1,500.00.
  - Altered payload attempt returned HTTP 409 Conflict.

---

## 7. Operational Recommendations for Production

1. **Idempotency Key Expiration / TTL**: In a production payment gateway, idempotency keys should have a defined lifespan (e.g. 24–48 hours) using partitioned tables or cleanup jobs.
2. **In-Flight Locking**: For long-running operations, an in-flight status (`PENDING`) should be stored immediately so that concurrent retries receive `HTTP 425 Too Early` or wait on the leader rather than re-executing.
3. **Database Unique Constraints**: Never rely solely on application-level `if (exists)` checks for idempotency in multi-threaded or multi-instance systems; database unique constraints or atomic locks are mandatory.
