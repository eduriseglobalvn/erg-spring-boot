package com.erg.ergspringboot.elearning;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyFramesResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyTierResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierTableRowResponse;
import com.erg.ergspringboot.elearning.internal.engagement.StudentTierJob;
import com.erg.ergspringboot.elearning.internal.engagement.StudentTierReader;
import java.util.List;
import org.springframework.stereotype.Service;

/** Module entry point of the student tiers and frames; every call is about the authenticated student only. */
@Service
public class StudentTierOperations {

    private final StudentTierReader tiers;
    private final StudentTierJob job;

    public StudentTierOperations(StudentTierReader tiers, StudentTierJob job) {
        this.tiers = tiers;
        this.job = job;
    }

    /** Admin trigger of the weekly tier update for one tenant; returns how many students were tiered. */
    public int runTierUpdate(String tenantId) {
        return job.runForTenant(tenantId);
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
