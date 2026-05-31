package com.erg.ergspringboot.common.response.profiles;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public final class ProfileResponses {

    private ProfileResponses() {
    }

    public record SocialLinksResponse(Map<String, String> links) {
    }

    public record ProfileResponse(
            String id,
            @JsonProperty("user_id") String userId,
            @JsonProperty("full_name") String fullName,
            String bio,
            String phone,
            @JsonProperty("date_of_birth") String dateOfBirth,
            String gender,
            String address,
            String city,
            String district,
            SocialLinksResponse socialLinks,
            @JsonProperty("avatar_url") String avatarUrl,
            boolean isProfileCompleted,
            @JsonProperty("teaching_philosophy") String teachingPhilosophy,
            Map<String, String> specialties,
            Double rating,
            @JsonProperty("internal_note") String internalNote,
            String createdAt,
            String updatedAt
    ) {
    }
}
