package com.erg.ergspringboot.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.request.auth.AuthRequests.ResetPasswordRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.UpdateProfileRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ApiDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void authRequestKeepsGoJsonFieldNames() throws Exception {
        String json = objectMapper.writeValueAsString(
                new ResetPasswordRequest("user@example.com", "123456", "new-password")
        );

        assertThat(json).contains("\"new_password\":\"new-password\"");
    }

    @Test
    void userProfileRequestKeepsSnakeCaseFieldNames() throws Exception {
        String json = objectMapper.writeValueAsString(
                new UpdateProfileRequest(
                        "Jane Doe",
                        null,
                        null,
                        null,
                        "2000-01-01",
                        null,
                        null,
                        null,
                        "Teacher",
                        null,
                        Map.of("facebook", "https://example.test/jane"),
                        "https://example.test/avatar.png"
                )
        );

        assertThat(json).contains("\"full_name\":\"Jane Doe\"");
        assertThat(json).contains("\"date_of_birth\":\"2000-01-01\"");
        assertThat(json).contains("\"social_links\"");
        assertThat(json).contains("\"avatar_url\":\"https://example.test/avatar.png\"");
    }

    @Test
    void lmsStartAttemptRequestCarriesRequiredValidation() {
        StartAttemptRequest request = new StartAttemptRequest("", "", "", "");

        assertThat(validator.validate(request)).hasSize(4);
    }
}
