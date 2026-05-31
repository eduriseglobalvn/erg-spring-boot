package com.erg.ergspringboot.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FieldErrorDetail(
        String field,
        String tag,
        @JsonProperty("actual_tag")
        String actualTag,
        String message
) {
}
