package com.erg.ergspringboot.lms.authorization;
import java.lang.annotation.*; import org.springframework.security.access.prepost.PreAuthorize;
/** Authoring readers or learners playing an assigned quiz; the use case enforces the assignment check for learners. */
@Documented @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'lms.quiz.read', 'lms.quiz.play', '*')") public @interface LmsQuizPackageRead { }
