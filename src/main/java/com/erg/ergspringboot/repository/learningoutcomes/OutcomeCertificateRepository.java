package com.erg.ergspringboot.repository.learningoutcomes;

import com.erg.ergspringboot.model.learningoutcomes.OutcomeCertificate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutcomeCertificateRepository extends JpaRepository<OutcomeCertificate, String> {
    Optional<OutcomeCertificate> findByTenantIdAndId(String tenantId, String id);
    Optional<OutcomeCertificate> findByTenantIdAndCertificateNumber(String tenantId, String certificateNumber);
    Optional<OutcomeCertificate> findByTenantIdAndVerificationId(String tenantId, String verificationId);
    Optional<OutcomeCertificate> findByTenantIdAndLearnerIdAndLearningPathId(
            String tenantId, String learnerId, String learningPathId);
    List<OutcomeCertificate> findTop100ByTenantIdAndLearnerIdOrderByIssuedAtDesc(
            String tenantId, String learnerId);
    List<OutcomeCertificate> findAllByTenantIdAndLearnerIdOrderByIssuedAtDesc(
            String tenantId, String learnerId);
}
