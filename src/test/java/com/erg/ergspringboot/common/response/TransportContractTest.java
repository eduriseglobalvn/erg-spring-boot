package com.erg.ergspringboot.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransportContractTest {

    @Test
    void errorEnvelopeUsesTypedTransportPayloadInsteadOfObject() {
        RecordComponent errors = Arrays.stream(ApiResponse.class.getRecordComponents())
                .filter(component -> component.getName().equals("errors"))
                .findFirst()
                .orElseThrow();

        assertThat(errors.getType()).isNotEqualTo(Object.class);
        assertThat(errors.getType().getSimpleName()).isEqualTo("TransportError");
    }

    @Test
    void reusableErrorDetailsAreFieldErrorsRatherThanWildcardValues() {
        RecordComponent details = Arrays.stream(ErrDetail.class.getRecordComponents())
                .filter(component -> component.getName().equals("details"))
                .findFirst()
                .orElseThrow();

        assertThat(details.getGenericType().getTypeName())
                .isEqualTo("java.util.List<com.erg.ergspringboot.common.response.FieldErrorDetail>");
    }

    @Test
    void pageResponseRemainsTheOffsetPaginationContract() {
        assertThat(Arrays.stream(PageResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("items", "page", "size", "totalItems", "totalPages", "hasNext", "hasPrevious");
    }

    @Test
    void productionCodeDoesNotAdoptLegacyPaginatedData() throws Exception {
        List<Path> offenders = sourceFiles().stream()
                .filter(path -> !path.endsWith("common/response/PaginatedData.java"))
                .filter(path -> read(path).contains("PaginatedData"))
                .toList();

        assertThat(offenders).isEmpty();
    }

    @Test
    void controllersDoNotReimplementRequestCorrelationLookup() throws Exception {
        List<String> forbiddenFragments = List.of(
                "getAttribute(\"requestId\")",
                "getHeader(\"X-Request-ID\")",
                "getHeader(\"X-Request-Id\")"
        );
        List<Path> offenders = new ArrayList<>();
        for (Path path : sourceFiles()) {
            if (!path.toString().replace('\\', '/').contains("/controller/")) {
                continue;
            }
            String source = read(path);
            if (forbiddenFragments.stream().anyMatch(source::contains)) {
                offenders.add(path);
            }
        }

        assertThat(offenders).isEmpty();
    }

    private List<Path> sourceFiles() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
