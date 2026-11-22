package com.erg.ergspringboot.authorization.internal.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class AuthorizationCatalogCacheEvictorTest {

    private final ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager("authorizationRoles", "authorizationPermissions");
    private final AuthorizationCatalogCacheEvictor evictor;

    AuthorizationCatalogCacheEvictorTest() {
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        SessionFactoryImplementor sessionFactory = mock(SessionFactoryImplementor.class);
        when(factory.unwrap(SessionFactoryImplementor.class)).thenReturn(sessionFactory);
        when(sessionFactory.getEventListenerRegistry()).thenReturn(mock(EventListenerRegistry.class));
        evictor = new AuthorizationCatalogCacheEvictor(factory, caches);
    }

    @Test
    void roleAssignmentRoleAndPermissionWritesClearBothCatalogCaches() {
        for (Object entity : new Object[] {new UserRoleAssignment(), new Role(), new Permission()}) {
            fill();
            PostInsertEvent event = mock(PostInsertEvent.class);
            when(event.getEntity()).thenReturn(entity);
            evictor.onPostInsert(event);
            assertThat(caches.getCache("authorizationRoles").get("all")).as(entity.getClass().getSimpleName()).isNull();
            assertThat(caches.getCache("authorizationPermissions").get("all")).isNull();
        }
    }

    @Test
    void anUnrelatedUserRowUpdateKeepsTheCatalogCaches() {
        fill();
        PostUpdateEvent event = mock(PostUpdateEvent.class);
        when(event.getEntity()).thenReturn(new User());

        evictor.onPostUpdate(event);

        assertThat(caches.getCache("authorizationRoles").get("all")).isNotNull();
        assertThat(caches.getCache("authorizationPermissions").get("all")).isNotNull();
    }

    private void fill() {
        caches.getCache("authorizationRoles").put("all", "roles");
        caches.getCache("authorizationPermissions").put("all", "permissions");
    }
}
