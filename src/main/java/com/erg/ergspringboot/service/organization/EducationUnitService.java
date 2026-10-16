package com.erg.ergspringboot.service.organization;

import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitListResponse;
import com.erg.ergspringboot.organization.internal.OrganizationEducationUnitQuery;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import org.springframework.stereotype.Service;

@Service
@Deprecated(since = "2026-08-08", forRemoval = false)
public class EducationUnitService {

    private final OrganizationEducationUnitQuery query;

    public EducationUnitService(EducationUnitRepository educationUnitRepository) {
        this.query = new OrganizationEducationUnitQuery(educationUnitRepository);
    }

    public EducationUnitListResponse listEducationUnits(UserAccessContext access) {
        return query.list(access);
    }
}
