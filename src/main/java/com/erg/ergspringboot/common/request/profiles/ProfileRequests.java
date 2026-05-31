package com.erg.ergspringboot.common.request.profiles;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public final class ProfileRequests {

    private ProfileRequests() {
    }

    public record CreateProfileRequest(
            @JsonProperty("user_id") @NotBlank String userId,
            @JsonProperty("full_name") @Size(max = 255) String fullName,
            String bio,
            @Size(max = 64) String phone,
            @JsonProperty("date_of_birth") String dateOfBirth,
            String gender,
            String address,
            String city,
            String district,
            @JsonProperty("social_links") Map<String, String> socialLinks,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("teaching_philosophy") String teachingPhilosophy,
            Map<String, String> specialties,
            @JsonProperty("internal_note") String internalNote
    ) {
    }

    public record UpdateProfileRequest(
            @JsonProperty("full_name") @Size(max = 255) String fullName,
            String bio,
            @Size(max = 64) String phone,
            @JsonProperty("date_of_birth") String dateOfBirth,
            String gender,
            String address,
            String city,
            String district,
            @JsonProperty("social_links") Map<String, String> socialLinks,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("teaching_philosophy") String teachingPhilosophy,
            Map<String, String> specialties,
            @JsonProperty("internal_note") String internalNote
    ) {
    }
}
