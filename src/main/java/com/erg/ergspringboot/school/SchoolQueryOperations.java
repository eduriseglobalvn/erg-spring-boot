package com.erg.ergspringboot.school;

import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolDirectoryWorkspace;
import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolOperationsWorkspace;
import com.erg.ergspringboot.service.school.SchoolOperationsReadService;
import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Public read boundary for school directory and operational projections. */
@Service
public class SchoolQueryOperations {
    private final SchoolOperationsReadService reads;

    public SchoolQueryOperations(SchoolOperationsReadService reads) {
        this.reads = reads;
    }

    public SchoolDirectoryWorkspace directory(
            String tenantId, Collection<String> schoolIds, boolean allScope,
            String search, String status, String schoolType, Integer page, Integer size
    ) {
        return reads.directory(tenantId, schoolIds, allScope, search, status, schoolType, page, size);
    }

    public SchoolOperationsWorkspace operations(
            String tenantId, String schoolId,
            boolean canCreate, boolean canUpdate, boolean canArchive, boolean canManageClasses,
            Set<String> selectedFields
    ) {
        return reads.operations(tenantId, schoolId, canCreate, canUpdate, canArchive, canManageClasses,
                selectedFields);
    }
}
