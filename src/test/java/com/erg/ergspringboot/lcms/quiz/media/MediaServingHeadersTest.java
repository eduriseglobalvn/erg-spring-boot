package com.erg.ergspringboot.lcms.quiz.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MediaServingHeadersTest {
    @Test
    void svgServedSandboxed() {
        var svg = LocalMediaStorageAdapter.servingHeaders("quiz-media/t/ab/abc.svg");
        assertThat(svg).containsEntry("Content-Security-Policy", "sandbox")
                .containsEntry("X-Content-Type-Options", "nosniff");
        assertThat(LocalMediaStorageAdapter.servingHeaders("quiz-media/t/ab/abc.png"))
                .doesNotContainKey("Content-Security-Policy").containsEntry("X-Content-Type-Options", "nosniff");
    }
}
