package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.curriculum.persistence.CurriculumProgram;
import com.erg.ergspringboot.curriculum.persistence.CurriculumProgramRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumProgramRow;
import com.erg.ergspringboot.curriculum.persistence.CurriculumProgramRowRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumTeachingPlanSettingRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Reads the curriculum plan (PPCT start date) and the curriculum framework rows (KCT) for the class log. */
@Component
class CurriculumLookup {

    private final CurriculumTeachingPlanSettingRepository settings;
    private final CurriculumProgramRepository programs;
    private final CurriculumProgramRowRepository rows;

    CurriculumLookup(CurriculumTeachingPlanSettingRepository settings, CurriculumProgramRepository programs,
                     CurriculumProgramRowRepository rows) {
        this.settings = settings;
        this.programs = programs;
        this.rows = rows;
    }

    /** "2026-2027" for a date in Sep 2026 - Aug 2027 (school years start in September). */
    static String academicYear(LocalDate date) {
        int start = date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1;
        return start + "-" + (start + 1);
    }

    /** Memoises the lookups of one request; not shared between requests. */
    Scope scope(String tenantId) {
        return new Scope(tenantId);
    }

    final class Scope {
        private final String tenantId;
        private final Map<String, LocalDate> starts = new HashMap<>();
        private final Map<String, TreeMap<Integer, CurriculumProgramRow>> framework = new HashMap<>();

        private Scope(String tenantId) {
            this.tenantId = tenantId;
        }

        /** Plan start date of (school, subject) for the school year of {@code date}; Sep 1 when the plan is not set up. */
        LocalDate planStart(String schoolId, String subjectId, LocalDate date) {
            String year = academicYear(date);
            return starts.computeIfAbsent(schoolId + '|' + subjectId + '|' + year, k ->
                    settings.findByTenantIdAndSchoolIdAndSubjectIdAndAcademicYear(tenantId, schoolId, subjectId, year)
                            .map(s -> s.getStartDate())
                            .orElseGet(() -> LocalDate.of(Integer.parseInt(year.substring(0, 4)), 9, 1)));
        }

        /** KCT rows of the program of (subject, level, school year of date) keyed by period; empty when none. */
        TreeMap<Integer, CurriculumProgramRow> framework(String subjectId, String levelId, LocalDate date) {
            if (levelId == null) return new TreeMap<>();
            return framework.computeIfAbsent(subjectId + '|' + levelId + '|' + academicYear(date), k -> {
                TreeMap<Integer, CurriculumProgramRow> byPeriod = new TreeMap<>();
                Optional<CurriculumProgram> program = programs.findByTenantIdAndSubjectIdAndLevelIdAndAcademicYear(
                        tenantId, subjectId, levelId, academicYear(date));
                program.ifPresent(p -> {
                    List<CurriculumProgramRow> found = rows.findByTenantIdAndProgramIdOrderBySortOrderAscPeriodAsc(tenantId, p.getId());
                    found.stream().filter(r -> "active".equals(r.getStatus())).forEach(r -> byPeriod.putIfAbsent(r.getPeriod(), r));
                });
                return byPeriod;
            });
        }
    }
}
