package com.erg.ergspringboot.platform.security;

import java.util.List;

/**
 * Classes a teacher works with without a standing scope: homeroom, assigned work, or the published timetable (inside
 * the schedule access window). The LMS module implements it; the request principal carries the result as CLASS scope.
 */
public interface TeacherClassScopeProvider {
    List<String> classIds(String tenantId, String userId);
}
