package com.softropic.sendam.client.contract.nexah;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from the Nexah sendsms endpoint.
 * responseCode == 1 indicates success; 0 indicates error.
 */
public record NexahSendResponse(
        @JsonProperty("responsecode") int responseCode,
        @JsonProperty("responsedescription") String responseDescription,
        @JsonProperty("responsemessage") String responseMessage,
        @JsonProperty("sms") List<NexahSmsEntry> sms
) {
}
