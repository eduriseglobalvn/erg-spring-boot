package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientRepository;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

class NotificationUnreadCacheTest {

    private final NotificationRecipientRepository recipients = mock(NotificationRecipientRepository.class);
    private final LmsReadCacheInvalidator caches = mock(LmsReadCacheInvalidator.class);
    private final NotificationService service = new NotificationService(recipients,
            mock(NotificationPreferenceRepository.class), mock(NotificationMessageRepository.class),
            mock(NotificationCampaignRepository.class), mock(UserDirectoryPort.class), caches);

    @Test
    void unreadCountIsCachedPerUserAndPortalWithAStampedeGuard() throws Exception {
        Cacheable cacheable = NotificationService.class.getMethod("unreadCount", String.class, String.class, String.class)
                .getAnnotation(Cacheable.class);

        assertThat(cacheable.cacheNames()).containsExactly("notificationUnread");
        assertThat(cacheable.sync()).isTrue();
        assertThat(cacheable.key()).isEqualTo("{#tenantId, #userId, #portal}");
    }

    @Test
    void markAllReadAndMarkReadAndArchiveEvictOnlyThatUsersCount() {
        NotificationRecipientEntity recipient = new NotificationRecipientEntity();
        recipient.setStatus("unread");
        org.mockito.Mockito.when(recipients.findOwn("t1", "u1", "lms", "n1")).thenReturn(List.of(recipient));

        service.markAllRead("t1", "u1", "lms");
        service.markRead("t1", "u1", "lms", "n1");
        service.archive("t1", "u1", "lms", "n1");

        verify(caches, org.mockito.Mockito.times(3)).evictScopesAfterCommit("t1", Map.of("notificationUnread", List.of("u1")));
    }
}
