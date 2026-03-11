# Requirements Specification: Gateway Admin UI

## 1. Overview
The Gateway Admin UI is a dedicated web interface for system administrators to monitor and manage the SMS Gateway module. It provides a real-time overview of the system state, client balances, API key lifecycle, and SMS traffic.

## 2. General Technical Requirements
### 2.1 Core Standards
- **Vue 3 Composition API**: Use `<script setup>` exclusively.
- **Plain JavaScript**: No TypeScript.
- **Framework**: Quasar Framework (Vue 3).
- **Responsive**: Mobile-first design using Quasar's grid system.
- **Color Palette**: Primary color `#1976d2`.
- **Internationalization**: Support for English (en-US) and French (fr-FR) with full key parity.
- **Functional Splitting**: Large components must be split into functional sub-components (max 250 lines).

### 2.2 Data Handling
- **Long Type Handling**: All `Long` values from the backend must be treated as `String` in the frontend to avoid precision loss (truncation) by JavaScript's number type.
- **Pagination**: All listing pages must implement server-side pagination.
- **Progress Indicators**: Every asynchronous operation must display a loading state (e.g., `QInnerLoading`, `:loading` on buttons).

### 2.3 Error Handling
- **Centralized API**: All calls must be centralized in an `api/` folder organized by domain.
- **Server Errors**: Handle the standard `ErrorDto` format:
  - `helpCode`: Unique identifier for the specific error occurrence (Sqid).
  - `errorMsg.key`: i18n key for the error.
  - `errorMsg.message`: Fallback English message.
- **Validation**: Use `lazy-rules` (validate on blur) for all form inputs.

## 3. Functional Requirements

### 3.1 Dashboard (Overview)
- **Aggregated Stats**: Display total active clients, total credits in system, and SMS success/failure rates.
- **System Health**: Visibility into provider (Nexah) connectivity status (Circuit Breaker state).

### 3.2 Client Management
- **Client Listing**: View all clients with their ID (String), label, and current available balance.
- **Client Creation**: Admin form to register new clients.
- **Search/Filter**: Filter clients by ID or name.

### 3.3 API Key Management
- **Client-Specific Keys**: Ability to view all API keys associated with a specific client ID.
- **Key Creation**: Admin can generate a new API key for any client.
- **Key Revocation**: Admin can revoke (deactivate) an existing key for any client.
- **Security**: Raw API keys must only be shown once upon creation.

### 3.4 Top-up Management
- **Pending Approvals**: A dedicated list of top-up requests in `PENDING_APPROVAL` status.
- **Approval Workflow**:
  - **Approve**: Action to credit the client's balance immediately.
  - **Reject**: Action to dismiss the request without balance change.
- **History**: View historical top-up requests with their final status (APPROVED/REJECTED).

### 3.5 SMS Traffic Monitoring
- **Scheduled SMS**: View a list of SMS requests that are `ACCEPTED` and scheduled for future delivery.
- **Message Status**: Drill down into specific `sendRequestId` to see recipient-level delivery reports (DLR).
- **Purge/Cleanup**: (Optional) View stats on purged old records.

### 3.6 Webhook & Callbacks
- **Client Endpoints**: View registered webhook URLs per client.
- **Delivery Logs**: Monitor the status of outgoing delivery reports to client webhooks (PENDING, DELIVERED, FAILED).

## 4. UX & Consistency
- **Feedback**: Use `$q.notify()` for all success and error notifications.
- **Accessibility**: Ensure form labels and contrast ratios meet WCAG AA standards.
- **Navigation**: Sidebar navigation for desktop, bottom tabs or burger menu for mobile.
- **Buttons**: Use `:disable` during loading states to prevent duplicate submissions.

## 5. Testing Requirements
- **Vitest + Vue Test Utils**: Mandatory for all components.
- **Isolation**: One test file per component.
- **Coverage**: Must simulate actual user flows, including edge cases like network failures.
