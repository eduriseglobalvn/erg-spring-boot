package com.erg.ergspringboot.crm.persistence;

import com.erg.ergspringboot.crm.persistence.CrmSchoolContact;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmSchoolContactRepository extends JpaRepository<CrmSchoolContact, String> {

    List<CrmSchoolContact> findByTenantIdAndSchoolIdInOrderByIsPrimaryDescCreatedAtAsc(
            String tenantId, Collection<String> schoolIds);
}
