package com.softropic.sendam.gateway.provider.nexah.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Individual SMS entry in the Nexah send response.
 * totalSmsUnit is Integer here (send response returns numeric, per nexahApi.md 2.1.2).
 * NOTE: In DR callbacks, total_sms_unit arrives as String — handled separately in NexahDrEntry.
 */
public record NexahSmsEntry(
        @JsonProperty("status") String status,
        @JsonProperty("smsclientid") String smsClientId,
        @JsonProperty("messageid") String messageId,
        @JsonProperty("mobileno") String mobileNo,
        @JsonProperty("errorcode") Integer errorCode,
        @JsonProperty("errordescription") String errorDescription,
        @JsonProperty("total_sms_unit") Integer totalSmsUnit,
        @JsonProperty("balance") Integer balance
) {
}
