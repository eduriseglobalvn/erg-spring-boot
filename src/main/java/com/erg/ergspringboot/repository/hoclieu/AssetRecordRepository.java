package com.erg.ergspringboot.repository.hoclieu;

import com.erg.ergspringboot.model.hoclieu.AssetRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRecordRepository extends JpaRepository<AssetRecord, String> {

    List<AssetRecord> findByResourceIdInOrderByUpdatedAtDesc(Collection<String> resourceIds);

    List<AssetRecord> findByResourceIdOrderByUpdatedAtDesc(String resourceId);

    long countByResourceId(String resourceId);

    void deleteByResourceId(String resourceId);
}
