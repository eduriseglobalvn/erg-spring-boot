package com.erg.ergspringboot.elearning;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyFramesResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyTierResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierTableRowResponse;
import com.erg.ergspringboot.elearning.internal.engagement.StudentTierReader;
import java.util.List;
import org.springframework.stereotype.Service;

/** Module entry point of the student tiers and frames; every call is about the authenticated student only. */
@Service
public class StudentTierOperations {

    private final StudentTierReader tiers;

    public StudentTierOperations(StudentTierReader tiers) {
        this.tiers = tiers;
    }

    public MyTierResponse myTier(String tenantId, String userId) {
        return tiers.myTier(tenantId, userId);
    }

    public List<TierTableRowResponse> tierTable(String tenantId, String userId, String weekStart) {
        return tiers.tierTable(tenantId, userId, weekStart);
    }

    public MyFramesResponse myFrames(String tenantId, String userId) {
        return tiers.myFrames(tenantId, userId);
    }

    public MyFramesResponse selectFrame(String tenantId, String userId, String tierKey) {
        return tiers.selectFrame(tenantId, userId, tierKey);
    }

    public boolean acknowledgeTierPromotion(String tenantId, String userId, String weekStart) {
        return tiers.acknowledgePromotion(tenantId, userId, weekStart);
    }
}
