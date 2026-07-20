package com.erg.ergspringboot.repository.quiz.media;

import com.erg.ergspringboot.model.quiz.media.MediaAsset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {

    Optional<MediaAsset> findByTenantIdAndSha256(String tenantId, String sha256);

    Optional<MediaAsset> findByIdAndTenantId(String id, String tenantId);
}
