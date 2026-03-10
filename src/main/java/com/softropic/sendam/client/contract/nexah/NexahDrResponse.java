package com.softropic.sendam.client.contract.nexah;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response body returned by the partner's DR callback endpoint to Nexah.
 * Contains acknowledgement entries for each DR received.
 * Nexah will retry delivery reports for entries with status == 0.
 */
public record NexahDrResponse(
        @JsonProperty("dlrlist") List<NexahDrAck> dlrList
) {
}
