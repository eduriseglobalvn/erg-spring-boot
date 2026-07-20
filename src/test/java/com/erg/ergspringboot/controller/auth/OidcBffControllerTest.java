package com.erg.ergspringboot.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OidcBffControllerTest {

    @Test
    void sanitizesPortalAndLocalReturnPath() {
        assertThat(OidcBffController.sanitizePortal("LCMS")).isEqualTo("lcms");
        assertThat(OidcBffController.sanitizePortal("google")).isEqualTo("lms");
        assertThat(OidcBffController.sanitizeReturnTo("/quiz/123?tab=edit")).isEqualTo("/quiz/123?tab=edit");
        assertThat(OidcBffController.sanitizeReturnTo("https://evil.example/steal")).isEqualTo("/");
        assertThat(OidcBffController.sanitizeReturnTo("//evil.example/steal")).isEqualTo("/");
    }
}
