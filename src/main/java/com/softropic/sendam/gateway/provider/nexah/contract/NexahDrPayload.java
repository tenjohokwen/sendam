package com.softropic.sendam.gateway.provider.nexah.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound delivery report payload POSTed by Nexah to the partner's DR callback endpoint.
 * Contains a list of delivery report entries in the "dlrlist" array.
 */
public record NexahDrPayload(
        @JsonProperty("dlrlist") List<NexahDrEntry> dlrList
) {
}
