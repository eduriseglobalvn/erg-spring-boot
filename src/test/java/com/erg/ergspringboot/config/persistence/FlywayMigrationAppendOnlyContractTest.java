package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * BE-012 append-only migration guard. The upgrade-path integration test proves a previous-release
 * database upgrades in place; this pure-unit contract locks the policy that makes that safe:
 * versioned migrations are unique, gap-free and forward-only. A duplicate/gap version breaks Flyway
 * ordering, and any undo/down file invites a destructive production rollback the runbook forbids.
 */
class FlywayMigrationAppendOnlyContractTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration/postgres");
    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void versionedMigrationsAreUniqueGapFreeAndForwardOnly() throws IOException {
        List<String> names = migrationFileNames();
        assertThat(names).as("expected versioned migrations under %s", MIGRATION_DIR).isNotEmpty();

        TreeSet<Integer> versions = new TreeSet<>();
        List<Integer> all = new ArrayList<>();
        for (String name : names) {
            Matcher matcher = VERSIONED.matcher(name);
            assertThat(matcher.matches())
                    .as("migration '%s' must follow V<n>__<description>.sql", name)
                    .isTrue();
            int version = Integer.parseInt(matcher.group(1));
            all.add(version);
            versions.add(version);
        }

        assertThat(all).as("duplicate migration version detected").doesNotHaveDuplicates();
        assertThat(versions.first()).as("versions must start at 1").isEqualTo(1);
        assertThat(versions.last())
                .as("versions must be gap-free from 1..N")
                .isEqualTo(versions.size());
    }

    @Test
    void noUndoOrDownMigrationsExist() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            List<String> forbidden = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> {
                        String lower = name.toLowerCase(Locale.ROOT);
                        return name.matches("^U\\d+__.+")
                                || lower.contains("undo")
                                || lower.contains("rollback")
                                || lower.contains("down-migration");
                    })
                    .toList();
            assertThat(forbidden)
                    .as("destructive down/undo migrations are forbidden; roll forward instead")
                    .isEmpty();
        }
    }

    private List<String> migrationFileNames() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            return files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .filter(name -> name.startsWith("V"))
                    .toList();
        }
    }
}
