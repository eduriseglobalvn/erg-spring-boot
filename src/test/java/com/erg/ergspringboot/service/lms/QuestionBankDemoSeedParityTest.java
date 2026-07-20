package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class QuestionBankDemoSeedParityTest {

    private static final Path FE_QUIZ_ROOT = Path.of("..", "erg-system", "src", "features", "lcms", "quiz")
            .toAbsolutePath()
            .normalize();

    @Test
    void questionBankSeedMirrorsFrontendMockIds() throws IOException {
        Path feMock = FE_QUIZ_ROOT.resolve(Path.of("question-bank", "api", "mock-question-bank.ts"));
        assumeTrue(Files.exists(feMock), "erg-system workspace is required for FE/BE seed parity check");

        String frontend = Files.readString(feMock);
        String backend = readBackendSource("QuestionBankDemoBootstrapSeedService.java");

        List<String> frontendQuestionIds = frontendQuestionIds(frontend);
        List<String> backendQuestionIds = matches(backend, "upsertQuestion\\(\"(qb-[^\"]+)\"");
        List<String> frontendQuizIds = matches(frontend, "id:\\s*\"(quiz-[^\"]+)\"");
        List<String> backendQuizIds = matches(backend, "upsertQuizBankItem\\(\"(quiz-[^\"]+)\"");
        List<String> backendSubjectIds = matches(backend, "upsertSubject\\(\"([^\"]+)\"");

        assertThat(backendSubjectIds).contains("ic3-gs6", "python", "mos");
        assertThat(backendQuestionIds)
                .containsAll(frontendQuestionIds)
                .contains("qb-python-001", "qb-mos-001")
                .contains("qb-sample-q01-single-choice", "qb-sample-q15-essay");
        assertThat(backendQuizIds)
                .containsAll(frontendQuizIds)
                .contains("quiz-python-foundation-train", "quiz-mos-office-readiness")
                .contains("quiz-sample-editor-question-types");
    }

    @Test
    void questionBankSeedMirrorsFrontendMockQuestionAndQuizMetadata() throws IOException {
        Path feMock = FE_QUIZ_ROOT.resolve(Path.of("question-bank", "api", "mock-question-bank.ts"));
        assumeTrue(Files.exists(feMock), "erg-system workspace is required for FE/BE seed parity check");

        String frontend = Files.readString(feMock);
        String backend = readBackendSource("QuestionBankDemoBootstrapSeedService.java");

        assertThat(backendQuestionSeeds(backend))
                .containsAll(frontendQuestionSeeds(frontend))
                .contains(new QuestionSeed("qb-sample-q01-single-choice", "single_choice", "core", "ready"));
        assertThat(backendQuizSeeds(backend))
                .containsAll(frontendQuizSeeds(frontend))
                .anySatisfy(seed -> {
                    assertThat(seed.id()).isEqualTo("quiz-sample-editor-question-types");
                    assertThat(seed.questionIds()).contains("qb-sample-q01-single-choice", "qb-sample-q15-essay");
                });
    }

    @Test
    void runtimeDemoSeedCoversEveryFrontendQuestionTypeDemo() throws IOException {
        Path feQuestionTypes = FE_QUIZ_ROOT.resolve(Path.of("question-type-demo", "data", "question-type-demos.ts"));
        assumeTrue(Files.exists(feQuestionTypes), "erg-system workspace is required for FE/BE seed parity check");

        String frontend = Files.readString(feQuestionTypes);
        String backend = readBackendSource("QuizDemoBootstrapSeedService.java");

        List<String> frontendTypes = matches(frontend, "kind:\\s*\"([^\"]+)\"");
        List<String> backendTypes = matches(backend, "question\\(\"q\\d+-[^\"]+\",\\s*\"([^\"]+)\"");

        assertThat(backendTypes)
                .containsExactlyElementsOf(frontendTypes);
    }

    @Test
    void runtimeDemoReferencesOnlyQuestionsCreatedByQuestionBankSeed() throws IOException {
        String questionBankSeed = readBackendSource("QuestionBankDemoBootstrapSeedService.java");
        String runtimeSeed = readBackendSource("QuizDemoBootstrapSeedService.java");

        List<String> seededQuestionIds = matches(questionBankSeed, "upsertQuestion\\(\"(qb-[^\"]+)\"");
        List<String> runtimeQuestionIds = matches(runtimeSeed, "case \"q\\d+-[^\"]+\" -> \"(qb-[^\"]+)\"");

        assertThat(runtimeQuestionIds).hasSize(14);
        assertThat(seededQuestionIds).containsAll(runtimeQuestionIds);
        assertThat(runtimeQuestionIds).doesNotContain("qb-sample-q08-matching-image");
    }

    private static List<String> frontendQuestionIds(String frontend) {
        String blueprint = between(frontend, "const ic3QuestionBlueprints", "const ic3Questions");
        int ic3QuestionCount = countMatches(blueprint, "\\bstem:\\s*\"");

        List<String> ids = new ArrayList<>();
        for (int index = 1; index <= ic3QuestionCount; index += 1) {
            ids.add("qb-ic3-" + String.format("%03d", index));
        }
        ids.addAll(matches(frontend, "createLegacyQuestion\\(\"(qb-[^\"]+)\""));
        return sortedUnique(ids);
    }

    private static List<QuestionSeed> frontendQuestionSeeds(String frontend) {
        String blueprint = between(frontend, "const ic3QuestionBlueprints", "const ic3Questions");
        var matcher = Pattern.compile(
                "type:\\s*\"([^\"]+)\"[\\s\\S]*?difficulty:\\s*\"([^\"]+)\"[\\s\\S]*?status:\\s*\"([^\"]+)\""
        ).matcher(blueprint);
        List<QuestionSeed> seeds = new ArrayList<>();
        int index = 1;
        while (matcher.find()) {
            seeds.add(new QuestionSeed(
                    "qb-ic3-" + String.format("%03d", index),
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3)
            ));
            index += 1;
        }

        var legacyMatcher = Pattern.compile(
                "createLegacyQuestion\\(\"(qb-[^\"]+)\",\\s*\"[^\"]+\",\\s*\"[^\"]+\",\\s*\"[^\"]+\",\\s*\"[^\"]+\",\\s*\"([^\"]+)\""
        ).matcher(frontend);
        while (legacyMatcher.find()) {
            seeds.add(new QuestionSeed(legacyMatcher.group(1), "multiple-choice", legacyMatcher.group(2), "ready"));
        }
        return sortedQuestionSeeds(seeds);
    }

    private static List<QuestionSeed> backendQuestionSeeds(String backend) {
        var matcher = Pattern.compile(
                "upsertQuestion\\(\"(qb-[^\"]+)\",\\s*\"[^\"]+\",\\s*\"[^\"]+\",\\s*(?:\"[^\"]+\"|topicId),\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*\"([^\"]+)\""
        ).matcher(backend);
        List<QuestionSeed> seeds = new ArrayList<>();
        while (matcher.find()) {
            seeds.add(new QuestionSeed(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)));
        }
        return sortedQuestionSeeds(seeds);
    }

    private static List<QuizSeed> frontendQuizSeeds(String frontend) {
        String quizBankItems = between(frontend, "export const quizBankItems", "export function");
        var matcher = Pattern.compile("createQuizBankItem\\(\\{([\\s\\S]*?)\\}\\)", Pattern.MULTILINE).matcher(quizBankItems);
        List<QuizSeed> seeds = new ArrayList<>();
        while (matcher.find()) {
            String block = matcher.group(1);
            seeds.add(new QuizSeed(
                    firstMatch(block, "id:\\s*\"([^\"]+)\""),
                    firstMatch(block, "kind:\\s*\"([^\"]+)\""),
                    firstMatch(block, "status:\\s*\"([^\"]+)\""),
                    matches(block, "\"(qb-[^\"]+)\"")
            ));
        }
        return sortedQuizSeeds(seeds);
    }

    private static List<QuizSeed> backendQuizSeeds(String backend) {
        var matcher = Pattern.compile(
                "upsertQuizBankItem\\(\"(quiz-[^\"]+)\",\\s*\"[^\"]+\",\\s*\"([^\"]+)\",\\s*\"[^\"]+\",\\s*\"[^\"]+\",\\s*\"([^\"]+)\",\\s*List\\.of\\(([^)]*)\\)"
        ).matcher(backend);
        List<QuizSeed> seeds = new ArrayList<>();
        while (matcher.find()) {
            seeds.add(new QuizSeed(matcher.group(1), matcher.group(2), matcher.group(3), matches(matcher.group(4), "\"(qb-[^\"]+)\"")));
        }
        return sortedQuizSeeds(seeds);
    }

    private static String readBackendSource(String fileName) throws IOException {
        return Files.readString(Path.of(
                "src",
                "main",
                "java",
                "com",
                "erg",
                "ergspringboot",
                "service",
                "lms",
                fileName
        ));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker);
        assertThat(start).as("start marker %s", startMarker).isGreaterThanOrEqualTo(0);
        assertThat(end).as("end marker %s", endMarker).isGreaterThan(start);
        return source.substring(start, end);
    }

    private static int countMatches(String source, String regex) {
        var matcher = Pattern.compile(regex).matcher(source);
        int count = 0;
        while (matcher.find()) {
            count += 1;
        }
        return count;
    }

    private static List<String> matches(String source, String regex) {
        var matcher = Pattern.compile(regex).matcher(source);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return sortedUnique(values);
    }

    private static String firstMatch(String source, String regex) {
        var matcher = Pattern.compile(regex).matcher(source);
        assertThat(matcher.find()).as("first match for %s", regex).isTrue();
        return matcher.group(1);
    }

    private static List<String> sortedUnique(List<String> values) {
        Set<String> unique = new LinkedHashSet<>(values);
        return unique.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static List<QuestionSeed> sortedQuestionSeeds(List<QuestionSeed> values) {
        return values.stream()
                .sorted(Comparator.comparing(QuestionSeed::id))
                .toList();
    }

    private static List<QuizSeed> sortedQuizSeeds(List<QuizSeed> values) {
        return values.stream()
                .sorted(Comparator.comparing(QuizSeed::id))
                .toList();
    }

    private record QuestionSeed(String id, String type, String difficulty, String status) {
    }

    private record QuizSeed(String id, String kind, String status, List<String> questionIds) {
    }
}
