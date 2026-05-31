package com.erg.ergspringboot.repository.hoclieu;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class ResourceRecordRepositoryContractTest {

    @Test
    void scopedResourceQueryDoesNotLowerNullableParameters() throws Exception {
        Method method = ResourceRecordRepository.class.getMethod(
                "findScoped",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .doesNotContain("lower(:status)")
                .doesNotContain("lower(:visibility)")
                .contains("lower(resource.status) = :status")
                .contains("lower(resource.visibility) = :visibility");
    }
}
