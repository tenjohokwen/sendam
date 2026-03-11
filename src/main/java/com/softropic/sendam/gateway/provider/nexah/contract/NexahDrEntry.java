package com.softropic.sendam.gateway.provider.nexah.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Delivery report entry received from Nexah in the DR callback request.
 *
 * IMPORTANT FIELD NOTES:
 * - "reponsecode" (not "responsecode") — intentional typo matching the Nexah spec.
 * - totalSmsUnit is String, not Integer — Nexah sends it as a quoted string in DR payloads
 *   (e.g., "2" not 2). Using String avoids deserialization failure if Nexah sends null or "".
 */
public record NexahDrEntry(
        @JsonProperty("reponsecode") String responseCode,
        @JsonProperty("reponsedescription") String responseDescription,
        @JsonProperty("mobileno") String mobileNo,
        @JsonProperty("messageid") String messageId,
        @JsonProperty("total_sms_unit") String totalSmsUnit,
        @JsonProperty("submittime") String submitTime,
        @JsonProperty("senttime") String sentTime,
        @JsonProperty("deliverytime") String deliveryTime,
        @JsonProperty("status") String status,
        @JsonProperty("traffic") String traffic
) {
}
