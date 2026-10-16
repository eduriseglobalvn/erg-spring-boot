package com.erg.ergspringboot.lcms.authorization;
import java.lang.annotation.*; import org.springframework.security.access.prepost.PreAuthorize;
@Documented @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'lms.exercise.update', 'lms.quiz.create', '*')") public @interface LcmsQuizUpdate { }
