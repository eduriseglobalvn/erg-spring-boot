package com.erg.ergspringboot.notifications;

import java.util.List;

/** Who a campaign targets; stored as jsonb. Roles are TEACHER|STUDENT|STAFF. */
public record CampaignAudience(
        boolean wholeTenant,
        List<String> schoolIds,
        List<String> gradeIds,
        List<String> classIds,
        List<String> groupIds,
        List<String> roles,
        List<String> userIds) {}
