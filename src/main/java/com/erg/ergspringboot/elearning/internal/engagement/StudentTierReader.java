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

/** Student-facing reads and writes of tiers and frames: the authenticated student's own rows only. */
@Service
public class StudentTierReader {

    private final StudentRepository students;
    private final StudentTierService service;
    private final NamedParameterJdbcTemplate jdbc;

    public StudentTierReader(StudentRepository students, StudentTierService service, JdbcTemplate jdbc) {
        this.students = students;
        this.service = service;
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    // ---- student reads ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MyTierResponse myTier(String tenantId, String userId) {
        Student student = requireStudent(tenantId, userId);
        LocalDate today = LocalDate.now(LeaderboardXp.ZONE);
        String year = schoolYear(today);
        Candidate live = service.readXp(tenantId, List.of(student.getId()), yearStart(today), ZonedDateTime.now(LeaderboardXp.ZONE), today).get(student.getId());
        Row row = latestRow(tenantId, student.getId(), year);
        if (row == null || row.tierKey() == null) {
            return new MyTierResponse(null, null, row == null ? null : row.gradeRank(), row == null ? null : row.gradeSize(),
                    live.xp(), live.submitted() >= TierMath.MIN_SUBMITTED, live.submitted(), false, null, null, promotion(tenantId, student.getId(), year));
        }
        List<Cell> cells = cells(tenantId, row.schoolId(), row.grade(), row.weekStart());
        int myPosition = TierMath.position(row.tierKey(), row.division());
        TierStepResponse nextDivision = null;
        TierStepResponse nextTier = null;
        for (Cell cell : cells) {
            int position = TierMath.position(cell.tierKey(), cell.division());
            if (position <= myPosition) continue;
            TierStepResponse step = new TierStepResponse(cell.tierKey(), cell.division(), cell.minXp(),
                    row.gradeRank() == null ? 0 : Math.max(0, row.gradeRank() - cell.rankTo()));
            boolean sameTier = cell.tierKey().equals(row.tierKey());
            // The nearest cell above: the next division of this tier, or the entry cell of the next tier.
            if (sameTier && (nextDivision == null || position < TierMath.position(nextDivision.tierKey(), nextDivision.division()))) nextDivision = step;
            if (!sameTier && (nextTier == null || position < TierMath.position(nextTier.tierKey(), nextTier.division()))) nextTier = step;
        }
        return new MyTierResponse(row.tierKey(), row.division(), row.gradeRank(), row.gradeSize(), live.xp(),
                live.submitted() >= TierMath.MIN_SUBMITTED, live.submitted(), row.protect(), nextDivision, nextTier,
                promotion(tenantId, student.getId(), year));
    }

    @Transactional(readOnly = true)
    public List<TierTableRowResponse> tierTable(String tenantId, String userId, String weekStart) {
        Student student = requireStudent(tenantId, userId);
        StudentTierService.Cohort cohort = cohortOf(tenantId, student.getId());
        LocalDate week;
        try {
            week = weekStart == null || weekStart.isBlank() ? null : LocalDate.parse(weekStart.trim());
        } catch (DateTimeParseException invalid) {
            throw new BadRequestException("weekStart must be a date (yyyy-MM-dd).");
        }
        List<Cell> cells = cohort == null ? List.of() : cells(tenantId, cohort.schoolId(), cohort.grade(), week);
        List<TierTableRowResponse> table = new ArrayList<>();
        for (TierMath.TierDef def : TierMath.TIERS) {
            List<Cell> own = cells.stream().filter(cell -> cell.tierKey().equals(def.key()))
                    .sorted(Comparator.comparingInt(Cell::rankFrom)).toList();
            table.add(new TierTableRowResponse(def.key(), def.share(), own.stream().mapToInt(c -> c.rankTo() - c.rankFrom() + 1).sum(),
                    own.stream().map(c -> new TierCellResponse(c.division(), c.rankFrom(), c.rankTo(), c.minXp())).toList()));
        }
        return table;
    }

    @Transactional(readOnly = true)
    public MyFramesResponse myFrames(String tenantId, String userId) {
        Student student = requireStudent(tenantId, userId);
        return frames(tenantId, student.getId());
    }

    /** {@code tierKey} null = "Không dùng". A frame that is not unlocked is refused. */
    @Transactional
    public MyFramesResponse selectFrame(String tenantId, String userId, String tierKey) {
        Student student = requireStudent(tenantId, userId);
        String key = tierKey == null || tierKey.isBlank() ? null : tierKey.trim();
        if (key != null) {
            if (TierMath.TIERS.stream().noneMatch(def -> def.key().equals(key))) throw new BadRequestException("Unknown frame.");
            Integer unlocked = jdbc.queryForObject("select count(*) from student_frame where tenant_id = :t and student_id = :s and tier_key = :k",
                    Map.of("t", tenantId, "s", student.getId(), "k", key), Integer.class);
            if (unlocked == null || unlocked == 0) throw new BadRequestException("This frame is not unlocked yet.");
        }
        jdbc.update("""
                insert into student_frame_choice (tenant_id, student_id, tier_key, updated_at) values (:t, :s, :k, now())
                on conflict (tenant_id, student_id) do update set tier_key = excluded.tier_key, updated_at = now()
                """, new MapSqlParameterSource("t", tenantId).addValue("s", student.getId()).addValue("k", key));
        return frames(tenantId, student.getId());
    }

    /** Marks the promotion of {@code weekStart} (and every earlier one) as seen; unknown weeks change nothing. */
    @Transactional
    public boolean acknowledgePromotion(String tenantId, String userId, String weekStart) {
        Student student = requireStudent(tenantId, userId);
        LocalDate week;
        try {
            week = LocalDate.parse(weekStart == null ? "" : weekStart.trim());
        } catch (DateTimeParseException invalid) {
            throw new BadRequestException("weekStart must be a date (yyyy-MM-dd).");
        }
        List<SqlParameterSource> seen = promotions(tenantId, student.getId(), schoolYear(week)).stream()
                .filter(p -> !p.weekStart().isAfter(week))
                .map(p -> (SqlParameterSource) new MapSqlParameterSource("t", tenantId).addValue("s", student.getId()).addValue("w", p.weekStart()))
                .toList();
        jdbc.batchUpdate("insert into student_tier_seen (tenant_id, student_id, week_start) values (:t, :s, :w) on conflict do nothing",
                seen.toArray(SqlParameterSource[]::new));
        return true;
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private MyFramesResponse frames(String tenantId, String studentId) {
        Map<String, LocalDate> unlockedAt = new HashMap<>();
        Map<String, String> unlockedYear = new HashMap<>();
        jdbc.query("select tier_key, unlocked_at, school_year from student_frame where tenant_id = :t and student_id = :s",
                Map.of("t", tenantId, "s", studentId), rs -> {
            unlockedAt.put(rs.getString("tier_key"), rs.getObject("unlocked_at", LocalDate.class));
            unlockedYear.put(rs.getString("tier_key"), rs.getString("school_year"));
        });
        Row row = latestRow(tenantId, studentId, schoolYear(LocalDate.now(LeaderboardXp.ZONE)));
        List<Cell> cells = row == null ? List.of() : cells(tenantId, row.schoolId(), row.grade(), row.weekStart());
        List<FrameResponse> frames = new ArrayList<>();
        for (TierMath.TierDef def : TierMath.TIERS) {
            boolean unlocked = unlockedAt.containsKey(def.key());
            Integer toPass = null;
            if (!unlocked && row != null && row.gradeRank() != null) {
                int lowestRank = cells.stream().filter(c -> c.tierKey().equals(def.key())).mapToInt(Cell::rankTo).max().orElse(-1);
                if (lowestRank > 0) toPass = Math.max(0, row.gradeRank() - lowestRank);
            }
            frames.add(new FrameResponse(def.key(), unlocked, unlocked ? unlockedAt.get(def.key()).toString() : null,
                    unlockedYear.get(def.key()), toPass));
        }
        Map<String, Object> choice = jdbc.query("select tier_key from student_frame_choice where tenant_id = :t and student_id = :s",
                Map.of("t", tenantId, "s", studentId), rs -> rs.next() ? java.util.Collections.singletonMap("tier", rs.getString("tier_key")) : null);
        String selected = choice != null ? (String) choice.get("tier") : row == null ? null : row.tierKey();
        return new MyFramesResponse(selected, frames);
    }

    private TierPromotionResponse promotion(String tenantId, String studentId, String year) {
        Set<LocalDate> seen = new HashSet<>(jdbc.queryForList("select week_start from student_tier_seen where tenant_id = :t and student_id = :s",
                Map.of("t", tenantId, "s", studentId), LocalDate.class));
        return promotions(tenantId, studentId, year).stream().filter(p -> !seen.contains(p.weekStart()))
                .max(Comparator.comparingInt((Promotion p) -> TierMath.position(p.toKey(), p.toDivision())).thenComparing(Promotion::weekStart))
                .map(p -> new TierPromotionResponse(p.weekStart().toString(), p.fromKey(), p.fromDivision(), p.toKey(), p.toDivision(),
                        p.studentsPassed(), p.percentile()))
                .orElse(null);
    }

    /** Weeks in which the tier (not just the division) went up from a previous tier of the same school year. */
    private List<Promotion> promotions(String tenantId, String studentId, String year) {
        List<Row> rows = jdbc.query("""
                select * from student_tier_week where tenant_id = :t and student_id = :s and school_year = :year order by week_start
                """, Map.of("t", tenantId, "s", studentId, "year", year), (rs, n) -> row(rs));
        List<Promotion> result = new ArrayList<>();
        Row before = null;
        for (Row row : rows) {
            if (before != null && before.tierKey() != null && row.tierKey() != null && !before.tierKey().equals(row.tierKey())
                    && TierMath.position(row.tierKey(), row.division()) > TierMath.position(before.tierKey(), before.division())) {
                int passed = before.gradeRank() == null || row.gradeRank() == null ? 0 : Math.max(0, before.gradeRank() - row.gradeRank());
                int percentile = row.gradeRank() == null || row.gradeSize() == null || row.gradeSize() == 0 ? 0
                        : (int) Math.ceil(100.0 * row.gradeRank() / row.gradeSize());
                result.add(new Promotion(row.weekStart(), before.tierKey(), before.division(), row.tierKey(), row.division(), passed, percentile));
            }
            before = row;
        }
        return result;
    }

    private Row latestRow(String tenantId, String studentId, String year) {
        return jdbc.query("""
                select * from student_tier_week where tenant_id = :t and student_id = :s and school_year = :year
                order by week_start desc limit 1
                """, Map.of("t", tenantId, "s", studentId, "year", year), rs -> rs.next() ? row(rs) : null);
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getString("school_id"), rs.getString("grade"), rs.getObject("week_start", LocalDate.class),
                (Integer) rs.getObject("grade_rank"), (Integer) rs.getObject("grade_size"), rs.getString("tier_key"),
                (Integer) rs.getObject("division"), rs.getBoolean("protected"));
    }

    /** Cutoff cells of a cohort for {@code week}, or its latest stored week when null. */
    private List<Cell> cells(String tenantId, String schoolId, String grade, LocalDate week) {
        return jdbc.query("""
                select tier_key, division, rank_from, rank_to, min_xp from student_tier_cutoff
                where tenant_id = :t and school_id = :school and grade = :grade
                  and week_start = coalesce(cast(:week as date), (select max(week_start) from student_tier_cutoff
                                                    where tenant_id = :t and school_id = :school and grade = :grade))
                order by rank_from
                """, new MapSqlParameterSource("t", tenantId).addValue("school", schoolId).addValue("grade", grade).addValue("week", week),
                (rs, n) -> new Cell(rs.getString("tier_key"), rs.getInt("division") == 0 ? null : rs.getInt("division"),
                        rs.getInt("rank_from"), rs.getInt("rank_to"), rs.getInt("min_xp")));
    }

    private StudentTierService.Cohort cohortOf(String tenantId, String studentId) {
        return jdbc.query("""
                select c.school_id, c.grade from lms_students s
                join academic_classes c on c.tenant_id = s.tenant_id and c.id = s.academic_class_id
                where s.tenant_id = :t and s.id = :s
                """, Map.of("t", tenantId, "s", studentId), rs -> rs.next() ? new StudentTierService.Cohort(tenantId, rs.getString(1), rs.getString(2)) : null);
    }

    private Student requireStudent(String tenantId, String userId) {
        return students.findFirstByTenantIdAndAuthUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated student was not found."));
    }

    private static String schoolYear(LocalDate day) {
        return StudentTierService.schoolYear(day);
    }

    private static LocalDate yearStart(LocalDate day) {
        return StudentTierService.yearStart(day);
    }

    private record Row(String schoolId, String grade, LocalDate weekStart, Integer gradeRank, Integer gradeSize, String tierKey,
                       Integer division, boolean protect) { }

    private record Promotion(LocalDate weekStart, String fromKey, Integer fromDivision, String toKey, Integer toDivision,
                             int studentsPassed, int percentile) { }
}
