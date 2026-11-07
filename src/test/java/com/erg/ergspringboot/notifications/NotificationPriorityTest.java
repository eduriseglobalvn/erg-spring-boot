package com.erg.ergspringboot.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NotificationPriorityTest {

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "normal, INFO", "NORMAL, INFO", "high, IMPORTANT", " High , IMPORTANT", "urgent, URGENT",
            "INFO, INFO", "IMPORTANT, IMPORTANT", "URGENT, URGENT", "'', INFO", "NULL, INFO", "low, INFO"
    })
    void legacyAndCampaignValuesNormalise(String stored, NotificationPriority expected) {
        assertThat(NotificationPriority.fromStored(stored)).isEqualTo(expected);
    }
}
