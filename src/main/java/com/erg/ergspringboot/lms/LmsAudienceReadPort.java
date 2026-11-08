package com.erg.ergspringboot.lms;

import java.util.Collection;
import java.util.List;

/**
 * Public, persistence-free read port for resolving notification audiences. Students are returned as
 * login user ids; students without a login account are skipped. {@code limit} bounds every list.
 */
public interface LmsAudienceReadPort {

    /** Classes of the given schools plus classes whose grade is in {@code grades}; ordered by name. */
    List<String> classIdsOf(String tenantId, Collection<String> schoolIds, Collection<String> grades);

    /** Classes a teacher runs: homeroom teacher, or assigned homework to. */
    List<String> classIdsTaughtBy(String tenantId, String teacherUserId);

    /** Student user ids grouped by {@code classIds} order, then by student name. */
    List<String> studentUserIdsInClasses(String tenantId, List<String> classIds, int limit);

    List<String> studentUserIdsInGroups(String tenantId, Collection<String> groupIds, int limit);

    List<String> allStudentUserIds(String tenantId, int limit);

    /** The subset of {@code userIds} that are students of one of {@code classIds}. */
    List<String> studentUserIdsInClassesAmong(String tenantId, Collection<String> classIds, Collection<String> userIds);
}
