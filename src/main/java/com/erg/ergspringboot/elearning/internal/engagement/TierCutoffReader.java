package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierCutoffCell;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierCutoffsResponse;
import java.time.LocalDate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Cutoff cells of a school/grade cohort. A separate bean so the cache proxy applies (the readers call it); the same
 * cohort is read by every student of that grade, and only the weekly recompute changes it (it evicts the school).
 */
@Service
public class TierCutoffReader {

    private final NamedParameterJdbcTemplate jdbc;

    public TierCutoffReader(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    /** Cells for {@code week}, or the cohort's latest stored week when null. Key index 1 (school) is the eviction scope. */
    @Cacheable(cacheNames = "tierCutoffs", sync = true,
            key = "{#tenantId, #schoolId, #grade, #week == null ? 'latest' : #week.toString()}")
    public TierCutoffsResponse cutoffs(String tenantId, String schoolId, String grade, LocalDate week) {
        return new TierCutoffsResponse(jdbc.query("""
                select tier_key, division, rank_from, rank_to, min_xp from student_tier_cutoff
                where tenant_id = :t and school_id = :school and grade = :grade
                  and week_start = coalesce(cast(:week as date), (select max(week_start) from student_tier_cutoff
                                                    where tenant_id = :t and school_id = :school and grade = :grade))
                order by rank_from
                """, new MapSqlParameterSource("t", tenantId).addValue("school", schoolId).addValue("grade", grade).addValue("week", week),
                (rs, n) -> new TierCutoffCell(rs.getString("tier_key"), rs.getInt("division") == 0 ? null : rs.getInt("division"),
                        rs.getInt("rank_from"), rs.getInt("rank_to"), rs.getInt("min_xp"))));
    }
}
