package com.erg.ergspringboot.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphQlModuleAssemblyContractTest {

    private static final Path MODULES = Path.of("src/main/resources/graphql/modules");

    @Test
    void boundedContextSourcesAssembleToTheCommittedCanonicalSchema() throws Exception {
        List<String> files = List.of(
                "00-shell.graphqls",
                "10-school-quiz.graphqls",
                "20-elearning.graphqls",
                "30-lms.graphqls",
                "35-learning-outcomes.graphqls",
                "40-mutation-social.graphqls",
                "45-school-operations-mutations.graphqls",
                "50-calendar.graphqls");
        String assembled = files.stream()
                .map(file -> read(MODULES.resolve(file)))
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        assertThat(assembled).isEqualTo(read(Path.of("src/main/resources/graphql/schema.graphqls")));
        assertThat(files).allSatisfy(file -> assertThat(Files.size(MODULES.resolve(file))).isPositive());
    }

    @Test
    void moduleOrderIsExplicitAndRootDefinitionsAreNotDuplicated() throws Exception {
        String assembled = read(Path.of("src/main/resources/graphql/schema.graphqls"));
        assertThat(assembled).contains("type Query {", "type Mutation {");
        assertThat(assembled.split("type Query ", -1)).hasSize(2);
        // Only the shell may declare the root Mutation; bounded-context modules extend it.
        assertThat(assembled.split("(?m)^type Mutation \\{", -1)).hasSize(2);
        assertThat(assembled).contains("extend type Mutation {");
        assertThat(assembled).contains("TeachingCalendarWorkspace", "StudentDiscussionFeed");
    }

    private String read(Path path) {
        try {
            return Files.readString(path).replace("\r\n", "\n");
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
