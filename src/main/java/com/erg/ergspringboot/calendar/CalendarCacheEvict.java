package com.erg.ergspringboot.calendar;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.cache.annotation.CacheEvict;

/** Every calendar write evicts the workspace, the overview week and the catalog together, so the three never disagree. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@CacheEvict(cacheNames = {CalendarCaches.WORKSPACE, CalendarCaches.OVERVIEW_WEEK, CalendarCaches.CATALOG}, allEntries = true)
public @interface CalendarCacheEvict {
}
