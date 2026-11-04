package com.erg.ergspringboot.enterpriseconnectors.persistence;

import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnector;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnterpriseConnectorRepository extends JpaRepository<EnterpriseConnector, String> {
    Optional<EnterpriseConnector> findByTenantIdAndId(String tenantId, String id);

    Optional<EnterpriseConnector> findByTenantIdAndProviderKey(String tenantId, String providerKey);

    List<EnterpriseConnector> findTop100ByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
