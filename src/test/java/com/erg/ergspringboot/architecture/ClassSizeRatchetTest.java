package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * P3-5: production classes stay at or under ~400 lines. Reviewed exceptions keep a ceiling that may
 * only shrink: the two LMS repositories are query declarations, and QuestionKindStrategy holds one
 * small strategy per question kind (P3-3) on purpose.
 */
class ClassSizeRatchetTest {
    private static final int LIMIT = 400;
    private static final Path ROOT = Path.of("src/main/java");
    private static final Map<String, Integer> REVIEWED_CEILINGS = Map.of(
            "com/erg/ergspringboot/lms/persistence/AssignmentRepository.java", 573,
            "com/erg/ergspringboot/lms/persistence/AttemptRepository.java", 479,
            "com/erg/ergspringboot/lcms/quiz/publish/QuestionKindStrategy.java", 394);

    @Test
    void noProductionClassGrowsPastTheLimit() throws IOException {
        Map<String, Long> oversized = new TreeMap<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                long lines;
                try (Stream<String> content = Files.lines(file)) {
                    lines = content.count();
                }
                String key = ROOT.relativize(file).toString().replace('\\', '/');
                if (lines > REVIEWED_CEILINGS.getOrDefault(key, LIMIT)) {
                    oversized.put(key, lines);
                }
            }
        }
        assertThat(oversized).as("split these by responsibility instead of raising a ceiling").isEmpty();
    }
}
