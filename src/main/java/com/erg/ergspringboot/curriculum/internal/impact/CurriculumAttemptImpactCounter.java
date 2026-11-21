package com.erg.ergspringboot.curriculum.internal.impact;

import com.erg.ergspringboot.curriculum.CurriculumUsageCounter.Node;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Student attempts that reference a curriculum node. Each branch (quiz-side, assignment-side) is an index-driven
 * join on the attempt's tenant/quiz and tenant/assignment indexes and the branches are UNIONed, instead of one
 * OR across two outer joins that scans the whole attempts table. The numbers match the former JPQL counts.
 */
@Component
public class CurriculumAttemptImpactCounter {
    private static final String QUIZ_SIDE = """
            select a.id from lms_attempts a
            join lms_quizzes q on q.tenant_id = a.tenant_id and q.id = a.quiz_id
            where a.tenant_id = ? and %s""";
    private static final String ASSIGNMENT_SIDE = """
            select a.id from lms_attempts a
            join lms_assignments s on s.tenant_id = a.tenant_id and s.id = a.assignment_id
            where a.tenant_id = ? and s.subject_id = ?""";

    private final JdbcTemplate jdbc;

    public CurriculumAttemptImpactCounter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(String tenantId, Node node, String id) {
        Query query = query(tenantId, node, id);
        Long total = jdbc.queryForObject("select count(*) from (" + String.join(" union ", query.sides) + ") u",
                Long.class, query.args.toArray());
        return total == null ? 0 : total;
    }

    /** Hard delete only needs zero / non-zero, so stop at the first attempt. */
    public boolean exists(String tenantId, Node node, String id) {
        Query query = query(tenantId, node, id);
        String sql = "select " + String.join(" or ", query.sides.stream().map(side -> "exists(" + side + ")").toList());
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, query.args.toArray()));
    }

    private Query query(String tenantId, Node node, String id) {
        Query query = new Query();
        switch (node) {
            case SUBJECT -> {
                query.add(QUIZ_SIDE.formatted("q.subject_id = ?"), tenantId, id);
                query.add(ASSIGNMENT_SIDE, tenantId, id);
            }
            case LEVEL -> query.add(QUIZ_SIDE.formatted("q.level_id = ?"), tenantId, id);
            case TOPIC -> query.add(QUIZ_SIDE.formatted(
                    "(q.category_id = ? or jsonb_exists(coalesce(q.topic_ids, '[]'::jsonb), ?))"), tenantId, id, id);
        }
        return query;
    }

    private static final class Query {
        private final List<String> sides = new ArrayList<>();
        private final List<Object> args = new ArrayList<>();

        void add(String side, Object... sideArgs) {
            sides.add(side);
            args.addAll(List.of(sideArgs));
        }
    }
}
