package com.erg.ergspringboot.enterpriseconnectors.persistence;

import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnterpriseConnectorRunRepository extends JpaRepository<EnterpriseConnectorRun, String> {
    Optional<EnterpriseConnectorRun> findByTenantIdAndId(String tenantId, String id);

    List<EnterpriseConnectorRun> findTop100ByTenantIdAndConnectorIdOrderByCreatedAtDesc(
            String tenantId, String connectorId);
}
