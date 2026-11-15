package com.erg.ergspringboot.calendar.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'timekeeping.manage', 'lms.schedule.read', 'lms.schedule.manage', 'lms.manage', '*')")
public @interface CalendarDayLoadRead {
}
