package com.erg.ergspringboot.controller.learningcontent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

class LearningContentCanonicalRouteTest {

    @Test
    void contentCommandsKeepLegacyRoutesAndMountCanonicalV1Aliases() throws Exception {
        assertPost("createGoogleSlidesLecture", "/api/content/lectures/google-slides",
                "/api/v1/content/lectures/google-slides");
        assertPost("createContentItem", "/api/content/items", "/api/v1/content/items");
        assertPatch("updateContentItem", "/api/content/items/{contentItemId}",
                "/api/v1/content/items/{contentItemId}");
        assertDelete("deleteContentItem", "/api/content/items/{contentItemId}",
                "/api/v1/content/items/{contentItemId}");
        assertPost("createContentAsset", "/api/content/assets", "/api/v1/content/assets");
        assertPatch("updateContentAsset", "/api/content/assets/{contentAssetId}",
                "/api/v1/content/assets/{contentAssetId}");
        assertPost("attachContentAsset", "/api/content/items/{contentItemId}/assets",
                "/api/v1/content/items/{contentItemId}/assets");
    }

    @Test
    void curriculumPlacementCommandsKeepLegacyRoutesAndMountCanonicalV1Aliases() throws Exception {
        assertPost("placeContentItem", "/api/curriculum/topics/{topicId}/content-items",
                "/api/v1/curriculum/topics/{topicId}/content-items");
        assertDelete("removePlacement", "/api/curriculum/topics/{topicId}/content-items/{placementId}",
                "/api/v1/curriculum/topics/{topicId}/content-items/{placementId}");
        assertPatch("reorderPlacements", "/api/curriculum/topics/{topicId}/content-items/reorder",
                "/api/v1/curriculum/topics/{topicId}/content-items/reorder");
    }

    @Test
    void securityPolicyCoversCanonicalAndLegacyPrefixes() throws Exception {
        String security = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java"));

        assertThat(security).contains("/api/v1/content/**", "/api/content/**")
                .contains("/api/v1/curriculum/topics/*/content-items/**");
    }

    private void assertPost(String method, String legacy, String canonical) {
        assertThat(find(method).getAnnotation(PostMapping.class).value()).contains(legacy, canonical);
    }

    private void assertPatch(String method, String legacy, String canonical) {
        assertThat(find(method).getAnnotation(PatchMapping.class).value()).contains(legacy, canonical);
    }

    private void assertDelete(String method, String legacy, String canonical) {
        assertThat(find(method).getAnnotation(DeleteMapping.class).value()).contains(legacy, canonical);
    }

    private Method find(String name) {
        return java.util.Arrays.stream(LearningContentController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
