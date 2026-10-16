package com.erg.ergspringboot.reporting.authorization;
import java.lang.annotation.*; import org.springframework.security.access.prepost.PreAuthorize;
@Documented @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'lms.report.read', 'lms.grade.read', 'lms.manage', '*')") public @interface ReportingRead { }
