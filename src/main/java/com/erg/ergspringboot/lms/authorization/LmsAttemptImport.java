package com.erg.ergspringboot.lms.authorization;
import java.lang.annotation.*; import org.springframework.security.access.prepost.PreAuthorize;
/** Learner (own offline result) or teacher/reviewer (results collected from a class); scope is enforced in the use case. */
@Documented @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'lms.assignment.submit', 'lms.attempt.grade', 'lms.grade.write', 'lms.manage', '*')") public @interface LmsAttemptImport { }
