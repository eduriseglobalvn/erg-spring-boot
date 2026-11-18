package com.erg.ergspringboot.lms.authorization;
import java.lang.annotation.*; import org.springframework.security.access.prepost.PreAuthorize;
// Teachers hold lms.student_group.update; the class scope check in the service limits them to their own classes.
@Documented @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'lms.student.manage', 'lms.student_group.update', '*')") public @interface LmsStudentCredentialReset { }
