package com.erg.ergspringboot.curriculum.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** Curriculum management reads (usage counts) that a plain curriculum reader must not see; deny-aware evaluator. */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'curriculum.manage', '*')")
public @interface CurriculumManage {
}
