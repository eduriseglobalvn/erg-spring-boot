package com.erg.ergspringboot.curriculum;

import com.erg.ergspringboot.curriculum.persistence.CurriculumProgram;
import com.erg.ergspringboot.curriculum.persistence.CurriculumProgramRow;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.curriculum.persistence.CurriculumProgramRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumProgramRowRepository;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumProgramRequest;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumProgramResponse;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumProgramRowRequest;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumProgramRowResponse;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurriculumProgramService {
    private final CurriculumProgramRepository programs;
    private final CurriculumProgramRowRepository rows;

    public CurriculumProgramService(CurriculumProgramRepository programs, CurriculumProgramRowRepository rows) {
        this.programs = programs;
        this.rows = rows;
    }

    @Transactional(readOnly = true)
    public CurriculumProgramResponse get(String subjectId, String levelId, String academicYear,
                                         UserAccessContext access) {
        String cleanSubjectId = normalizeId(subjectId);
        String cleanLevelId = normalizeId(levelId);
        return programs.findByTenantIdAndSubjectIdAndLevelIdAndAcademicYear(
                        access.tenantId(), cleanSubjectId, cleanLevelId, academicYear)
                .map(program -> toResponse(program, rows.findByTenantIdAndProgramIdOrderBySortOrderAscPeriodAsc(
                        access.tenantId(), program.getId())))
                .orElse(null);
    }

    @Transactional
    public CurriculumProgramResponse upsert(String subjectId, String levelId, String academicYear,
                                             CurriculumProgramRequest request, UserAccessContext access) {
        if (request == null || request.rows() == null) {
            throw CurriculumException.badRequest("CURRICULUM_PROGRAM_ROWS_REQUIRED", "rows is required");
        }
        String cleanSubjectId = normalizeId(subjectId);
        String cleanLevelId = normalizeId(levelId);
        CurriculumProgram program = programs.findByTenantIdAndSubjectIdAndLevelIdAndAcademicYear(
                        access.tenantId(), cleanSubjectId, cleanLevelId, academicYear)
                .orElseGet(CurriculumProgram::new);
        if (request.expectedVersion() != null && program.getId() != null
                && !request.expectedVersion().equals(program.getRecordVersion())) {
            throw CurriculumException.conflict("CURRICULUM_PROGRAM_VERSION_CONFLICT", "program was changed by another user");
        }
        program.setTenantId(access.tenantId());
        program.setSubjectId(cleanSubjectId);
        program.setLevelId(cleanLevelId);
        program.setAcademicYear(academicYear);
        program.setName(request.name() == null || request.name().isBlank() ? "Phân phối chương trình" : request.name().trim());
        program.setStatus(request.status() == null || request.status().isBlank() ? "draft" : request.status().trim());
        program.setCreatedBy(program.getCreatedBy() == null ? access.userId() : program.getCreatedBy());
        program.setUpdatedBy(access.userId());
        CurriculumProgram saved = programs.save(program);
        rows.deleteByTenantIdAndProgramId(access.tenantId(), saved.getId());
        rows.flush();
        List<CurriculumProgramRowRequest> orderedRows = request.rows().stream().sorted(Comparator.comparing(
                        row -> row.period() == null ? Integer.MAX_VALUE : row.period())).toList();
        List<CurriculumProgramRow> savedRows = IntStream.range(0, orderedRows.size())
                .mapToObj(index -> toRow(orderedRows.get(index), saved, access, index)).toList();
        rows.saveAll(savedRows);
        return toResponse(saved, rows.findByTenantIdAndProgramIdOrderBySortOrderAscPeriodAsc(access.tenantId(), saved.getId()));
    }

    private static String normalizeId(String id) {
        if (id == null) return null;
        if (id.startsWith("category-")) return id.substring("category-".length());
        if (id.startsWith("level-")) return id.substring("level-".length());
        if (id.startsWith("subject-")) return id.substring("subject-".length());
        return id;
    }

    private CurriculumProgramRow toRow(CurriculumProgramRowRequest request, CurriculumProgram program,
                                       UserAccessContext access, int index) {
        if (request.topic() == null || request.topic().isBlank() || request.title() == null || request.title().isBlank()) {
            throw CurriculumException.badRequest("CURRICULUM_PROGRAM_ROW_INVALID", "topic and title are required");
        }
        CurriculumProgramRow row = new CurriculumProgramRow();
        row.setTenantId(access.tenantId());
        row.setProgramId(program.getId());
        row.setTopic(request.topic().trim());
        row.setPeriod(request.period() == null ? index + 1 : request.period());
        row.setTitle(request.title().trim());
        row.setSessions(request.sessions() == null || request.sessions() < 1 ? 1 : request.sessions());
        row.setSortOrder(index);
        row.setStatus(request.status() == null || request.status().isBlank() ? "active" : request.status());
        return row;
    }

    private static CurriculumProgramResponse toResponse(CurriculumProgram program, List<CurriculumProgramRow> rows) {
        return new CurriculumProgramResponse(program.getId(), program.getTenantId(), program.getSubjectId(), program.getLevelId(),
                program.getAcademicYear(), program.getName(), program.getStatus(), program.getRecordVersion(),
                rows.stream().map(row -> new CurriculumProgramRowResponse(row.getId(), row.getTopic(), row.getPeriod(), row.getTitle(),
                        row.getSessions(), row.getSortOrder(), row.getStatus(), row.getRecordVersion())).toList(),
                program.getCreatedAt(), program.getUpdatedAt());
    }
}
