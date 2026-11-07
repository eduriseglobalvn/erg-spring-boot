package com.erg.ergspringboot.lcms.quiz.publish;

import static com.erg.ergspringboot.lcms.quiz.publish.AnswerGrading.mapList;
import static com.erg.ergspringboot.lcms.quiz.publish.AnswerGrading.stringList;

import com.erg.ergspringboot.utils.IdGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hides the authoring order from the player-facing body of sequence, matching and drag_words questions. Runs after the
 * answer key is extracted from the authoring slide, so the key keeps the real order/pairs while the body only
 * carries a shuffled list and opaque response ids. The shuffle is random (not seeded by the question id): a
 * seed a student can recompute would let them invert the permutation. Key and body are stored together at
 * publish time, so nothing needs to be reproducible.
 */
final class PlayerBodyShuffler {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PlayerBodyShuffler() {
    }

    /** Runs the sequence/matching/word-bank step for that kind after the answer key was extracted; others are untouched. */
    static void apply(String runtimeKind, Map<String, Object> body, Map<String, Object> key) {
        QuestionKindStrategy strategy = QuestionKindStrategy.forRuntimeKind(runtimeKind);
        if (strategy == QuestionKindStrategy.SEQUENCE) {
            sequence(body, key);
        } else if (strategy == QuestionKindStrategy.MATCHING) {
            matching(body, key);
        } else if (strategy == QuestionKindStrategy.BLANKS) {
            wordBank(body, key);
        }
    }

    /** Shuffles {@code sequenceItems} so it never equals the key's correct order (when there are 2+ items). */
    static void sequence(Map<String, Object> body, Map<String, Object> key) {
        shuffleAwayFrom(body, "sequenceItems", stringList(key.get("order")));
    }

    /** drag_words: the word bank is authored in slot order, so it never starts with the slots' correct words in order. */
    static void wordBank(Map<String, Object> body, Map<String, Object> key) {
        List<String> slotWords = new ArrayList<>();
        for (Map<String, Object> blank : mapList(key.get("blanks"))) {
            List<String> answers = stringList(blank.get("answers"));
            if (!answers.isEmpty()) {
                slotWords.add(answers.getFirst());
            }
        }
        shuffleAwayFrom(body, "wordBank", slotWords);
    }

    /** Shuffles a list field so its ids never start with {@code correct} in order (when there are 2+ items). */
    private static void shuffleAwayFrom(Map<String, Object> body, String field, List<String> correct) {
        if (!(body.get(field) instanceof List<?> items) || items.size() < 2) {
            return;
        }
        List<Object> shuffled = new ArrayList<>(items);
        for (int attempt = 0; attempt < 8 && startsWith(shuffled, correct); attempt++) {
            Collections.shuffle(shuffled, RANDOM);
        }
        if (startsWith(shuffled, correct)) {
            Collections.rotate(shuffled, 1); // still matching after retries is only possible for duplicate ids
        }
        body.put(field, shuffled);
    }

    private static boolean startsWith(List<Object> items, List<String> order) {
        List<String> ids = AnswerKeySources.ids(items);
        return ids != null && !order.isEmpty() && ids.size() >= order.size() && ids.subList(0, order.size()).equals(order);
    }

    /**
     * Splits {@code matching: [{id, prompt, response, ...}]} into {@code matching} (prompts only) and a shuffled
     * {@code matchingResponses} with opaque ids, and rewrites the key's pairs to those ids. Only applies when the
     * key was derived from the pair ids (promptId == responseId); an explicitly authored key is left alone.
     */
    static void matching(Map<String, Object> body, Map<String, Object> key) {
        List<Map<String, Object>> pairs = mapList(body.get("matching"));
        List<Map<String, Object>> keyPairs = mapList(key.get("pairs"));
        boolean identityKey = !keyPairs.isEmpty() && keyPairs.stream()
                .allMatch(p -> p.get("promptId") != null && Objects.equals(p.get("promptId"), p.get("responseId")));
        if (pairs.isEmpty() || !identityKey) {
            return;
        }
        List<Map<String, Object>> prompts = new ArrayList<>();
        List<Map<String, Object>> responses = new ArrayList<>();
        List<Map<String, Object>> newKey = new ArrayList<>();
        for (Map<String, Object> pair : pairs) {
            Object promptId = pair.get("id");
            String responseId = "r-" + IdGenerator.newId();
            Map<String, Object> prompt = new LinkedHashMap<>();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", responseId);
            for (Map.Entry<String, Object> field : pair.entrySet()) {
                if (field.getKey().startsWith("response")) {
                    response.put(field.getKey(), field.getValue());
                } else {
                    prompt.put(field.getKey(), field.getValue());
                }
            }
            prompts.add(prompt);
            responses.add(response);
            if (promptId != null) {
                newKey.add(Map.of("promptId", promptId, "responseId", responseId));
            }
        }
        List<Object> correctOrder = newKey.stream().map(pair -> pair.get("responseId")).toList();
        for (int attempt = 0; attempt < 8 && responseIds(responses).equals(correctOrder); attempt++) {
            Collections.shuffle(responses, RANDOM);
        }
        if (responseIds(responses).equals(correctOrder)) {
            Collections.rotate(responses, 1); // never hand the learner a solved board
        }
        body.put("matching", prompts);
        body.put("matchingResponses", responses);
        key.put("pairs", newKey);
    }

    private static List<Object> responseIds(List<Map<String, Object>> responses) {
        return responses.stream().map(response -> response.get("id")).toList();
    }
}
