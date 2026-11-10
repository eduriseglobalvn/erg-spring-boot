package com.erg.ergspringboot.lms.internal.quiz.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;

class QuizRuntimePackagePlayerSettingsTest {

    @Test
    void aTestQuizPlaysInTestingModeAndKeepsTheEditorSettings() {
        Map<String, Object> settings = QuizRuntimePackageService.playerSettings(
                Map.of("enableTimeLimit", true, "timeLimit", "20:00"), "test");
        assertEquals("testing", settings.get("mode"));
        assertEquals("20:00", settings.get("timeLimit"));
    }

    @Test
    void aPracticeQuizPlaysInTrainingMode() {
        assertEquals("training", QuizRuntimePackageService.playerSettings(null, "train").get("mode"));
    }

    @Test
    void anExplicitModeWinsAndAnUnknownKindAddsNone() {
        assertEquals("training", QuizRuntimePackageService.playerSettings(Map.of("mode", "training"), "test").get("mode"));
        assertFalse(QuizRuntimePackageService.playerSettings(Map.of(), null).containsKey("mode"));
    }
}
