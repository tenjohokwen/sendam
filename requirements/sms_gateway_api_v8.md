---

# **SMS Gateway API — V8 Specification**

Provider: **Nexah**

---

# **1\. System Overview**

## **1.1 Purpose**

The SMS Gateway is a secure multi-tenant API that:

* Wraps the **Nexah SMS REST API**  
* Provides **idempotent SMS submission**  
* Maintains **per-recipient lifecycle tracking**  
* Enforces **strict financial correctness**  
* Guarantees **no negative balances**  
* Maintains **audit-grade ledgers**  
* Performs **provider balance reconciliation**  
* Ensures **deterministic message lifecycle**

The system currently supports **one upstream provider (Nexah)**.  
No routing or failover logic is implemented in this version.

---

# **2\. Core Design Guarantees**

The system guarantees:

1. **No duplicate SMS submissions**  
2. **No negative client balances**  
3. **Financial correctness based on provider-authoritative cost**  
4. **Deterministic lifecycle transitions**  
5. **Idempotent webhook processing**  
6. **Crash-safe queue processing**  
7. **Provider cost drift detection**  
8. **Traceable end-to-end request flow**

---

# **3\. Core Identifiers & Request Tracing**

To ensure **full observability**, every message flow includes trace identifiers.

## **3.1 Identifiers**

| Field | Scope | Description |
| ----- | ----- | ----- |
| requestId | API request | Unique ID generated per API request |
| sendRequestId | Client | Client-provided idempotency key |
| gateway\_message\_id | Internal | Unique ID generated per message |
| provider\_message\_id | Provider | Nexah messageid |

## **3.2 Gateway Message ID Generation**

`gateway_message_id` generation rules:

* Format: **UUIDv7 or ULID**  
* Generated **per recipient**  
* Globally unique  
* Time-sortable

Used for:

* tracing  
* customer support  
* reconciliation  
* cross-provider routing (future capability)

## **3.3 Logging Requirements**

All logs must include:

requestId  
sendRequestId  
gateway\_message\_id  
provider\_message\_id  
client\_id

This guarantees **traceability across the full pipeline**.

---

# **4\. System Limits & Capacity Controls**

To prevent overload, the gateway enforces global limits.

| Limit | Value |
| ----- | ----- |
| max\_recipients\_per\_request | 10,000 |
| max\_pending\_messages | configurable |
| max\_scheduled\_window | 30 days |

Requests exceeding limits return:

429 LIMIT\_EXCEEDED

---

# **5\. Message Model**

## **5.1 Hierarchy**

Request  
  └── Messages (per recipient)

Bulk requests are **expanded internally**.

### **Rule**

If **any recipient fails validation**, the **entire request is rejected**.

After provider submission:

* each recipient is independent  
* financial adjustments occur per message

---

# **6\. Message Lifecycle**

## **6.1 States**

| State | Meaning |
| ----- | ----- |
| ACCEPTED | Request validated, credits reserved |
| SUBMITTED | Sent to Nexah |
| COMPLETED | Nexah responded DELIVRD |
| FAILED | Nexah responded UNDELIV |
| FINALIZED | DLR confirmed delivery |
| FAIL\_FINALIZED | DLR confirmed failure |

### **Terminal States**

FINALIZED  
FAIL\_FINALIZED

No transitions allowed afterward.

---

# **7\. Message Submission Flow**

## **7.1 Submission**

POST /v1/sms/send

### **Request Schema**

{  
  "sendRequestId": "string",  
  "sender": "string",  
  "message": "string",  
  "recipients": \["string"\]  
}

### **Response**

{  
  "requestId": "uuid",  
  "acceptedMessages": 10,  
  "gatewayMessageIds": \[\]  
}

---

# **8\. Provider Communication**

## **8.1 Provider Timeout Rules**

All upstream provider interactions follow centralized timeout configuration.

| Parameter | Value |
| ----- | ----- |
| provider\_request\_timeout | 5 seconds |
| provider\_retry\_window | 24 hours |
| DLR\_wait\_window | 48 hours |

---

# **9\. Provider Retry Policy**

Failed provider submissions follow the retry strategy below.

| Parameter | Value |
| ----- | ----- |
| max\_retries | 5 |
| retry\_backoff | exponential |
| retry\_window | 24 hours |

Example schedule:

Attempt 1: immediate  
Attempt 2: \+5s  
Attempt 3: \+30s  
Attempt 4: \+2m  
Attempt 5: \+10m

After retries exhausted:

state → FAILED

---

# **10\. Delivery Reports (DLR)**

## **10.1 Endpoint**

POST /provider/nexah/dlr

## **10.2 Payload**

{  
  "messageid": "string",  
  "status": "DELIVRD|UNDELIV",  
  "total\_sms\_unit": 1  
}

## **10.3 Processing**

1. Match message using:

provider\_message\_id

2. Validate:

total\_sms\_unit \== send\_response\_units

If mismatch:

CRITICAL ALERT  
NO automatic finalization

---

# **11\. DLR Timeout Rule**

If message remains in:

COMPLETED  
FAILED

for **48 hours** without DLR:

1. Admin notification sent  
2. 24 hour manual review window

After **72 hours total**

COMPLETED → FAIL\_FINALIZED  
FAILED → FAIL\_FINALIZED

---

# **12\. Provider Outage Handling**

## **12.1 Detection**

If Nexah is unreachable for **\>3 hours**:

System enters:

PROVIDER\_DOWN

## **12.2 Behavior**

New send requests are rejected:

503 PROVIDER\_UNAVAILABLE

## **12.3 Backlog Strategy**

To prevent runaway queues:

| Parameter | Description |
| ----- | ----- |
| max\_queue\_backlog | configurable |
| backlog\_grace\_window | 3 hours |

If backlog exceeds limit:

new requests rejected

## **12.4 Recovery**

When provider health checks pass:

mode → NORMAL  
workers resume queue draining

---

# **13\. Financial Model**

## **13.1 Single Ledger Architecture**

Authoritative ledger:

credit\_ledger

Balance derived from:

Balance \= CreditsAdded \- CreditsDebited

Ledger entry types:

TOPUP\_APPROVED  
SMS\_RESERVATION  
SMS\_DEBIT  
ADMIN\_ADJUSTMENT  
RECONCILIATION\_ADJUSTMENT

---

## **13.2 Reservation Model**

At `ACCEPTED`:

credits reserved \= calculated\_segment\_count

Ledger entry:

SMS\_RESERVATION

After provider response:

Reservation converted to:

SMS\_DEBIT

---

## **13.3 Global Financial Invariant**

Client\_Available\_Balance ≥ 0

Enforced using:

SELECT ... FOR UPDATE

---

# **14\. Message Retention Policy**

## **14.1 Message Data**

| Storage Tier | Retention |
| ----- | ----- |
| Hot storage | 30 days |
| Archive | optional |
| Ledger | permanent |

Messages in terminal states may be pruned after retention window.

---

# **15\. Reconciliation**

The gateway validates provider billing using:

1. Per-message variance tracking  
2. Hourly balance snapshots  
3. Snapshot equation

previous\_balance  
\- sum(total\_sms\_unit)  
\= current\_balance

If mismatch occurs:

SNAPSHOT\_DIVERGENCE

Admin intervention required.

---

# **16\. Reconciliation Alert Thresholds**

To detect cost drift.

| Parameter | Meaning |
| ----- | ----- |
| variance\_threshold\_segments | segment difference |
| variance\_threshold\_percentage | cost deviation |
| alert\_severity\_levels | INFO / WARNING / CRITICAL |

Example:

WARNING: variance ≥ 5%  
CRITICAL: variance ≥ 10%

---

# **17\. Queue Processing**

Workers use PostgreSQL locking:

SELECT ... FOR UPDATE SKIP LOCKED

Guarantees:

* no duplicate processing  
* multi-node safe  
* crash-safe execution

---

# **18\. Internal Monitoring**

The gateway exposes operational metrics.

## **18.1 Core Metrics**

| Metric | Description |
| ----- | ----- |
| messages\_submitted | total accepted |
| messages\_failed | submission failures |
| provider\_latency | provider response time |
| dlr\_latency | delivery report latency |
| queue\_depth | pending messages |
| ledger\_balance | system financial state |

These metrics power:

* dashboards  
* alerting  
* SLA monitoring

---

# **19\. SLA Definitions**

| SLA | Target |
| ----- | ----- |
| API uptime | 99.9% |
| API latency | \<200ms p95 |
| Provider submission | 95% within 5s |
| DLR processing | 99% \<2s |
| Reconciliation | hourly |

---

# **20\. Security**

Webhook security layers:

1. IP allowlist  
2. Reverse DNS verification  
3. TLS certificate validation  
4. HMAC signature verification (if supported)  
5. Timestamp freshness (5 min window)  
6. Replay detection  
7. Rate limiting

---

# **21\. State Transition Table**

| Event | From | To |
| ----- | ----- | ----- |
| Accepted | — | ACCEPTED |
| Sent to provider | ACCEPTED | SUBMITTED |
| DELIVRD | SUBMITTED | COMPLETED |
| UNDELIV | SUBMITTED | FAILED |
| DLR DELIVRD | COMPLETED | FINALIZED |
| DLR UNDELIV | FAILED | FAIL\_FINALIZED |
| Timeout | COMPLETED/FAILED | FAIL\_FINALIZED |

---

# **22\. Idempotency**

Constraint:

(client\_id, sendRequestId) UNIQUE

Duplicate request:

returns original response

---

