package com.erg.ergspringboot.repository.enterpriseconnectors;

import com.erg.ergspringboot.model.enterpriseconnectors.EnterpriseConnectorCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnterpriseConnectorCommandReceiptRepository
        extends JpaRepository<EnterpriseConnectorCommandReceipt, String> {
    Optional<EnterpriseConnectorCommandReceipt> findByTenantIdAndOperationAndIdempotencyKey(
            String tenantId, String operation, String idempotencyKey);
}
