package com.erg.ergspringboot.repository.crm;

import com.erg.ergspringboot.model.crm.CrmSchoolDeal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmSchoolDealRepository extends JpaRepository<CrmSchoolDeal, String> {

    List<CrmSchoolDeal> findByOwnerUserIdAndStageIgnoreCaseOrderBySignedAtDesc(String ownerUserId, String stage);

    List<CrmSchoolDeal> findByOwnerUserIdAndCreatedAtBetween(String ownerUserId, OffsetDateTime from, OffsetDateTime to);
}
