package com.erg.ergspringboot.lms;

import java.util.Collection;
import java.util.List;

/**
 * Public read port for finding students to address in notifications. Only students still on the roll
 * with a login account are returned; {@code classIds == null} means the whole tenant.
 */
public interface LmsStudentLookupPort {

    record StudentCard(
            String userId, String fullName, String studentCode, String email, String phone, String className) {}

    /** Students whose code (upper-case), email (lower-case) or phone (digits only, as stored) is among the keys. */
    List<StudentCard> findByKeys(
            String tenantId, Collection<String> classIds,
            Collection<String> codes, Collection<String> emails, Collection<String> phones);

    /** Students ordered by name, at most {@code limit}. */
    List<StudentCard> listStudents(String tenantId, Collection<String> classIds, int limit);
}
