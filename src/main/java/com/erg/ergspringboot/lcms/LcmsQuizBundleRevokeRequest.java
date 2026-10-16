package com.erg.ergspringboot.lcms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Server-side revocation reason; tenant and actor always come from the session. */
public record LcmsQuizBundleRevokeRequest(
        @NotBlank @Size(max = 256) String reason
) {
}
