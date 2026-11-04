package com.erg.ergspringboot.learningoutcomes.internal;

import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPath;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPathRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Tenant-scoped learning path reads shared by the path, attainment, completion and certificate use cases. */
@Component
public class LearningPathLookup {
    private final LearningPathRepository paths;

    public LearningPathLookup(LearningPathRepository paths) {
        this.paths = paths;
    }

    Optional<LearningPath> find(String tenant, String id) {
        return paths.findByTenantIdAndId(tenant, id);
    }

    LearningPath require(String tenant, String id) {
        return find(tenant, id).orElseThrow(() -> new ResourceNotFoundException("learning path not found"));
    }

    /** SQ-10: one batched lookup for a page of rows; paths of another tenant are dropped. */
    Map<String, LearningPath> byIds(String tenant, List<String> ids) {
        List<String> distinct = ids.stream().map(OutcomeValues::trimToNull).filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        Map<String, LearningPath> byId = new HashMap<>();
        paths.findAllById(distinct).forEach(path -> {
            if (tenant.equals(path.getTenantId())) byId.put(path.getId(), path);
        });
        return byId;
    }
}
