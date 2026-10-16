package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import java.util.Map;

/**
 * Compatibility-only reference projection used while assembling Calendar read
 * models. It intentionally contains persistence entities for the old
 * {@code CalendarReadService} projection helpers and must not be used by new
 * public API methods. Remove after the legacy calendar read adapter is retired.
 *
 * <p>The projection deliberately lives in the Calendar module instead of in a
 * horizontal service package so read-model assemblers do not depend on the
 * legacy compatibility adapter.</p>
 */
@Deprecated(since = "2026-08-08")
public record CalendarReferenceMaps(
        Map<String, EducationUnit> schoolMap,
        Map<String, Classroom> classMap,
        Map<String, Subject> subjectMap,
        Map<String, Level> levelMap,
        Map<String, User> userMap,
        Map<String, String> teacherColorKeyMap
) { }
