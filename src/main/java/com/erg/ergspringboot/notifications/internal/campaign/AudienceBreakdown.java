package com.erg.ergspringboot.notifications.internal.campaign;

/**
 * {@code teachers/students/staff} count people reached by a scope or role (each person once, in the first
 * group that reached them); {@code picked} counts the individually chosen, {@code duplicatesMerged} how many of
 * those were already reached by a scope.
 */
public record AudienceBreakdown(int total, int teachers, int students, int staff, int picked, int duplicatesMerged) {}
