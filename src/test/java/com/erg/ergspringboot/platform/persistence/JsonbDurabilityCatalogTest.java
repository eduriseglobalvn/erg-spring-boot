package com.erg.ergspringboot.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonbDurabilityCatalogTest {

    @Test
    void inventoryIsExplicitAndHasNoUnclassifiedPayloads() {
        assertThat(JsonbDurabilityCatalog.all()).hasSizeGreaterThanOrEqualTo(45);
        assertThat(JsonbDurabilityCatalog.all()).allSatisfy(field -> {
            assertThat(field.owner()).isNotBlank();
            assertThat(field.entity()).isNotBlank();
            assertThat(field.property()).isNotBlank();
            assertThat(field.reason()).isNotBlank();
            assertThat(field.key()).isEqualTo(field.entity() + "." + field.property());
        });
        Set<JsonbDurabilityCatalog.Classification> classifications = EnumSet.copyOf(
                JsonbDurabilityCatalog.all().stream().map(JsonbDurabilityCatalog.Field::classification).toList());
        assertThat(classifications).containsExactlyInAnyOrder(
                JsonbDurabilityCatalog.Classification.DURABLE_VERSIONED,
                JsonbDurabilityCatalog.Classification.DURABLE_OWNER_READER,
                JsonbDurabilityCatalog.Classification.TRANSIENT_EVENT,
                JsonbDurabilityCatalog.Classification.EXEMPT_AUDIT_OR_PII);
    }

    @Test
    void quizPublishedDocumentsAreOwnedAndVersioned() {
        assertThat(JsonbDurabilityCatalog.all()).filteredOn(field -> "quiz".equals(field.owner())
                        && ("QuizPackage".equals(field.entity()) || "QuizVersion".equals(field.entity())
                        || "QuizDraft".equals(field.entity())))
                .allSatisfy(field -> assertThat(field.classification())
                        .isIn(JsonbDurabilityCatalog.Classification.DURABLE_VERSIONED,
                                JsonbDurabilityCatalog.Classification.DURABLE_OWNER_READER));
        assertThat(JsonbDurabilityCatalog.all()).filteredOn(field ->
                "QuizPackage".equals(field.entity()) || "QuizVersion".equals(field.entity())
                        || "QuizDraft".equals(field.entity()))
                .allSatisfy(field -> assertThat(field.classification())
                        .isEqualTo(JsonbDurabilityCatalog.Classification.DURABLE_VERSIONED));
    }
}
