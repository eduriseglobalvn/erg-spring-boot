package com.erg.ergspringboot.content.persistence;

import com.erg.ergspringboot.content.persistence.ContentLifecycleTransition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentLifecycleTransitionRepository extends JpaRepository<ContentLifecycleTransition, String> {
    Optional<ContentLifecycleTransition> findByTenantIdAndResourceKindAndResourceIdAndIdempotencyKey(
            String tenantId, String resourceKind, String resourceId, String idempotencyKey);

    Optional<ContentLifecycleTransition> findTopByTenantIdAndResourceKindAndResourceIdAndToStateOrderByCreatedAtDesc(
            String tenantId, String resourceKind, String resourceId, String toState);
}
