package com.erg.ergspringboot.lms.internal.curriculum;

import com.erg.ergspringboot.curriculum.CurriculumUsageCounter;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import com.erg.ergspringboot.lms.persistence.AttemptRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Assignments and student attempts that reference a curriculum node. */
@Component
public class LmsCurriculumUsageCounter implements CurriculumUsageCounter {
    private final AssignmentRepository assignments;
    private final AttemptRepository attempts;

    public LmsCurriculumUsageCounter(AssignmentRepository assignments, AttemptRepository attempts) {
        this.assignments = assignments;
        this.attempts = attempts;
    }

    @Override
    public Usage count(String tenantId, Node node, String id, List<String> topicIds) {
        return switch (node) {
            case SUBJECT -> new Usage(0, 0, 0, 0, assignments.countImpactByTenantAndSubject(tenantId, id),
                    attempts.countImpactByTenantAndSubject(tenantId, id));
            case LEVEL -> new Usage(0, 0, 0, 0, assignments.countImpactByTenantAndLevel(tenantId, id),
                    attempts.countImpactByTenantAndLevel(tenantId, id));
            case TOPIC -> new Usage(0, 0, 0, 0, assignments.countImpactByTenantAndTopic(tenantId, id),
                    attempts.countImpactByTenantAndTopic(tenantId, id));
        };
    }
}
