package com.erg.ergspringboot.repository.crm;

import com.erg.ergspringboot.model.crm.CrmSchoolActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmSchoolActivityRepository extends JpaRepository<CrmSchoolActivity, String> {

    List<CrmSchoolActivity> findBySchoolIdOrderByCreatedAtDesc(String schoolId);
}
