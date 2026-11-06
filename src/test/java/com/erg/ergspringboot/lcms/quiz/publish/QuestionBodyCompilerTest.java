package com.erg.ergspringboot.lcms.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionBodyCompilerTest {

    private final QuestionBodyCompiler compiler = new QuestionBodyCompiler();

    @Test
    @SuppressWarnings("unchecked")
    void keyedMatchingFoldsItsResponsesBackIntoThePairs() {
        Map<String, Object> slide = new LinkedHashMap<>();
        slide.put("runtimeQuestion", Map.of(
                "matching", List.of(Map.of("id", "m1", "prompt", "Cat"), Map.of("id", "m2", "prompt", "Dog")),
                "matchingResponses", List.of(Map.of("id", "r1", "response", "Woof"), Map.of("id", "r2", "response", "Meow")),
                "matchingKey", Map.of("m1", "r2", "m2", "r1")));

        Map<String, Object> merged = compiler.mergeRuntimeQuestion(slide);

        List<Map<String, Object>> pairs = (List<Map<String, Object>>) merged.get("matching");
        assertThat(pairs).extracting(p -> p.get("response")).containsExactly("Meow", "Woof");
        assertThat(merged).doesNotContainKeys("matchingResponses", "matchingKey");
    }

    @Test
    @SuppressWarnings("unchecked")
    void matchingResponsesNeverStartInTheCorrectOrder() {
        for (int run = 0; run < 200; run++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("matching", List.of(
                    Map.of("id", "m1", "prompt", "Cat", "response", "Meow"),
                    Map.of("id", "m2", "prompt", "Dog", "response", "Woof")));
            Map<String, Object> key = new LinkedHashMap<>();
            key.put("pairs", List.of(Map.of("promptId", "m1", "responseId", "m1"), Map.of("promptId", "m2", "responseId", "m2")));

            PlayerBodyShuffler.matching(body, key);

            List<Object> shown = ((List<Map<String, Object>>) body.get("matchingResponses")).stream().map(r -> r.get("id")).toList();
            List<Object> correct = ((List<Map<String, Object>>) key.get("pairs")).stream().map(p -> p.get("responseId")).toList();
            assertThat(shown).isNotEqualTo(correct);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void choiceAndDragItemMediaBecomeLearnerImages() {
        Map<String, Object> slide = new LinkedHashMap<>();
        slide.put("id", "s1");
        slide.put("choices", List.of(
                Map.of("id", "c1", "label", "A", "correct", true,
                        "media", Map.of("type", "image", "src", "https://cdn/a.png", "alt", "Con mèo")),
                new LinkedHashMap<>(Map.of("id", "c2", "label", "B", "correct", false)),
                choiceWithNullMedia()));
        slide.put("dragDropItems", List.of(
                Map.of("id", "i1", "label", "Táo", "correctTargetId", "t1",
                        "media", Map.of("type", "image", "src", "https://cdn/apple.png"))));

        Map<String, Object> body = compiler.compileBody(slide, "single_choice", null, Map.of(), Map.of());

        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        assertThat(choices.get(0)).doesNotContainKey("media")
                .containsEntry("image", Map.of("url", "https://cdn/a.png", "alt", "Con mèo"))
                .doesNotContainKey("correct");
        assertThat(choices.get(1)).doesNotContainKey("image").doesNotContainKey("media");
        assertThat(choices.get(2)).doesNotContainKey("image").doesNotContainKey("media");
        // Drag items only reach drag_drop bodies.
        assertThat(body).doesNotContainKey("dragDropItems");
        Map<String, Object> dragBody = compiler.compileBody(slide, "drag_drop", null, Map.of(), Map.of());
        assertThat(dragBody).doesNotContainKey("choices");
        List<Map<String, Object>> items = (List<Map<String, Object>>) dragBody.get("dragDropItems");
        assertThat(items.get(0)).doesNotContainKey("media")
                .containsEntry("image", Map.of("url", "https://cdn/apple.png"));

        // The authoring slide is never mutated.
        assertThat(((List<Map<String, Object>>) slide.get("choices")).get(0)).containsKey("media");
    }

    @Test
    @SuppressWarnings("unchecked")
    void runtimeDragItemsWinAndKeepSlideMediaOnlyWhenTheyHaveNoImage() {
        Map<String, Object> slide = new LinkedHashMap<>();
        slide.put("runtimeQuestion", Map.of("dragDropItems", List.of(
                Map.of("id", "d1", "label", "Apple", "correctTargetId", "drop-target-fruit"),
                Map.of("id", "d2", "label", "Pear", "correctTargetId", "drop-target-fruit",
                        "image", Map.of("url", "https://cdn/runtime-pear.png")))));
        slide.put("dragDropItems", List.of(
                Map.of("id", "d1", "label", "Apple", "emoji", "A", "target", "Fruit",
                        "media", Map.of("src", "https://cdn/apple.png")),
                Map.of("id", "d2", "label", "Pear", "emoji", "P", "target", "Fruit",
                        "media", Map.of("src", "https://cdn/old-pear.png"))));

        Map<String, Object> merged = compiler.mergeRuntimeQuestion(slide);
        assertThat((List<Map<String, Object>>) merged.get("dragDropItems"))
                .extracting(item -> item.get("correctTargetId")).containsOnly("drop-target-fruit");

        Map<String, Object> body = compiler.compileBody(merged, "drag_drop", null, Map.of(), Map.of());
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("dragDropItems");
        assertThat(items.get(0)).isEqualTo(Map.of("id", "d1", "label", "Apple", "image", Map.of("url", "https://cdn/apple.png")));
        assertThat(items.get(1)).isEqualTo(Map.of("id", "d2", "label", "Pear", "image", Map.of("url", "https://cdn/runtime-pear.png")));
    }

    private static Map<String, Object> choiceWithNullMedia() {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("id", "c3");
        choice.put("label", "C");
        choice.put("correct", false);
        choice.put("media", null);
        return choice;
    }
}
