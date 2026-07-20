package com.erg.ergspringboot.platform.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramWebhookPayload(@JsonProperty("update_id") long updateId, JsonNode message,
                                     @JsonProperty("callback_query") JsonNode callbackQuery) { }
