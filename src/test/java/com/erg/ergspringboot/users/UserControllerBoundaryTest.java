package com.erg.ergspringboot.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.users.AdminEmployeeController;
import com.erg.ergspringboot.controller.users.UserController;
import com.erg.ergspringboot.auth.AuthOperations;
import org.junit.jupiter.api.Test;

class UserControllerBoundaryTest {
    @Test
    void userControllerDependsOnUsersFacadeAndAuthAdapter() {
        assertThat(UserController.class.getConstructors()).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(UserOperations.class, AuthOperations.class));
    }

    @Test
    void adminControllerDependsOnUsersFacadeAndEmployeeFacades() {
        assertThat(AdminEmployeeController.class.getConstructors()).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(UserOperations.class, EmployeeOperations.class));
    }
}
