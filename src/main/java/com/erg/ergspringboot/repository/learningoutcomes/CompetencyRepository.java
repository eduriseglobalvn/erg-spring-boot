package com.erg.ergspringboot.repository.learningoutcomes;

import com.erg.ergspringboot.model.learningoutcomes.Competency;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyRepository extends JpaRepository<Competency, String> {
    Optional<Competency> findByTenantIdAndId(String tenantId, String id);
    Optional<Competency> findByTenantIdAndCode(String tenantId, String code);
    List<Competency> findTop100ByTenantIdOrderByUpdatedAtDesc(String tenantId);
    List<Competency> findTop100ByTenantIdAndEducationUnitIdOrderByUpdatedAtDesc(
            String tenantId, String educationUnitId);
}
