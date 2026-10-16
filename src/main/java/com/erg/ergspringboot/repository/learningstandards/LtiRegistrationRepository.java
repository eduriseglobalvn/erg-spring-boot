package com.erg.ergspringboot.repository.learningstandards;

import com.erg.ergspringboot.model.learningstandards.LtiRegistration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LtiRegistrationRepository extends JpaRepository<LtiRegistration, String> {
    Optional<LtiRegistration> findByTenantIdAndId(String tenantId, String id);

    Optional<LtiRegistration> findByTenantIdAndIssuerAndClientId(String tenantId, String issuer, String clientId);

    List<LtiRegistration> findTop100ByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
