package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * P3-5: production classes stay at or under ~600 lines. Reviewed exceptions keep a ceiling that may
 * only shrink: the two LMS repositories are query declarations, and QuestionKindStrategy holds one
 * small strategy per question kind (P3-3) on purpose.
 *
 * <p>Line count alone let whole methods hide on one 800-character line, so a second ratchet caps line
 * length at {@value #MAX_LINE_LENGTH}. Files that already break it are listed in
 * {@code long-line-baseline.txt}; the list may only shrink.
 */
class ClassSizeRatchetTest {
    private static final int LIMIT = 600;
    private static final int MAX_LINE_LENGTH = 160;
    private static final Path ROOT = Path.of("src/main/java");
    private static final Path LONG_LINE_BASELINE = Path.of("src/test/resources/architecture/long-line-baseline.txt");
    private static final Map<String, Integer> REVIEWED_CEILINGS = Map.of(
            "com/erg/ergspringboot/lms/persistence/AssignmentRepository.java", 522,
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

    @Test
    void noNewProductionLineIsLongerThanTheLimitAndTheBaselineOnlyShrinks() throws IOException {
        Set<String> baseline = new TreeSet<>();
        for (String line : Files.readAllLines(LONG_LINE_BASELINE)) {
            String entry = line.strip();
            if (!entry.isEmpty() && !entry.startsWith("#")) baseline.add(entry);
        }

        Set<String> withLongLines = new TreeSet<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                try (Stream<String> content = Files.lines(file)) {
                    if (content.anyMatch(line -> line.length() > MAX_LINE_LENGTH)) {
                        withLongLines.add(ROOT.relativize(file).toString().replace('\\', '/'));
                    }
                }
            }
        }

        Set<String> newOffenders = new TreeSet<>(withLongLines);
        newOffenders.removeAll(baseline);
        assertThat(newOffenders)
                .as("these files have a line over %d characters; wrap it (do not add the file to the baseline)",
                        MAX_LINE_LENGTH)
                .isEmpty();

        Set<String> fixed = new TreeSet<>(baseline);
        fixed.removeAll(withLongLines);
        assertThat(fixed)
                .as("these files no longer have a line over %d characters (or no longer exist): "
                        + "remove them from long-line-baseline.txt so the baseline only shrinks", MAX_LINE_LENGTH)
                .isEmpty();
    }
}
