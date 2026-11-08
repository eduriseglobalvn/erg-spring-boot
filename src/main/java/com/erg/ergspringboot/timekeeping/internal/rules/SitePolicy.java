package com.erg.ergspringboot.timekeeping.internal.rules;

/** Where and how far from it a punch counts; {@code allowOutside} lets staff punch outside the zone with a reason. */
public record SitePolicy(double lat, double lng, int radiusM, boolean allowOutside) {}
