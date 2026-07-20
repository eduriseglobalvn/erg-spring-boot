package com.erg.ergspringboot.service.school;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class SchoolOperationsDemoSeedInitializerTest {

    @Test
    void seedsWhenExplicitlyEnabled() {
        SchoolOperationsDemoSeedService service = org.mockito.Mockito.mock(SchoolOperationsDemoSeedService.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("erg.lifecycle.school-operations-seed-on-startup", "true");

        new SchoolOperationsDemoSeedInitializer(service, environment)
                .run(new DefaultApplicationArguments());

        verify(service).seedIfEmpty("erg");
    }

    @Test
    void remainsDisabledByDefault() {
        SchoolOperationsDemoSeedService service = org.mockito.Mockito.mock(SchoolOperationsDemoSeedService.class);

        new SchoolOperationsDemoSeedInitializer(service, new MockEnvironment())
                .run(new DefaultApplicationArguments());

        verify(service, never()).seedIfEmpty("erg");
    }
}
