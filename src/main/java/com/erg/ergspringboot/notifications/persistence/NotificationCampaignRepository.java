package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.CampaignStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaignEntity, String> {

    List<NotificationCampaignEntity> findByTenantIdAndStatusAndScheduledAtLessThanEqual(
            String tenantId, CampaignStatus status, OffsetDateTime now);
}
