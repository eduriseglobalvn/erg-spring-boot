package com.erg.ergspringboot.notifications;

import java.util.List;
import java.util.Objects;

/** Who a campaign targets; stored as jsonb. Roles are TEACHER|STUDENT|STAFF. Missing lists mean "none". */
public record CampaignAudience(
        boolean wholeTenant,
        List<String> schoolIds,
        List<String> gradeIds,
        List<String> classIds,
        List<String> groupIds,
        List<String> roles,
        List<String> userIds) {

    public CampaignAudience {
        schoolIds = clean(schoolIds);
        gradeIds = clean(gradeIds);
        classIds = clean(classIds);
        groupIds = clean(groupIds);
        roles = clean(roles);
        userIds = clean(userIds);
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of()
                : values.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).toList();
    }
}
