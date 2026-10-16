package com.erg.ergspringboot.service.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortableQuizAssetBundlerTest {

    @Test
    void extractsAndDeduplicatesInlineMediaIntoSignedAssetManifest() {
        var result = PortableQuizAssetBundler.bundle(Map.of(
                "sections", List.of(Map.of("questions", List.of(Map.of(
                        "contentImage", Map.of("url", "data:image/png;base64,aW1hZ2U="),
                        "matching", List.of(Map.of(
                                "responseImage", Map.of("url", "data:image/png;base64,aW1hZ2U=")))))))));

        String serializedQuiz = result.runtimeQuiz().toString();
        assertThat(serializedQuiz).doesNotContain("data:image/png;base64").contains("asset://sha256-");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assets = (List<Map<String, Object>>) result.assetsManifest().get("assets");
        assertThat(assets).hasSize(1);
        assertThat(assets.getFirst())
                .containsEntry("mimeType", "image/png")
                .containsEntry("contentBase64", "aW1hZ2U=")
                .containsEntry("sizeBytes", 5);
        assertThat(String.valueOf(assets.getFirst().get("sha256"))).startsWith("sha256:");
    }
}
