package com.erg.ergspringboot.timekeeping.internal.evidence;

import java.net.URI;
import java.time.Duration;

/** Private object store for punch selfies: nothing in it is ever publicly readable, reads go through short-lived URLs. */
interface PrivateObjectStorage {

    void put(String key, byte[] bytes, String contentType);

    URI presignGet(String key, Duration ttl);

    /** Idempotent: deleting a missing object succeeds. */
    void delete(String key);
}
