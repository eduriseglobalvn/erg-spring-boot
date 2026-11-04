package com.erg.ergspringboot.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AuthorizationFacadeTypeBoundaryTest {
    @Test
    void publicFacadeDoesNotExposeLegacyServiceTypes() {
        for (Method method : AuthorizationCommandOperations.class.getDeclaredMethods()) {
            assertFalse(method.toGenericString().contains("com.erg.ergspringboot.service.users."));
        }
    }
}
