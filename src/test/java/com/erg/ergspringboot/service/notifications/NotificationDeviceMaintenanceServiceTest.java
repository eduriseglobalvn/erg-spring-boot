package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeviceMaintenanceServiceTest {

    @Mock
    private NotificationDeviceRegistrationRepository deviceRepository;

    @Mock
    private NotificationMetrics metrics;

    @Test
    void disablesDevicesOlderThanConfiguredStaleAge() {
        when(deviceRepository.disableStaleDevices(any(), any())).thenReturn(3);

        NotificationPushProperties properties = new NotificationPushProperties();
        NotificationDeviceMaintenanceService service = new NotificationDeviceMaintenanceService(
                deviceRepository,
                properties,
                metrics
        );

        int disabled = service.disableStaleDevices();

        ArgumentCaptor<OffsetDateTime> staleBefore = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(deviceRepository).disableStaleDevices(staleBefore.capture(), any());
        verify(metrics).staleDisabled(3);
        assertThat(disabled).isEqualTo(3);
        assertThat(staleBefore.getValue()).isBefore(OffsetDateTime.now().minusDays(29));
        assertThat(staleBefore.getValue()).isAfter(OffsetDateTime.now().minusDays(31));
    }
}
