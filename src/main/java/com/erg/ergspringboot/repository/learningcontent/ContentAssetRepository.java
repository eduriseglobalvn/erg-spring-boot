package com.erg.ergspringboot.repository.learningcontent;

import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentAssetRepository extends JpaRepository<ContentAsset, String> {

    Optional<ContentAsset> findByIdAndTenantId(String id, String tenantId);

    List<ContentAsset> findAllByTenantIdAndIdIn(String tenantId, Collection<String> ids);
}
