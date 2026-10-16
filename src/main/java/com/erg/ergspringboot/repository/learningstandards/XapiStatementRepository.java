package com.erg.ergspringboot.repository.learningstandards;

import com.erg.ergspringboot.model.learningstandards.XapiStatement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface XapiStatementRepository extends JpaRepository<XapiStatement, String> {
    Optional<XapiStatement> findByTenantIdAndId(String tenantId, String id);

    Optional<XapiStatement> findByTenantIdAndStatementId(String tenantId, String statementId);
}
