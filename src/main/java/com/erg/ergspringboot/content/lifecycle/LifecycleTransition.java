package com.erg.ergspringboot.content.lifecycle;

/** Pure, serializable transition decision used by the application service and unit tests. */
public record LifecycleTransition(
        LifecycleState from,
        LifecycleState to,
        boolean rollback
) { }
