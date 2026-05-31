package com.erg.ergspringboot.repository.crm;

import com.erg.ergspringboot.model.crm.CrmReferral;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmReferralRepository extends JpaRepository<CrmReferral, String> {

    List<CrmReferral> findBySchoolIdOrderByCreatedAtAsc(String schoolId);
}
