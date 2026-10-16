package com.erg.ergspringboot.lms.authorization;
import java.lang.annotation.*; import org.springframework.security.access.prepost.PreAuthorize;
@Documented @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'lms.grade.read', '*')") public @interface LmsGradeRead { }
