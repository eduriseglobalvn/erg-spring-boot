package com.erg.ergspringboot.repository.crm;

import com.erg.ergspringboot.model.crm.CrmSchoolContact;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmSchoolContactRepository extends JpaRepository<CrmSchoolContact, String> {

    List<CrmSchoolContact> findBySchoolIdInOrderByIsPrimaryDescCreatedAtAsc(Collection<String> schoolIds);
}
