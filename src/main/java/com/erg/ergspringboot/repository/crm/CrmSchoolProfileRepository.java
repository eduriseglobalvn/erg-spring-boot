package com.erg.ergspringboot.repository.crm;

import com.erg.ergspringboot.model.crm.CrmSchoolProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmSchoolProfileRepository extends JpaRepository<CrmSchoolProfile, String> {

    Optional<CrmSchoolProfile> findFirstBySchoolId(String schoolId);

    List<CrmSchoolProfile> findBySchoolIdIn(Collection<String> schoolIds);
}
