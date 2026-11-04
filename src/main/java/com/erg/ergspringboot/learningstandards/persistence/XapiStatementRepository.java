package com.erg.ergspringboot.learningstandards.persistence;

import com.erg.ergspringboot.learningstandards.persistence.XapiStatement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface XapiStatementRepository extends JpaRepository<XapiStatement, String> {
    Optional<XapiStatement> findByTenantIdAndId(String tenantId, String id);

    Optional<XapiStatement> findByTenantIdAndStatementId(String tenantId, String statementId);
}
