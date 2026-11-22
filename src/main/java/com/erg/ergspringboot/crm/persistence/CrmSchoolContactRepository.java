package com.erg.ergspringboot.crm.persistence;

import com.erg.ergspringboot.crm.persistence.CrmSchoolContact;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrmSchoolContactRepository extends JpaRepository<CrmSchoolContact, String> {

    List<CrmSchoolContact> findByTenantIdAndSchoolIdInOrderByIsPrimaryDescCreatedAtAsc(
            String tenantId, Collection<String> schoolIds);

    /** The one contact each school shows (primary first, then oldest): the same order as above, one row per school. */
    @Query(value = """
            select distinct on (school_id) *
            from crm_school_contacts
            where tenant_id = :tenantId and school_id in (:schoolIds)
            order by school_id, is_primary desc, created_at asc
            """, nativeQuery = true)
    List<CrmSchoolContact> findPrimaryContacts(
            @Param("tenantId") String tenantId, @Param("schoolIds") Collection<String> schoolIds);
}
