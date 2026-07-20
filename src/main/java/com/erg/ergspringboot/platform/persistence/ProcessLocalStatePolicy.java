package com.erg.ergspringboot.platform.persistence;

import com.erg.ergspringboot.exception.NotImplementedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Blocks demo-only in-memory business stores from being exposed in production. */
@Component
public class ProcessLocalStatePolicy {
    private final boolean enabled;

    public ProcessLocalStatePolicy(@Value("${erg.process-local.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public void assertEnabled(String storeName) {
        if (!enabled) {
            throw new NotImplementedException("PROCESS_LOCAL_STATE_DISABLED",
                    storeName + " is disabled in production until a tenant-scoped persistent store is configured");
        }
    }
}
