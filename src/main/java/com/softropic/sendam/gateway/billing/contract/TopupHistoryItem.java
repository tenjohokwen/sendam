package com.softropic.sendam.gateway.billing.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record TopupHistoryItem(
    @JsonProperty("id")               long    id,
    @JsonProperty("client_id")        long    clientId,
    @JsonProperty("amount")           long    amount,
    @JsonProperty("transaction_id")   String  transactionId,
    @JsonProperty("payment_type")     String  paymentType,
    @JsonProperty("account_number")   String  accountNumber,
    @JsonProperty("topup_status")     String  topupStatus,
    @JsonProperty("created_date")     Instant createdDate,
    @JsonProperty("approved_at")      Instant approvedAt,   // null when not approved
    @JsonProperty("rejected_at")      Instant rejectedAt    // null when not rejected
) {}
