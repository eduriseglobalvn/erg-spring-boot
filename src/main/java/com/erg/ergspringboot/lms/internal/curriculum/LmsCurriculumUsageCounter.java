package com.erg.ergspringboot.lms.internal.curriculum;

import com.erg.ergspringboot.curriculum.CurriculumUsageCounter;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Assignments that reference a curriculum node; attempts are counted by CurriculumAttemptImpactCounter. */
@Component
public class LmsCurriculumUsageCounter implements CurriculumUsageCounter {
    private final AssignmentRepository assignments;

    public LmsCurriculumUsageCounter(AssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    public Usage count(String tenantId, Node node, String id, List<String> topicIds) {
        return switch (node) {
            case SUBJECT -> new Usage(0, 0, 0, 0, assignments.countImpactByTenantAndSubject(tenantId, id), 0);
            case LEVEL -> new Usage(0, 0, 0, 0, assignments.countImpactByTenantAndLevel(tenantId, id), 0);
            case TOPIC -> new Usage(0, 0, 0, 0, assignments.countImpactByTenantAndTopic(tenantId, id), 0);
        };
    }
}
