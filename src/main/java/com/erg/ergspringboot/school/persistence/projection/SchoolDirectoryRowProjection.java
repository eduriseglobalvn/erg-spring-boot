package com.erg.ergspringboot.school.persistence.projection;

public interface SchoolDirectoryRowProjection {
    String getId();
    String getName();
    String getSlug();
    String getSchoolType();
    String getStatus();
    String getDistrict();
    Integer getAcademicYearStart();
    Integer getAcademicYearEnd();
    Long getClassCount();
    Long getStudentCount();
    Long getVersion();
}
