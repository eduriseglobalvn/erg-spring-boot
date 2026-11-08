package com.erg.ergspringboot.timekeeping.internal.evidence;

import com.erg.ergspringboot.timekeeping.persistence.TkFaceConsentRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** One consent row per (tenant, user); recording again is a no-op. */
@Service
public class FaceConsentService {

    private final TkFaceConsentRepository consents;
    private final Clock clock;

    FaceConsentService(TkFaceConsentRepository consents, Clock clock) {
        this.consents = consents;
        this.clock = clock;
    }

    public boolean hasConsent(String tenantId, String userId) {
        return consents.existsByTenantIdAndUserId(tenantId, userId);
    }

    public void recordConsent(String tenantId, String userId) {
        consents.insertIfAbsent(IdGenerator.newId(), tenantId, userId, clock.instant());
    }
}
