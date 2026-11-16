package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.notifications.internal.NotificationCommandService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Teaching-schedule fixtures with notifications mocked: the published-batch consumer would write real ones. */
public abstract class TeachingScheduleITBase extends TeachingScheduleFixtures {

    @MockitoBean protected NotificationCommandService notifications;
}
