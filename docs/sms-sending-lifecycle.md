# SMS Sending Lifecycle

This document describes the end-to-end process of sending an SMS in the Sendam gateway, from the initial client request to the final delivery status update and credit reconciliation.

## 1. High-Level Overview

The SMS sending process is divided into three main phases:
1.  **Ingestion & Validation**: The API receives the request, validates it, and reserves credits.
2.  **Asynchronous Dispatch**: A background worker polls for accepted requests and submits them to the SMS provider.
3.  **Delivery Tracking & Finalization**: The provider sends delivery reports (DLR) via webhooks, which update the message status and finalize billing.

---

## 2. Detailed Sequence Diagram

The following diagram illustrates the interaction between components during the SMS lifecycle.

```mermaid
sequenceDiagram
    participant Client
    participant SmsResource
    participant SmsService
    participant BillingService
    participant DB as Database
    participant Scheduler as SmsSchedulerService
    participant Provider as Nexah Provider
    participant Webhook as DrCallbackResource

    Note over Client, DB: Phase 1: Ingestion & Validation
    Client->>SmsResource: POST /v1/sms/send
    SmsResource->>SmsService: sendSms(clientId, request)
    SmsService->>SmsService: Validate (Sender, Message, Recipients, Rate Limits)
    SmsService->>BillingService: Reserve Credits (expected segments + 1 buffer)
    SmsService->>DB: Save SendRequest (Status: ACCEPTED)
    SmsService->>DB: Save SendRequestRecipient (Status: ACCEPTED)
    SmsService-->>Client: 200 OK (Accepted, Request ID)

    Note over DB, Provider: Phase 2: Asynchronous Dispatch
    Scheduler->>DB: Poll for ACCEPTED requests
    DB-->>Scheduler: List of SendRequests
    Scheduler->>DB: Update Status: SENDING
    Scheduler->>Provider: Submit SMS (Batch)
    Provider-->>Scheduler: Provider Message IDs
    Scheduler->>DB: Update Recipient Status: SUBMITTED + Provider IDs
    Scheduler->>DB: Update Parent Status: SUBMITTED

    Note over Provider, DB: Phase 3: Delivery Tracking & Finalization
    Provider->>Webhook: POST /v1/provider/dr (Webhook)
    Webhook->>DB: Update Recipient Status (COMPLETED/FAILED)
    Webhook->>DB: Update actual segments consumed
    Note right of Webhook: When all recipients are terminal
    Webhook->>DB: Update Parent Status (FINALIZED/FAIL_FINALIZED)
    Webhook->>BillingService: Finalize Credits (Settle difference)
```

plantuml Diagram

```plantuml

@startuml
title SMS Sending Workflow

participant Client
participant SmsResource
participant SmsService
participant BillingService
participant DB as Database
participant Scheduler as SmsSchedulerService
participant Provider as "Nexah Provider"
participant Webhook as DrCallbackResource

== Phase 1: Ingestion & Validation ==

Client->>SmsResource: POST /v1/sms/send
SmsResource->>SmsService: sendSms(clientId, request)
SmsService->>SmsService: Validate (Sender, Message, Recipients, Rate Limits)
SmsService->>BillingService: Reserve Credits (expected segments + 1 buffer)
SmsService->>DB: Save SendRequest (Status: ACCEPTED)
SmsService->>DB: Save SendRequestRecipient (Status: ACCEPTED)
SmsService-->>Client: 200 OK (Accepted, Request ID)

== Phase 2: Asynchronous Dispatch ==

Scheduler->>DB: Poll for ACCEPTED requests
DB-->>Scheduler: List of SendRequests
Scheduler->>DB: Update Status: SENDING
Scheduler->>Provider: Submit SMS (Batch)
Provider-->>Scheduler: Provider Message IDs
Scheduler->>DB: Update Recipient Status: SUBMITTED + Provider IDs
Scheduler->>DB: Update Parent Status: SUBMITTED

== Phase 3: Delivery Tracking & Finalization ==

Provider->>Webhook: POST /v1/provider/dr (Webhook)
Webhook->>DB: Update Recipient Status (COMPLETED/FAILED)
Webhook->>DB: Update actual segments consumed
note right of Webhook: When all recipients are terminal
Webhook->>DB: Update Parent Status (FINALIZED/FAIL_FINALIZED)
Webhook->>BillingService: Finalize Credits (Settle difference)

@enduml
```
---

## 3. Component Responsibilities

### Phase 1: Ingestion (`SmsResource` & `SmsService`)
-   **Authentication**: Handled by the API key security filter chain.
-   **Idempotency**: Checked via `sendRequestId` to prevent double-processing.
-   **Rate Limiting**: Enforced at both request level (10 req/s) and recipient level (1000/min).
-   **Validation**: 
    -   Sender ID format.
    -   Message content.
    -   Recipient phone number format (Cameroon mobile numbers).
    -   Future schedule time.
-   **Credit Reservation**: 
    -   Calculates segments using `SmsSegmentCalculator`.
    -   Reserves `(segments + 1) * recipients` to account for potential provider segment deviations.
-   **Persistence**: Saves the request as `ACCEPTED`.

### Phase 2: Dispatch (`SmsSchedulerService` & `SmsDispatchWorker`)
-   **Polling**: `SmsSchedulerService` runs every 30 seconds.
-   **Locking**: `SmsDispatchWorker` grabs batches of `ACCEPTED` requests and marks them `SENDING` to prevent duplicate dispatch in multi-node setups.
-   **Transmission**: Uses `NexahDispatchService` to send messages to the provider.
-   **Status Update**: Transitions recipients to `SUBMITTED` once the provider accepts the message and returns a `gatewayMessageId`.

### Phase 3: Delivery Reports (`DrCallbackResource` & `SmsProviderReportListener`)
-   **Webhook Reception**: `DrCallbackResource` receives DLRs from Nexah.
-   **Event-Driven Processing**: DLRs are converted into `ProviderDeliveryReportEvent`.
-   **Status Advancement**: `SmsProviderReportListener` updates individual recipients based on the DLR status (`DELIVRD` -> `COMPLETED`, otherwise `FAILED`).
-   **Finalization**: Once all recipients for a `SendRequest` reach a terminal state, the parent request is marked `FINALIZED` or `FAIL_FINALIZED`.
-   **Reconciliation**: Triggers an `SmsFinalisedEvent` which leads to credit settlement (releasing the buffer and charging the actual segments consumed).

---

## 4. Status Transition Map

### SendRequest (Parent) Statuses
-   **ACCEPTED**: Initial state after successful ingestion and credit reservation.
-   **SENDING**: Currently being processed by the dispatch worker.
-   **SUBMITTED**: Successfully handed over to the SMS provider.
-   **FINALIZED**: All recipients reached a terminal state (at least one success).
-   **FAIL_FINALIZED**: All recipients reached a terminal state, but all failed.
-   **CANCELLED**: Scheduled request was cancelled before dispatch.

### SendRequestRecipient Statuses
-   **ACCEPTED**: Initial state.
-   **SUBMITTED**: Provider has accepted the message and assigned an ID.
-   **COMPLETED**: Delivery confirmation received (DLR: `DELIVRD`).
-   **FAILED**: Permanent failure reported by provider or terminal retry state.

---

## 5. Recovery Mechanisms
-   **Stale Dispatch Recovery**: `SmsSchedulerService` recovers requests stuck in `SENDING` (e.g., node crash during dispatch) after 10 minutes by reverting them to `ACCEPTED`.
-   **Stale DLR Recovery**: Requests stuck in `SUBMITTED` for more than 24 hours are force-finalized to ensure credits are eventually settled.
