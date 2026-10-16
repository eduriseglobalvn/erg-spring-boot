package com.erg.ergspringboot.repository.learningstandards;

import com.erg.ergspringboot.model.learningstandards.LearningPackage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningPackageRepository extends JpaRepository<LearningPackage, String> {
    Optional<LearningPackage> findByTenantIdAndId(String tenantId, String id);

    Optional<LearningPackage> findByTenantIdAndArtifactKey(String tenantId, String artifactKey);

    List<LearningPackage> findTop100ByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<LearningPackage> findTop100ByTenantIdAndStatusOrderByUpdatedAtDesc(String tenantId, String status);
}
