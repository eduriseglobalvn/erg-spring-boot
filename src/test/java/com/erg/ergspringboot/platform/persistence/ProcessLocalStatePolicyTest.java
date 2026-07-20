package com.erg.ergspringboot.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.NotImplementedException;
import com.erg.ergspringboot.service.documents.DocumentService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProcessLocalStatePolicyTest {

    @Test
    void productionPolicyRejectsProcessLocalBusinessState() {
        ProcessLocalStatePolicy policy = new ProcessLocalStatePolicy(false);

        assertThatThrownBy(() -> policy.assertEnabled("documents"))
                .isInstanceOf(NotImplementedException.class)
                .hasMessageContaining("tenant-scoped persistent store");
    }

    @Test
    void processLocalDocumentStoreCannotBeConstructedWithoutTheProductionPolicy() {
        assertThat(Arrays.stream(DocumentService.class.getConstructors())
                .filter(constructor -> Arrays.stream(constructor.getParameterTypes())
                        .noneMatch(ProcessLocalStatePolicy.class::equals))
                .toList())
                .as("every DocumentService construction path must enforce the production process-local policy")
                .isEmpty();
    }
}
