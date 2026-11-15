package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.calendar.TeachingPeriodTemplateInput.Slot;
import java.util.List;

/** Input of saveSchoolPeriodTimes: a new template for one school that applies from {@code effectiveFrom} (yyyy-MM-dd). */
public record SchoolPeriodTimesInput(String schoolId, String effectiveFrom, String name, List<Slot> slots) {}
