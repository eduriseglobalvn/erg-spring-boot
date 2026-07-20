package com.erg.ergspringboot.platform.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscordWebhookPayload(int type, String id, JsonNode data) { }
