package com.softropic.sendam.gateway.provider.nexah.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Acknowledgement entry in the DR callback response sent back to Nexah.
 * status == 1 means processed successfully; status == 0 means failed (Nexah will retry).
 *
 * NOTE: "reponsecode"/"reponsedescription" — intentional typo matching the Nexah spec.
 */
public record NexahDrAck(
        @JsonProperty("reponsecode") int responseCode,
        @JsonProperty("reponsedescription") String responseDescription,
        @JsonProperty("messageid") String messageId,
        @JsonProperty("mobileno") String mobileNo,
        @JsonProperty("status") int status,
        @JsonProperty("submittime") String submitTime,
        @JsonProperty("senttime") String sentTime,
        @JsonProperty("deliverytime") String deliveryTime
) {
}
