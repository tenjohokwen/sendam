package com.softropic.sendam.client.contract.nexah;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for the Nexah sendsms endpoint.
 * Field names mapped to lowercase JSON as per nexahApi.md section 2.1.1.
 */
public record NexahSendRequest(
        @JsonProperty("user") String user,
        @JsonProperty("password") String password,
        @JsonProperty("senderid") String senderid,
        @JsonProperty("sms") String sms,
        @JsonProperty("mobiles") String mobiles
) {
}
