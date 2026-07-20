package com.erg.ergspringboot.common.response.school;

import com.erg.ergspringboot.common.response.school.SchoolResponses.AcademicYearResponse;
import com.erg.ergspringboot.common.response.school.SchoolResponses.GradeResponse;
import java.util.List;

public final class SchoolOperationsResponses {
    private SchoolOperationsResponses() {}

    public record SchoolDirectoryWorkspace(
            List<SchoolDirectoryItem> items, int page, int size, long totalItems,
            int totalPages, boolean hasNext, boolean hasPrevious
    ) {}

    public record SchoolDirectoryItem(
            String id, String name, String slug, String schoolType, String status, String district,
            String academicYear, long classCount, long studentCount, long version
    ) {}

    public record SchoolOperationsWorkspace(
            SchoolOperationsProfile school, SchoolOperationsSummary summary,
            List<AcademicYearResponse> academicYears, List<GradeResponse> grades,
            List<SchoolClass> classes, List<SchoolStudent> students, List<SchoolSubject> subjects,
            SchoolOperationsPermissions permissions
    ) {}

    public record SchoolOperationsProfile(
            String id, String name, String slug, String schoolType, String status,
            String address, String district, String principalName, String contactPhone,
            String contactEmail, String currentAcademicYearId, long version
    ) {}

    public record SchoolOperationsSummary(long classCount, long activeClassCount, long studentCount, int gradeCount) {}

    public record SchoolClass(String id, String name, String grade, String homeroomTeacher, long studentCount, String status) {}

    public record SchoolStudent(
            String id, String code, String fullName, String birthDate, String grade, String className,
            String guardianPhone, List<String> subjectIds, String status, Double averageScore
    ) {}

    public record SchoolSubject(String id, String name, String code, long studentCount, int teacherCount) {}

    public record SchoolOperationsPermissions(
            boolean canCreateSchool, boolean canUpdateSchool, boolean canArchiveSchool, boolean canManageClasses
    ) {}
}
