package com.erg.ergspringboot.repository.learningcontent;

import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentItemAssetRepository extends JpaRepository<ContentItemAsset, String> {

    Optional<ContentItemAsset> findByIdAndTenantIdAndContentItemId(String id, String tenantId, String contentItemId);

    List<ContentItemAsset> findAllByTenantIdAndContentItemId(String tenantId, String contentItemId);

    List<ContentItemAsset> findAllByTenantIdAndContentItemIdIn(String tenantId, List<String> contentItemIds);

    @Query("""
            select coalesce(max(link.sortOrder), 0) + 1
            from ContentItemAsset link
            where link.tenantId = :tenantId
              and link.contentItemId = :contentItemId
            """)
    Integer nextSortOrder(@Param("tenantId") String tenantId, @Param("contentItemId") String contentItemId);
}
