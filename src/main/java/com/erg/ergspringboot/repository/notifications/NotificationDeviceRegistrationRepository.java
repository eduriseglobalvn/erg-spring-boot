package com.erg.ergspringboot.repository.notifications;

import com.erg.ergspringboot.model.notifications.NotificationDeviceRegistrationEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationDeviceRegistrationRepository extends JpaRepository<NotificationDeviceRegistrationEntity, String> {

    Optional<NotificationDeviceRegistrationEntity> findByTenantIdAndFirebaseInstallationId(String tenantId, String firebaseInstallationId);

    Optional<NotificationDeviceRegistrationEntity> findByTenantIdAndRegistrationTokenHash(String tenantId, String registrationTokenHash);

    Optional<NotificationDeviceRegistrationEntity> findByTenantIdAndUserIdAndId(String tenantId, String userId, String id);

    Optional<NotificationDeviceRegistrationEntity> findByTenantIdAndUserIdAndPortalAndId(
            String tenantId,
            String userId,
            String portal,
            String id
    );

    List<NotificationDeviceRegistrationEntity> findByTenantIdAndUserIdAndPortalAndEnabledTrue(
            String tenantId,
            String userId,
            String portal
    );

    List<NotificationDeviceRegistrationEntity> findByTenantIdAndUserIdInAndPortalAndEnabledTrue(
            String tenantId,
            Collection<String> userIds,
            String portal
    );

    long countByEnabledTrue();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDeviceRegistrationEntity device
            set device.enabled = false,
                device.revokedAt = :revokedAt
            where device.tenantId = :tenantId
              and device.userId = :userId
              and device.id = :id
            """)
    int revokeOwnDevice(String tenantId, String userId, String id, OffsetDateTime revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDeviceRegistrationEntity device
            set device.enabled = false,
                device.revokedAt = :revokedAt
            where device.enabled = true
              and (
                (device.lastSeenAt is not null and device.lastSeenAt <= :staleBefore)
                or (device.lastSeenAt is null and device.tokenUpdatedAt is not null and device.tokenUpdatedAt <= :staleBefore)
                or (device.lastSeenAt is null and device.tokenUpdatedAt is null and device.updatedAt <= :staleBefore)
              )
            """)
    int disableStaleDevices(OffsetDateTime staleBefore, OffsetDateTime revokedAt);
}
