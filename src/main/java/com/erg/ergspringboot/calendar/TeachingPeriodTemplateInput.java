package com.erg.ergspringboot.calendar;

import java.util.List;

/**
 * Input of saveTeachingPeriodTemplate: {@code id} null creates a template, else replaces its name and slots. Times are
 * "HH:mm"; a slot without times is a "trống giờ" period.
 */
public record TeachingPeriodTemplateInput(String id, String name, List<Slot> slots) {

    public record Slot(int periodNo, String session, String startTime, String endTime) {}
}
