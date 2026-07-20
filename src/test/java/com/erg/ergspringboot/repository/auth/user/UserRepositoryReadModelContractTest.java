package com.erg.ergspringboot.repository.auth.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class UserRepositoryReadModelContractTest {

    @Test
    void adminUserListUsesPageableProjectionInsteadOfLoadingUserEntities() throws Exception {
        Method method = UserRepository.class.getMethod(
                "findAdminListItems",
                String.class,
                String.class,
                String.class,
                String.class,
                Pageable.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("UserListItemProjection")
                .contains("exists (")
                .contains("from u.roles r")
                .contains("u.tenantId = :tenantId")
                .contains("order by u.createdAt desc")
                .doesNotContain("select u");
        assertThat(method.getReturnType().getName()).isEqualTo("org.springframework.data.domain.Page");
    }

    @Test
    void adminUserListRolesAreFetchedOnlyForCurrentPage() throws Exception {
        Method method = UserRepository.class.getMethod("findRoleNamesByUserIds", java.util.Collection.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("UserRoleNameProjection")
                .contains("where u.id in :userIds")
                .contains("join u.roles r");
    }
}
