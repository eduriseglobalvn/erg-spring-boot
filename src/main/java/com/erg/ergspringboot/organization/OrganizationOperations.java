package com.erg.ergspringboot.organization;

import com.erg.ergspringboot.common.response.organization.EducationUnitResponses.EducationUnitListResponse;
import com.erg.ergspringboot.organization.internal.OrganizationEducationUnitQuery;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import org.springframework.stereotype.Service;

/** Public organization boundary; persistence and entity mapping stay behind the module. */
@Service
public class OrganizationOperations {

    private final OrganizationEducationUnitQuery educationUnits;

    public OrganizationOperations(OrganizationEducationUnitQuery educationUnits) {
        this.educationUnits = educationUnits;
    }

    public EducationUnitListResponse listEducationUnits(UserAccessContext access) {
        return educationUnits.list(access);
    }
}
