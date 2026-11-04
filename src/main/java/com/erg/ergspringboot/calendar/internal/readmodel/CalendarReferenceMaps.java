package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Level;
import com.erg.ergspringboot.lms.persistence.Subject;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.User;
import java.util.Map;

/**
 * Module-internal reference projection used while assembling Calendar read
 * models. It holds persistence entities, so it stays inside
 * {@code calendar.internal.readmodel} and must never appear on a public port.
 */
public record CalendarReferenceMaps(
        Map<String, EducationUnit> schoolMap,
        Map<String, Classroom> classMap,
        Map<String, Subject> subjectMap,
        Map<String, Level> levelMap,
        Map<String, User> userMap,
        Map<String, String> teacherColorKeyMap
) { }
