package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.FrameResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyFramesResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyTierResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierCellResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierPromotionResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierStepResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierTableRowResponse;
import com.erg.ergspringboot.elearning.internal.engagement.ClassLeaderboardService.Tier;
import com.erg.ergspringboot.elearning.internal.engagement.TierMath.Candidate;
import com.erg.ergspringboot.elearning.internal.engagement.TierMath.Cell;
import com.erg.ergspringboot.elearning.internal.engagement.TierMath.Placement;
import com.erg.ergspringboot.elearning.internal.engagement.TierMath.Standing;
import com.erg.ergspringboot.elearning.internal.engagement.TierMath.State;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student tiers and frames (spec 5.3-5.8). The weekly job ({@link #refreshCohort}) stores one row per student and week;
 * the student-facing reads only ever touch the authenticated student's own rows plus the cutoff table of their grade.
 */
@Service
public class StudentTierService {

    private static final String ACTIVE = "(s.status is null or lower(s.status) in ('active', 'enrolled'))";

    private final NamedParameterJdbcTemplate jdbc;

    public StudentTierService(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    /** Students of one school and grade compete together (the school year is the week's). */
    public record Cohort(String tenantId, String schoolId, String grade) { }

    // ---- weekly job ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Cohort> cohorts() {
        return jdbc.getJdbcTemplate().query("""
                select distinct s.tenant_id, c.school_id, c.grade from lms_students s
                join academic_classes c on c.tenant_id = s.tenant_id and c.id = s.academic_class_id
                where %s and c.school_id is not null and c.grade is not null and c.grade <> ''
                """.formatted(ACTIVE), (rs, row) -> new Cohort(rs.getString(1), rs.getString(2), rs.getString(3)));
    }

    /**
     * Standings as of {@code weekStart} 00:00 (hand-ins before it). Idempotent: the cohort's rows of that week are
     * replaced; the previous week's stored state (week_start < weekStart) feeds the drop protection, so a re-run never
     * counts the same week twice towards the below-cutoff demotion counter. Returns the number of students tiered.
     */
    @Transactional
    public int refreshCohort(Cohort cohort, LocalDate weekStart) {
        LocalDate lastDay = weekStart.minusDays(1);
        String year = schoolYear(lastDay);
        List<String> ids = jdbc.queryForList("""
                select s.id from lms_students s
                join academic_classes c on c.tenant_id = s.tenant_id and c.id = s.academic_class_id
                where s.tenant_id = :t and c.school_id = :school and c.grade = :grade and %s
                """.formatted(ACTIVE), Map.of("t", cohort.tenantId(), "school", cohort.schoolId(), "grade", cohort.grade()), String.class);
        if (ids.isEmpty()) return 0;
        Map<String, Candidate> xp = readXp(cohort.tenantId(), ids, yearStart(lastDay), weekStart.atStartOfDay(LeaderboardXp.ZONE), lastDay);
        Standing standing = TierMath.standings(new ArrayList<>(xp.values()));
        Map<String, Placement> placed = new HashMap<>();
        standing.placements().forEach(placement -> placed.put(placement.studentId(), placement));
        Map<String, State> previous = previousStates(cohort.tenantId(), ids, year, weekStart);

        Map<String, Object> key = Map.of("t", cohort.tenantId(), "school", cohort.schoolId(), "grade", cohort.grade(), "week", weekStart);
        jdbc.update("delete from student_tier_week where tenant_id = :t and school_id = :school and grade = :grade and week_start = :week", key);
        jdbc.update("delete from student_tier_cutoff where tenant_id = :t and school_id = :school and grade = :grade and week_start = :week", key);

        List<SqlParameterSource> weeks = new ArrayList<>();
        List<SqlParameterSource> frames = new ArrayList<>();
        for (String id : ids) {
            Candidate c = xp.get(id);
            Placement placement = placed.get(id);
            State state = TierMath.next(previous.get(id), placement);
            weeks.add(new MapSqlParameterSource(key).addValue("student", id).addValue("year", year)
                    .addValue("xp", c.xp()).addValue("submitted", c.submitted())
                    .addValue("rank", placement == null ? null : placement.rank()).addValue("size", standing.size())
                    .addValue("tier", state.tierKey()).addValue("division", state.division())
                    .addValue("below", state.belowCount()).addValue("protect", state.protect()));
            if (state.tierKey() != null) {
                frames.add(new MapSqlParameterSource(key).addValue("student", id).addValue("year", year).addValue("tier", state.tierKey()));
            }
        }
        jdbc.batchUpdate("""
                insert into student_tier_week (tenant_id, student_id, school_id, grade, school_year, week_start, course_xp,
                    submitted_count, grade_rank, grade_size, tier_key, division, below_count, protected)
                values (:t, :student, :school, :grade, :year, :week, :xp, :submitted, :rank, :size, :tier, :division, :below, :protect)
                """, weeks.toArray(SqlParameterSource[]::new));
        List<SqlParameterSource> cutoffs = standing.cells().stream().map(cell -> (SqlParameterSource) new MapSqlParameterSource(key)
                .addValue("year", year).addValue("tier", cell.tierKey()).addValue("division", cell.division() == null ? 0 : cell.division())
                .addValue("from", cell.rankFrom()).addValue("to", cell.rankTo()).addValue("min", cell.minXp())).toList();
        jdbc.batchUpdate("""
                insert into student_tier_cutoff (tenant_id, school_id, grade, school_year, week_start, tier_key, division,
                    rank_from, rank_to, min_xp) values (:t, :school, :grade, :year, :week, :tier, :division, :from, :to, :min)
                """, cutoffs.toArray(SqlParameterSource[]::new));
        jdbc.batchUpdate("""
                insert into student_frame (tenant_id, student_id, tier_key, unlocked_at, school_year)
                values (:t, :student, :tier, :week, :year) on conflict (tenant_id, student_id, tier_key) do nothing
                """, frames.toArray(SqlParameterSource[]::new));
        return ids.size();
    }

    private Map<String, State> previousStates(String tenantId, List<String> ids, String year, LocalDate weekStart) {
        Map<String, State> states = new HashMap<>();
        jdbc.query("""
                select distinct on (student_id) student_id, tier_key, division, below_count, protected from student_tier_week
                where tenant_id = :t and student_id in (:ids) and school_year = :year and week_start < :week
                order by student_id, week_start desc
                """, Map.of("t", tenantId, "ids", ids, "year", year, "week", weekStart), rs -> {
            states.put(rs.getString("student_id"), new State(rs.getString("tier_key"), (Integer) rs.getObject("division"),
                    rs.getInt("below_count"), rs.getBoolean("protected")));
        });
        return states;
    }

    /** Course XP (weekly streak bonus), hand-in count and the moment of the last hand-in, for every id (zeros when none). */
    Map<String, Candidate> readXp(String tenantId, Collection<String> ids, LocalDate yearStart, ZonedDateTime until, LocalDate asOf) {
        Map<String, Map<LocalDate, Integer>> weekly = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Instant> last = new HashMap<>();
        Map<String, Set<LocalDate>> days = new HashMap<>();
        List<String> states = LmsStatePolicy.completedAttemptStates().stream().toList();
        jdbc.query(ClassLeaderboardService.HAND_INS + " and submitted_at < :until", Map.of("tenantId", tenantId, "studentIds", ids,
                "states", states, "since", yearStart.atStartOfDay(LeaderboardXp.ZONE).toOffsetDateTime(), "until", until.toOffsetDateTime()), rs -> {
            String id = rs.getString("student_id");
            OffsetDateTime at = rs.getObject("submitted_at", OffsetDateTime.class);
            LocalDate week = at.atZoneSameInstant(LeaderboardXp.ZONE).toLocalDate().with(DayOfWeek.MONDAY);
            weekly.computeIfAbsent(id, k -> new HashMap<>()).merge(week,
                    LeaderboardXp.handInXp((Integer) rs.getObject("total_questions"), rs.getBoolean("before_deadline")), Integer::sum);
            counts.merge(id, 1, Integer::sum);
            last.merge(id, at.toInstant(), (a, b) -> a.isAfter(b) ? a : b);
        });
        jdbc.query(ClassLeaderboardService.ACTIVE_DAYS, Map.of("tenantId", tenantId, "studentIds", ids, "states", states,
                "since", yearStart.minusDays(7).atStartOfDay(LeaderboardXp.ZONE).toOffsetDateTime()), rs -> {
            days.computeIfAbsent(rs.getString("student_id"), k -> new HashSet<>()).add(rs.getObject("day", LocalDate.class));
        });
        Map<String, Candidate> result = new HashMap<>();
        for (String id : ids) {
            int xp = LeaderboardXp.courseXp(weekly.getOrDefault(id, Map.of()), days.getOrDefault(id, Set.of()), asOf);
            result.put(id, new Candidate(id, xp, counts.getOrDefault(id, 0), last.get(id)));
        }
        return result;
    }

    /** Tier and frame to show beside each student on the board. */
    Map<String, Tier> tiersFor(String tenantId, Collection<String> studentIds) {
        Map<String, Tier> result = new HashMap<>();
        if (studentIds.isEmpty()) return result;
        Map<String, String> current = new HashMap<>();
        Map<String, Integer> divisions = new HashMap<>();
        jdbc.query("""
                select distinct on (student_id) student_id, tier_key, division from student_tier_week
                where tenant_id = :t and student_id in (:ids) and school_year = :year and tier_key is not null
                order by student_id, week_start desc
                """, Map.of("t", tenantId, "ids", studentIds, "year", schoolYear(LocalDate.now(LeaderboardXp.ZONE))), rs -> {
            current.put(rs.getString("student_id"), rs.getString("tier_key"));
            divisions.put(rs.getString("student_id"), (Integer) rs.getObject("division"));
        });
        Map<String, String> chosen = new HashMap<>();
        Set<String> hasChoice = new HashSet<>();
        jdbc.query("select student_id, tier_key from student_frame_choice where tenant_id = :t and student_id in (:ids)",
                Map.of("t", tenantId, "ids", studentIds), rs -> {
            hasChoice.add(rs.getString("student_id"));
            chosen.put(rs.getString("student_id"), rs.getString("tier_key"));
        });
        for (String id : studentIds) {
            String frame = hasChoice.contains(id) ? chosen.get(id) : current.get(id);
            if (current.containsKey(id) || frame != null) result.put(id, new Tier(current.get(id), divisions.get(id), frame));
        }
        return result;
    }

    /** "2026-2027" for 1 Sep 2026 - 31 Aug 2027. */
    static String schoolYear(LocalDate day) {
        int start = LeaderboardXp.schoolYearStart(day);
        return start + "-" + (start + 1);
    }

    static LocalDate yearStart(LocalDate day) {
        return LocalDate.of(LeaderboardXp.schoolYearStart(day), 9, 1);
    }
}
