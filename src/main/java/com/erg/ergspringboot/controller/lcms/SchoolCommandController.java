package com.erg.ergspringboot.controller.lcms;

import static com.erg.ergspringboot.common.request.school.SchoolRequests.*;
import static com.erg.ergspringboot.common.response.school.SchoolResponses.*;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.school.SchoolOperations;
import com.erg.ergspringboot.lcms.authorization.LcmsSchoolCreate;
import com.erg.ergspringboot.lcms.authorization.LcmsSchoolUpdate;
import com.erg.ergspringboot.lcms.authorization.LcmsSchoolArchive;
import com.erg.ergspringboot.lcms.authorization.LcmsClassManage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lcms/schools")
public class SchoolCommandController {
    private final SchoolOperations operations;

    public SchoolCommandController(SchoolOperations operations) {
        this.operations = operations;
    }

    @PostMapping
    @LcmsSchoolCreate
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SchoolResponse> create(@Valid @RequestBody CreateSchoolRequest body, HttpServletRequest request) {
        return success(201, "School created", operations.create(body, UserAccessContext.from(request)), request);
    }

    @PutMapping("/{schoolId}")
    @LcmsSchoolUpdate
    public ApiResponse<SchoolResponse> replace(@PathVariable String schoolId, @Valid @RequestBody UpdateSchoolRequest body,
                                               HttpServletRequest request) {
        return success(200, "School updated", operations.update(schoolId, body, UserAccessContext.from(request)), request);
    }

    @PatchMapping("/{schoolId}")
    @LcmsSchoolUpdate
    public ApiResponse<SchoolResponse> patch(@PathVariable String schoolId, @Valid @RequestBody UpdateSchoolRequest body,
                                             HttpServletRequest request) {
        return success(200, "School updated", operations.update(schoolId, body, UserAccessContext.from(request)), request);
    }

    @DeleteMapping("/{schoolId}")
    @LcmsSchoolArchive
    public ApiResponse<CommandResult> archive(@PathVariable String schoolId, HttpServletRequest request) {
        return success(200, "School archived", operations.archive(schoolId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/{schoolId}/restore")
    @LcmsSchoolArchive
    public ApiResponse<CommandResult> restore(@PathVariable String schoolId, HttpServletRequest request) {
        return success(200, "School restored", operations.restore(schoolId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/{schoolId}/academic-years")
    @LcmsSchoolUpdate
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AcademicYearResponse> createYear(@PathVariable String schoolId,
            @Valid @RequestBody CreateAcademicYearRequest body, HttpServletRequest request) {
        return success(201, "Academic year created", operations.createAcademicYear(schoolId, body, UserAccessContext.from(request)), request);
    }

    @PostMapping("/{schoolId}/academic-years/{yearId}/activate")
    @LcmsSchoolUpdate
    public ApiResponse<AcademicYearResponse> activateYear(@PathVariable String schoolId, @PathVariable String yearId,
            @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        return success(200, "Academic year activated", operations.activateAcademicYear(schoolId, yearId, body, UserAccessContext.from(request)), request);
    }

    @PostMapping("/{schoolId}/academic-years/{yearId}/close")
    @LcmsSchoolUpdate
    public ApiResponse<AcademicYearResponse> closeYear(@PathVariable String schoolId, @PathVariable String yearId,
            @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        return success(200, "Academic year closed", operations.closeAcademicYear(schoolId, yearId, body, UserAccessContext.from(request)), request);
    }

    @PostMapping("/{schoolId}/grades/sync-defaults")
    @LcmsSchoolUpdate
    public ApiResponse<GradeSyncResponse> syncGrades(@PathVariable String schoolId, HttpServletRequest request) {
        return success(200, "Default grades synchronized", operations.syncDefaultGrades(schoolId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/{schoolId}/grades")
    @LcmsClassManage
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GradeResponse> createGrade(@PathVariable String schoolId,
            @Valid @RequestBody CreateGradeRequest body, HttpServletRequest request) {
        return success(201, "Grade created", operations.createGrade(schoolId, body, UserAccessContext.from(request)), request);
    }

    @PatchMapping("/{schoolId}/grades/{gradeId}")
    @LcmsClassManage
    public ApiResponse<GradeResponse> updateGrade(@PathVariable String schoolId, @PathVariable String gradeId,
            @Valid @RequestBody UpdateGradeRequest body, HttpServletRequest request) {
        return success(200, "Grade updated", operations.updateGrade(schoolId, gradeId, body, UserAccessContext.from(request)), request);
    }

    private <T> ApiResponse<T> success(int status, String message, T data, HttpServletRequest request) {
        String resolvedRequestId = com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
        return ApiResponse.success(status, message, data, request.getRequestURI(), resolvedRequestId);
    }
}
