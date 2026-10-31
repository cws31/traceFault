# TraceFault — Demonstration & Recording Guide (15–20 Minutes)

This step-by-step presentation script is tailored for a smooth, professional 15–20 minute software engineering and reliability demonstration.

---

## 1. Demonstration Timing & Sequence Overview

| Timestamp | Phase | Topic | Objective |
| :--- | :--- | :--- | :--- |
| **00:00 - 02:30** | Introduction & Context | Distributed Systems Failure Mode | Explain at-least-once retry dilemma & asymmetric network boundaries |
| **02:30 - 04:30** | Architecture & Code Walkthrough | Layered Design & Persistence Boundary | Show `PaymentPersistenceService` vs `FailureSimulationService` |
| **04:30 - 06:30** | Wireshark Packet Capture Setup | Network Tooling Preparation | Bind loopback adapter, set HTTP filter on port 8080 |
| **06:30 - 10:00** | Vulnerability Demonstration (Before Fix) | Reproducing Duplicate Payments | Send delayed request (timeout), retry, demonstrate 2 DB records created |
| **10:00 - 13:00** | Root Cause & Network vs DB Evidence | Correlating Network & Logs | Show client timeout in Wireshark vs committed row in H2 |
| **13:00 - 16:30** | Corrective Implementation (After Fix) | Enabling Idempotency Protection | Enable idempotency, repeat experiment, verify deduplication & 409 conflict |
| **16:30 - 18:30** | Automated Test Suite Run | Rigorous Verification | Execute `./mvnw test` showing all 12 tests passing |
| **18:30 - 20:00** | Conclusion & Key Takeaways | Architectural Principles | Summarize 2-tier defense: application lookup + DB unique constraint |

---

## 2. Phase-by-Phase Presenter Script & Exact Commands

### Phase 1: Introduction (00:00 - 02:30)

**Presenter Talking Points:**
> "In distributed architectures, payments require strict reliability. When a client sends a payment request, network degradation can cause the response to be lost or delayed. The client times out, assumes the payment failed, and retries. If the server is not idempotent, the customer is charged twice. Today we demonstrate TraceFault: we will reproduce this exact failure, inspect packet and database evidence, fix it using a two-tier idempotency defense, and prove the recovery."

---

### Phase 2: Start Application (02:30 - 04:30)

Open Terminal 1. Start the Spring Boot application:

**Windows PowerShell:**
```powershell
.\mvnw.cmd spring-boot:run
```

**macOS / Linux:**
```bash
./mvnw spring-boot:run
```

Verify startup output:
```
Started TraceFaultApplication in ... seconds
Dropping database unique index 'uk_payment_idempotency_key' to allow duplicate payment demonstration
```

---

### Phase 3: Wireshark Packet Capture Setup (04:30 - 06:30)

1. Launch **Wireshark**.
2. Select the loopback interface:
   - **Windows:** `Npcap Loopback Adapter` or `Adapter for loopback traffic`
   - **macOS / Linux:** `Loopback: lo0` or `lo`
3. Enter display filter:
   ```
   tcp.port == 8080 && http
   ```
4. Click **Start Capture** (blue shark fin icon).

---

### Phase 4: Reproduce the Failure — Before Fix (06:30 - 10:00)

Open Terminal 2.

#### Step A: Reset demonstration data
**Windows PowerShell:**
```powershell
curl.exe -s -X POST http://localhost:8080/api/demo/reset
```
**macOS / Linux:**
```bash
curl -s -X POST http://localhost:8080/api/demo/reset
```
*Expected Output:*
```json
{"status":"SUCCESS","message":"Demonstration data successfully reset.","idempotencyEnabled":false}
```

#### Step B: Send first payment with 3000ms server delay & 1000ms client timeout
**Windows PowerShell:**
```powershell
curl.exe --max-time 1 -s -X POST http://localhost:8080/api/payments `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: KEY-DEMO-001" `
  -H "X-Demo-Delay-Response-Ms: 3000" `
  -d '{"orderId":"ORDER-101","amount":1000.00,"currency":"INR"}'
```
**macOS / Linux:**
```bash
curl --max-time 1 -s -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: KEY-DEMO-001" \
  -H "X-Demo-Delay-Response-Ms: 3000" \
  -d '{"orderId":"ORDER-101","amount":1000.00,"currency":"INR"}'
```
*Expected Output:*
The command hangs for 1 second, then exits with timeout (curl code 28).

**Presenter Note:**
> "Notice the client timed out after 1 second. The client thinks the request failed. But let's check our server logs."
Point to Terminal 1 logs showing:
```
Committed payment [id=1, orderId='ORDER-101', amount=1000.00 INR, idempotencyKey='KEY-DEMO-001'] to database
=== [FAILURE INJECTION] Delaying HTTP response by 3000 ms AFTER database commit ===
```

#### Step C: Wait 3 seconds for server delay to finish

#### Step D: Client retries the identical payment without delay header
**Windows PowerShell:**
```powershell
curl.exe -s -X POST http://localhost:8080/api/payments `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: KEY-DEMO-001" `
  -d '{"orderId":"ORDER-101","amount":1000.00,"currency":"INR"}'
```
**macOS / Linux:**
```bash
curl -s -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: KEY-DEMO-001" \
  -d '{"orderId":"ORDER-101","amount":1000.00,"currency":"INR"}'
```
*Expected Output:*
```json
{"id":2,"orderId":"ORDER-101","amount":1000.00,"currency":"INR","idempotencyKey":"KEY-DEMO-001","paymentStatus":"SUCCESS","duplicate":false}
```

#### Step E: Inspect the incident damage
**Windows PowerShell & macOS / Linux:**
```bash
curl.exe -s http://localhost:8080/api/payments
curl.exe -s http://localhost:8080/api/payments/statistics
```
*Expected Output:*
```json
{"totalRecords":2,"successfulPayments":2,"totalProcessedAmount":2000.00}
```

**Presenter Note:**
> "Look at that: 2 payments exist with IDs 1 and 2 for the exact same order ORDER-101. The customer was charged 2000.00 INR instead of 1000.00 INR. This proves the vulnerability."

---

### Phase 5: Correlate Evidence (10:00 - 13:00)

1. Switch to **Wireshark**:
   - Highlight the first POST request.
   - Point out the 1-second gap followed by client FIN/RST packet.
   - Point out the second POST request arriving ~3.5 seconds later.
2. Open Browser to H2 Console:
   - URL: `http://localhost:8080/h2-console`
   - JDBC URL: `jdbc:h2:mem:tracefaultdb` (User: `sa`, Password: *empty*)
   - Execute: `SELECT * FROM PAYMENTS;`
   - Show the two separate rows with identical `idempotency_key = 'KEY-DEMO-001'`.
3. Presenter explains the distinction between **Network Evidence** and **Database Evidence**:
   > "Packet captures prove what happened on the wire, but they do NOT prove whether a database transaction committed or rolled back. Forensic investigations require checking both."

---

### Phase 6: Demonstrate the Correction — After Fix (13:00 - 16:30)

#### Step F: Enable Idempotency Protection
**Windows PowerShell & macOS / Linux:**
```bash
curl.exe -s -X POST "http://localhost:8080/api/demo/mode?idempotencyEnabled=true"
curl.exe -s -X POST http://localhost:8080/api/demo/reset
```
*Server Log Confirms:*
```
Enforcing database unique index 'uk_payment_idempotency_key' on payments(idempotency_key)
Enforced database unique index 'uk_payment_idempotency_key' successfully.
```

#### Step G: Repeat the exact same failure scenario
**Windows PowerShell:**
```powershell
curl.exe --max-time 1 -s -X POST http://localhost:8080/api/payments `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: KEY-DEMO-002" `
  -H "X-Demo-Delay-Response-Ms: 3000" `
  -d '{"orderId":"ORDER-102","amount":1500.00,"currency":"INR"}'
```
*Client times out after 1 second.*  
Wait 3 seconds.

Now send the retry:
```powershell
curl.exe -s -X POST http://localhost:8080/api/payments `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: KEY-DEMO-002" `
  -d '{"orderId":"ORDER-102","amount":1500.00,"currency":"INR"}'
```
*Expected Output:*
```json
{"id":3,"orderId":"ORDER-102","amount":1500.00,"currency":"INR","idempotencyKey":"KEY-DEMO-002","paymentStatus":"SUCCESS","duplicate":true}
```
Notice: `"duplicate": true` and `"id": 3` (re-uses existing record!).

#### Step H: Verify Database State
```bash
curl.exe -s http://localhost:8080/api/payments/statistics
```
*Expected Output:*
```json
{"totalRecords":1,"successfulPayments":1,"totalProcessedAmount":1500.00}
```
Only 1 transaction recorded, INR 1500.00 total.

#### Step I: Demonstrate Payload Tampering / Conflict Protection (HTTP 409)
```powershell
curl.exe -s -X POST http://localhost:8080/api/payments `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: KEY-DEMO-002" `
  -d '{"orderId":"ORDER-TAMPERED","amount":9999.00,"currency":"INR"}'
```
*Expected Output:*
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Idempotency key 'KEY-DEMO-002' was already used for a different payment payload. Existing: [orderId=ORDER-102, amount=1500.00 INR], Requested: [orderId=ORDER-TAMPERED, amount=9999.00 INR]"
}
```

---

### Phase 7: Automated Test Suite (16:30 - 18:30)

Run the full automated test suite:

**Windows PowerShell:**
```powershell
.\mvnw.cmd test
```

**macOS / Linux:**
```bash
./mvnw test
```

*Expected Output:*
```
[INFO] Results:
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Point out:
- Test 2 verifies duplicate reproduction in vulnerable mode.
- Test 3 verifies deduplication in corrected mode.
- Test 5 verifies HTTP 409 payload mismatch protection.
- Test 6 verifies concurrency safety across 10 simultaneous threads.

---

### Phase 8: Conclusion & Best Practices (18:30 - 20:00)

**Presenter Wrap-Up:**
1. **Never rely on client-side state alone.** Network timeouts are ambiguous.
2. **Commit before delay reproduces real-world response loss.**
3. **Two-tier idempotency defense**: Always combine memory/cache lookup with database-level unique constraints to eliminate concurrent race conditions.
