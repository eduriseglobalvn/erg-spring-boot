package com.erg.ergspringboot.service.organization;

import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitListResponse;
import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitResponse;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EducationUnitService {

    private final EducationUnitRepository educationUnitRepository;

    public EducationUnitService(EducationUnitRepository educationUnitRepository) {
        this.educationUnitRepository = educationUnitRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "organizationEducationUnits", key = "{#access.tenantId(), #access.managedEducationUnitIds()}")
    public EducationUnitListResponse listEducationUnits(UserAccessContext access) {
        var managedIds = access.managedEducationUnitIds();
        var limit = PageRequest.of(0, 500);
        var units = managedIds.contains("*")
                ? educationUnitRepository.findByTenantIdOrderByNameAsc(access.tenantId(), limit)
                : managedIds.isEmpty()
                        ? java.util.List.<EducationUnit>of()
                        : educationUnitRepository.findByTenantIdAndIdInOrderByNameAsc(
                                access.tenantId(), managedIds, limit);
        return new EducationUnitListResponse(units.stream()
                .map(this::toResponse)
                .toList());
    }

    private EducationUnitResponse toResponse(EducationUnit unit) {
        return new EducationUnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getSlug(),
                unit.getType(),
                unit.getParentId(),
                unit.getLogoUrl(),
                unit.getDescription(),
                unit.getStatus()
        );
    }
}
