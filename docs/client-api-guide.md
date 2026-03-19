# Sendam API Client Documentation

Welcome to the Sendam SMS Gateway API. This guide provides the necessary information to integrate with our platform, manage your credits, send SMS, and receive delivery reports.

## 1. Authentication

All API requests must be authenticated using an **API Key**. For now, API keys are created and managed by system administrators. Please contact support to obtain your key.

Include your API key in the `X-API-KEY` header of every request:

```http
X-API-KEY: your_api_key_here
```

**Base URL:** `https://api.sendam.com/v1` (Replace with the actual production URL)

---

## 2. Credits & Billing

### Get Current Balance
Returns your current available and reserved credit balance.
- **Endpoint:** `GET /credits/balance`
- **Response Structure:**
  ```json
  {
    "available_balance": 5000,
    "unit": "CREDIT",
    "currency": "XAF",
    "last_updated_at": "2023-10-27T10:00:00Z"
  }
  ```

### Request a Top-up
Submit a request to add credits to your account.
- **Endpoint:** `POST /credits/topups`
- **Request Body:**
  ```json
  {
    "amount": 1000,
    "transactionReference": "BANK_TRANSFER_REF_123"
  }
  ```
- **Response Structure (`200 OK`):**
  ```json
  {
    "topup_id": "top_ZG8K7a",
    "status": "PENDING_APPROVAL",
    "requested_at": "2023-10-27T10:00:00Z"
  }
  ```

### Check Top-up Status
- **Endpoint:** `GET /credits/topups/{topup_id}`
- **Response Structure:**
  ```json
  {
    "topup_id": "top_ZG8K7a",
    "amount": 1000,
    "status": "APPROVED",
    "approved_at": "2023-10-27T11:00:00Z"
  }
  ```
- **Top-up Statuses:** `PENDING_APPROVAL`, `APPROVED`, `REJECTED`.

### Ledger History
- **Endpoint:** `GET /credits/ledger?page=0&size=50`
- **Response Structure:**
  ```json
  {
    "entries": [
      {
        "timestamp": "2023-10-27T10:00:00Z",
        "type": "TOPUP",
        "amount": 1000,
        "balance_after": 5000,
        "reference": "top_ZG8K7a"
      }
    ],
    "page": 0,
    "size": 50,
    "totalElements": 125
  }
  ```

### Credit Consumption
Query total credits consumed within a time range.
- **Endpoint:** `GET /credits/consumption?from=...&to=...`
- **Response Structure:**
  ```json
  {
    "netCreditsConsumed": 450,
    "from": "2023-01-01T00:00:00Z",
    "to": "2023-01-31T23:59:59Z"
  }
  ```

---

## 3. SMS Operations

### Send SMS
Submit an SMS for immediate or scheduled delivery.
- **Endpoint:** `POST /sms/send`
- **Request Body:**
  ```json
  {
    "sendRequestId": "client_ref_001",
    "message": "Hello from Sendam!",
    "recipients": ["2376XXXXXXXX"],
    "scheduleTime": "2023-10-27T10:00:00Z"
  }
  ```
- **Response Structure (`200 OK`):**
  ```json
  {
    "request_id": "req_12345",
    "sendRequestId": "client_ref_001",
    "message_count": 1,
    "calculated_segment_count": 1,
    "reserved_credits": 2,
    "available_balance_after_reservation": 4998,
    "status": "ACCEPTED"
  }
  ```
- **Notes:** `reserved_credits` includes a +1 segment buffer per recipient for reconciliation.

### Check Send Status
- **Endpoint:** `GET /sms/status/{sendRequestId}?page=0&pageSize=20`
- **Response Structure:**
  ```json
  {
    "sendRequestId": "client_ref_001",
    "overall_status": "SUBMITTED",
    "page": 0,
    "page_size": 20,
    "total_messages": 1,
    "messages": [
      {
        "recipient": "2376XXXXXXXX",
        "status": "COMPLETED",
        "gatewayMessageId": "gw_abc123",
        "providerMessageId": "p_987",
        "segmentsConsumed": 1
      }
    ]
  }
  ```

### Cancel Scheduled SMS
- **Endpoint:** `DELETE /sms/scheduled/{sendRequestId}`
- **Response Structure:**
  ```json
  {
    "sendRequestId": "client_ref_001",
    "status": "CANCELLED"
  }
  ```

---

## 4. Webhooks

### Register Webhook
- **Endpoint:** `POST /v1/webhooks`
- **Request Body:**
  ```json
  {
    "url": "https://your-server.com/callback"
  }
  ```
- **Response Structure:**
  ```json
  {
    "webhook_id": "wh_ZG8K7a",
    "status": "ACTIVE",
    "created_at": "2023-10-27T10:00:00Z"
  }
  ```

---

## 5. Analytics

### Delivery Statistics
- **Endpoint:** `GET /sms/analytics/delivery-stats?from=...&to=...`
- **Response Structure:**
  ```json
  {
    "total_sent": 1000,
    "delivered": 950,
    "failed": 50,
    "delivery_rate": 95.0,
    "total_segments": 1050
  }
  ```

### Segment Totals
- **Endpoint:** `GET /sms/analytics/segment-totals?from=...&to=...`
- **Response Structure:**
  ```json
  {
    "total_segments": 1050,
    "from": "...",
    "to": "..."
  }
  ```

---

## 6. Error Handling

### Common Error Structure
All error responses follow this format:
```json
{
  "helpCode": "ZG8K7a",
  "errorMsg": {
    "errorKey": "INSUFFICIENT_CLIENT_BALANCE",
    "message": "Insufficient credit balance to complete the operation."
  },
  "fieldErrors": []
}
```
*   `helpCode`: A unique identifier for the error. Provide this to support for faster troubleshooting.
*   `errorKey`: A programmatic error code.
*   `message`: A human-readable description of the error.

### Field Validation Errors
For `400 Bad Request` validation errors, the `fieldErrors` list will be populated:
```json
{
  "helpCode": "...",
  "errorMsg": { "errorKey": "validation.invalidData", "message": "Invalid Data" },
  "fieldErrors": [
    {
      "objectName": "sendSmsRequest",
      "field": "recipients",
      "errorMsg": { "errorKey": "INVALID_PHONE_NUMBER", "message": "Invalid recipient phone numbers" }
    }
  ]
}
```

### Error Codes by Endpoint

| HTTP Status | Error Key | Description |
| :--- | :--- | :--- |
| **Global** | `security.unauthorized` | Missing or invalid API Key. |
| | `TOO_MANY_REQUESTS` | Rate limit exceeded (10 req/s or 1000 recipients/min). |
| | `ACCOUNT_FROZEN` | Your account is suspended. Contact support. |
| | `PROVIDER_UNAVAILABLE` | Temporary issue with the SMS provider. Retry later. |
| **SMS Send** | `INSUFFICIENT_CLIENT_BALANCE` | Not enough credits for the request. |
| | `INVALID_PHONE_NUMBER` | One or more recipients have invalid formats. |
| | `INVALID_SCHEDULE_TIME` | `scheduleTime` must be in the future. |
| **SMS Cancel**| `CANCEL_NOT_ALLOWED` | SMS is already being sent or is not scheduled. |
| **Top-ups** | `DUPLICATE_TRANSACTION_ID` | This transaction reference has already been used. |
| **Common** | `generic.unknown` | An internal server error occurred. |
| | `validation.badRequest` | Invalid JSON or missing required parameters. |
