package com.erg.ergspringboot.notifications.internal.campaign;

import java.util.List;

/** Distinct recipient user ids in stable order: scopes first (classes in class order), then picked users. */
public record ResolvedAudience(List<String> userIds, AudienceBreakdown breakdown) {}
