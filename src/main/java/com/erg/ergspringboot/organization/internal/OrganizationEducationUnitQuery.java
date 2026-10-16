package com.erg.ergspringboot.organization.internal;

import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitListResponse;
import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitResponse;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organization-owned read use case; the public facade returns DTOs only. */
@Service
public class OrganizationEducationUnitQuery {

    private final EducationUnitRepository educationUnitRepository;

    public OrganizationEducationUnitQuery(EducationUnitRepository educationUnitRepository) {
        this.educationUnitRepository = educationUnitRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "organizationEducationUnits", key = "{#access.tenantId(), #access.scopeFingerprint()}")
    public EducationUnitListResponse list(UserAccessContext access) {
        var managedIds = access.managedEducationUnitIds();
        var limit = PageRequest.of(0, 500);
        var units = managedIds.contains("*")
                ? educationUnitRepository.findByTenantIdOrderByNameAsc(access.tenantId(), limit)
                : managedIds.isEmpty()
                        ? java.util.List.<EducationUnit>of()
                        : educationUnitRepository.findByTenantIdAndIdInOrderByNameAsc(
                                access.tenantId(), managedIds, limit);
        return new EducationUnitListResponse(units.stream().map(this::toResponse).toList());
    }

    private EducationUnitResponse toResponse(EducationUnit unit) {
        return new EducationUnitResponse(
                unit.getId(), unit.getName(), unit.getSlug(), unit.getType(), unit.getParentId(),
                unit.getLogoUrl(), unit.getDescription(), unit.getStatus());
    }
}
