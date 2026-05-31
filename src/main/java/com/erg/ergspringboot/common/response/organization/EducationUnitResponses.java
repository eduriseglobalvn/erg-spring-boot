package com.erg.ergspringboot.common.response.organization;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class EducationUnitResponses {

    private EducationUnitResponses() {
    }

    public record EducationUnitResponse(
            String id,
            String name,
            String slug,
            String type,
            @JsonProperty("parent_id") String parentId,
            @JsonProperty("logo_url") String logoUrl,
            String description,
            String status
    ) {
    }

    public record EducationUnitListResponse(List<EducationUnitResponse> educationUnits) {
    }
}
