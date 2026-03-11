# **SMS Gateway – Client API Specification (V8)**

Version: 8.0  
Status: Production API Contract  
Base URL: `/v1`

---

# **1\. Overview**

The SMS Gateway API allows clients to:

* Send SMS messages (single or bulk)  
* Query delivery status  
* Manage credits and top-ups  
* Retrieve billing ledger history  
* Register webhooks for delivery events  
* Manage API keys  
* Cancel scheduled messages

The API wraps an upstream SMS provider and provides:

* Strong financial correctness  
* Idempotent request handling  
* Deterministic billing  
* Multi-tenant isolation  
* Auditable credit ledger

All endpoints use **JSON** for request and response bodies.

---

# **2\. Authentication**

## **2.1 API Key Authentication**

All API requests must include a valid API key.

Header:

Authorization: Bearer \<API\_KEY\>

Properties of API keys:

* Unique per client  
* Stored hashed  
* Rotatable  
* Immediately revocable

The gateway derives the **client\_id** from the API key.

Clients MUST NOT send `client_id`.

---

## **2.2 Request Trace Header**

Every response includes a request trace header:

X-Request-ID: req\_trace\_01HQ2T...

Purpose:

* Enables support teams to trace requests in logs  
* Clients SHOULD include this value in support tickets

---

# **3\. HTTP Status Codes**

All endpoints return consistent HTTP status codes.

| Scenario | HTTP Status |
| ----- | ----- |
| Success | 200 OK |
| Accepted for processing | 202 Accepted |
| Invalid request | 400 Bad Request |
| Authentication failure | 401 Unauthorized |
| Invalid or revoked API key | 403 Forbidden |
| Duplicate sendRequestId | 409 Conflict |
| Rate limit exceeded | 429 Too Many Requests |
| Provider temporarily unavailable | 503 Service Unavailable |
| Internal system error | 500 Internal Server Error |

---

# **4\. Error Format**

All errors follow the same schema.

Example:

HTTP 409

{  
  "error\_code": "DUPLICATE\_SEND\_REQUEST\_ID",  
  "message": "sendRequestId already exists for this client",  
  "retryable": false  
}

### **Standard Error Codes**

| Code | Description |
| ----- | ----- |
| INSUFFICIENT\_CLIENT\_BALANCE | Not enough credits |
| INVALID\_PHONE\_NUMBER | Invalid recipient |
| DUPLICATE\_SEND\_REQUEST\_ID | Idempotency conflict |
| PROVIDER\_UNAVAILABLE | SMS provider unavailable |
| RATE\_LIMIT\_EXCEEDED | Rate limit triggered |
| INVALID\_SCHEDULE\_TIME | Schedule time invalid |
| INVALID\_SENDER\_ID | Sender ID violates rules |

---

# **5\. Credit & Balance API**

Credits represent **billable SMS segments**.

Balance is derived transactionally from the ledger.

---

## **5.1 Get Available Balance**

Endpoint

GET /v1/credits/balance

Response

{  
  "available\_balance": 1250,  
  "unit": "segments",  
  "currency": "SMS\_SEGMENT",  
  "last\_updated\_at": "2026-03-03T12:00:00Z"  
}

Notes:

Balance reflects:

* Approved top-ups  
* Active reservations  
* Finalized debits

SLA:

p95 \< 100ms

---

## **5.2 Credit Ledger History**

Clients can audit all credit movements.

Endpoint

GET /v1/credits/ledger

Response

{  
  "entries": \[  
    {  
      "timestamp": "2026-03-03T12:10:00Z",  
      "type": "SMS\_DEBIT",  
      "amount": \-3,  
      "balance\_after": 197,  
      "reference": "req-20260303-001"  
    }  
  \]  
}

Ledger Entry Types:

* TOPUP\_APPROVED  
* SMS\_RESERVATION  
* SMS\_DEBIT  
* SMS\_REFUND

Purpose:

* Prevent billing disputes  
* Provide full audit history

---

# **6\. Top-Up API**

Top-ups are externally paid and require administrative approval.

---

## **6.1 Create Top-Up Request**

Endpoint

POST /v1/credits/topups

Request

{  
  "amount": 1000,  
  "transaction\_id": "TXN-982739182",  
  "payment\_type": "BANK\_TRANSFER",  
  "account\_number": "1234567890"  
}

Validation Rules

* transaction\_id must be unique per client  
* amount \> 0  
* account\_number validated per payment type

Response

{  
  "topup\_id": "top\_12345",  
  "status": "PENDING\_APPROVAL",  
  "requested\_at": "2026-03-03T12:10:00Z"  
}

Behavior

* Ledger entry created: `TOPUP_PENDING`  
* Credits issued only after approval

---

## **6.2 Get Top-Up Status**

GET /v1/credits/topups/{topup\_id}

Response

{  
  "topup\_id": "top\_12345",  
  "amount": 1000,  
  "status": "APPROVED",  
  "approved\_at": "2026-03-03T13:00:00Z"  
}

After approval:

* Ledger entry: `TOPUP_APPROVED`  
* Balance updated immediately

---

# **7\. Send SMS API**

Supports:

* Single SMS  
* Bulk SMS  
* Scheduled SMS

Partial sends are **not allowed** during validation.

---

## **7.1 Send SMS**

Endpoint

POST /v1/sms/send

Request

{  
  "sendRequestId": "req-20260303-001",  
  "sender": "MYBRAND",  
  "message": "Your OTP is 1234",  
  "recipients": \[  
    "+237655123456",  
    "+2376581234568"  
  \],  
  "scheduleTime": "2026-03-04T10:00:00Z"  
}

---

## **7.2 Request Validation Rules**

* `sendRequestId` unique per client forever  
* Recipients must be valid **E.164 numbers (use com.softropic.sendam.common.validation.CamMobileValidator**  
* **The phone number must be a valid cameroon number**  
* Message required  
* scheduleTime optional  
* scheduleTime must be future UTC  
* If any recipient invalid → entire request rejected  
* Credits must be sufficient for **all recipients**

---

## **7.3 Successful Response**

{  
  "request\_id": "req\_abc123",  
  "sendRequestId": "req-20260303-001",  
  "message\_count": 2,  
  "calculated\_segment\_count": 2,  
  "reserved\_credits": 2,  
  "available\_balance\_after\_reservation": 1248,  
  "status": "ACCEPTED"  
}

Behavior

* Credits reserved immediately  
* Balance updated atomically  
* Messages enter state `ACCEPTED`

---

## **7.4 Idempotency**

`sendRequestId` guarantees idempotency.

If reused:

* Original response returned  
* No duplicate send  
* No additional credit reservation

Database constraint:

UNIQUE(client\_id, sendRequestId)

---

# **8\. Sender ID Rules**

Sender IDs must follow these constraints.

| Property | Value |
| ----- | ----- |
| Max length | 11 characters |
| Allowed characters | A-Z 0-9 |
| Recommended format | Uppercase |

Example:

MYBRAND  
SHOP123  
ACMEOTP

Country-specific rules may apply.

---

# **9\. SMS Segment Calculation**

Billing is based on **SMS segments**.

| Encoding | Single | Concatenated |
| ----- | ----- | ----- |
| GSM-7 | 160 | 153 |
| UCS-2 | 70 | 67 |

Example:

Message length: 200 characters (GSM-7)

segments \= ceil(200 / 153\) \= 2

The API field:

calculated\_segment\_count

represents **estimated segments reserved**.

Final billing uses **provider-confirmed segment usage**.

---

# **10\. Scheduled SMS**

Rules:

* `scheduleTime` must be future UTC  
* Credits reserved immediately  
* Messages remain in `ACCEPTED` until dispatch  
* At schedule time → submitted to provider

If provider unavailable:

* Retry according to gateway retry policy

---

## **10.1 Cancel Scheduled SMS**

Endpoint

DELETE /v1/sms/scheduled/{sendRequestId}

Response

{  
  "sendRequestId": "req-20260303-001",  
  "status": "CANCELLED"  
}

Behavior:

* Allowed only before provider submission  
* Reserved credits fully released

---

# **11\. Message Status API**

---

## **11.1 Get Request Status**

Endpoint

GET /v1/sms/status/{sendRequestId}

Supports pagination.

GET /v1/sms/status/{sendRequestId}?page=1\&page\_size=100

Parameters

| Parameter | Description |
| ----- | ----- |
| page | Page number |
| page\_size | Items per page (max 1000\) |

---

## **11.2 Response**

{  
  "sendRequestId": "req-20260303-001",  
  "overall\_status": "FINALIZED",  
  "page": 1,  
  "page\_size": 100,  
  "total\_messages": 2,  
  "messages": \[  
    {  
      "recipient": "+491701234567",  
      "state": "FINALIZED",  
      "gateway\_message\_id": "msg\_gw\_01HQ2R2Z7F5",  
      "provider\_message\_id": "abc123",  
      "segments\_consumed": 1  
    }  
  \]  
}

---

## **11.3 Message States**

| State | Meaning |
| ----- | ----- |
| ACCEPTED | Request validated |
| SUBMITTED | Sent to provider |
| COMPLETED | Provider delivery success |
| FAILED | Provider delivery failure |
| FINALIZED | Delivery confirmed |
| FAIL\_FINALIZED | Delivery failed |

Terminal states:

* FINALIZED  
* FAIL\_FINALIZED

---

# **12\. Client Webhooks**

Clients may optionally receive delivery events via webhooks.

Polling remains supported.

---

## **12.1 Register Webhook**

POST /v1/webhooks

Request

{  
  "url": "https://client.example.com/sms-events",  
  "events": \["sms.finalized"\]  
}

Response

{  
  "webhook\_id": "wh\_12345",  
  "status": "ACTIVE",  
  "created\_at": "2026-03-03T12:00:00Z"  
}

---

## **12.2 Delivery Event Example**

{  
  "event": "sms.finalized",  
  "sendRequestId": "req-20260303-001",  
  "recipient": "+491701234567",  
  "gateway\_message\_id": "msg\_gw\_01HQ2R2Z7F5",  
  "status": "DELIVERED"  
}

---

# **13\. API Key Management**

---

## **13.1 Create API Key**

POST /v1/api-keys

Response

{  
  "api\_key\_id": "key\_12345",  
  "api\_key": "sk\_live\_xxxxx",  
  "created\_at": "2026-03-03T12:00:00Z"  
}

Key value is shown **only once**.

---

## **13.2 List API Keys**

GET /v1/api-keys

Response

{  
  "keys": \[  
    {  
      "api\_key\_id": "key\_12345",  
      "created\_at": "2026-03-03T12:00:00Z",  
      "status": "ACTIVE"  
    }  
  \]  
}

---

## **13.3 Revoke API Key**

DELETE /v1/api-keys/{id}

Revoked keys immediately lose access.

---

# **14\. Rate Limiting**

Default per client:

* 10 requests/sec  
* 1000 recipients/minute

Bulk requests consume rate **per recipient**.

Exceeded limits return:

HTTP 429

---

# **15\. Provider Downtime Handling**

If provider unavailable:

* `/sms/send` requests rejected  
* Balance unchanged  
* Existing retries continue

Error

{  
  "error\_code": "PROVIDER\_UNAVAILABLE",  
  "retryable": true  
}

---

# **16\. Data Retention**

| Data | Retention |
| ----- | ----- |
| Message status | 30 days |
| Metadata | Configurable |
| Ledger entries | Indefinite |

After deletion window:

GET /sms/status → 404

---

# **17\. Client Isolation Guarantees**

* Clients cannot access other client data  
* All queries scoped to authenticated client  
* sendRequestId unique per client  
* Hard database isolation

---

# **18\. Financial Guarantees**

1. Clients cannot overspend balance.  
2. Billing equals provider-reported segments.  
3. Balance never becomes negative.  
4. All transactions are auditable.  
5. Ledger is the single source of truth.

---

# **19\. Example End-to-End Flow**

1. Client checks balance → 100  
2. Client sends SMS to 2 recipients  
3. Gateway reserves 2 credits  
4. Balance becomes 98  
5. Message submitted to provider  
6. Delivery reports received  
7. Message finalized  
8. Ledger debit confirmed

System guarantees:

* Atomic reservations  
* Idempotent requests  
* Crash-safe ledger accounting

