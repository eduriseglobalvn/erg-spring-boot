package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverride;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScope;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AbstractCollectionEvent;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCollectionRecreateEvent;
import org.hibernate.event.spi.PostCollectionRecreateEventListener;
import org.hibernate.event.spi.PostCollectionRemoveEvent;
import org.hibernate.event.spi.PostCollectionRemoveEventListener;
import org.hibernate.event.spi.PostCollectionUpdateEvent;
import org.hibernate.event.spi.PostCollectionUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Evicts cached principals whenever a row they were built from changes: sessions (logout, logout-all,
 * revoke, replace, refresh), the user (status, lifecycle flags, password), role assignments, permission
 * overrides, access scopes and role/permission definitions. Listening at the ORM catches every writer;
 * none of these tables is written with bulk JPQL or JDBC in production code.
 */
@Component
class PrincipalCacheInvalidationListener implements PostInsertEventListener, PostUpdateEventListener,
        PostDeleteEventListener, PostCollectionRecreateEventListener, PostCollectionUpdateEventListener,
        PostCollectionRemoveEventListener {

    private final PrincipalCache cache;

    PrincipalCacheInvalidationListener(
            @Qualifier("postgresEntityManagerFactory") EntityManagerFactory entityManagerFactory,
            PrincipalCache cache) {
        this.cache = cache;
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getEventListenerRegistry();
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
        registry.appendListeners(EventType.POST_DELETE, this);
        registry.appendListeners(EventType.POST_COLLECTION_RECREATE, this);
        registry.appendListeners(EventType.POST_COLLECTION_UPDATE, this);
        registry.appendListeners(EventType.POST_COLLECTION_REMOVE, this);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        changed(event.getEntity());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        changed(event.getEntity());
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        changed(event.getEntity());
    }

    @Override
    public void onPostRecreateCollection(PostCollectionRecreateEvent event) {
        collectionChanged(event);
    }

    @Override
    public void onPostUpdateCollection(PostCollectionUpdateEvent event) {
        collectionChanged(event);
    }

    @Override
    public void onPostRemoveCollection(PostCollectionRemoveEvent event) {
        collectionChanged(event);
    }

    private void collectionChanged(AbstractCollectionEvent event) {
        changed(event.getAffectedOwnerOrNull());
    }

    private void changed(Object entity) {
        switch (entity) {
            case AuthSession session -> cache.evictUser(session.getUserId());
            case User user -> cache.evictUser(user.getId());
            case UserRoleAssignment assignment -> cache.evictUser(assignment.getUserId());
            case UserPermissionOverride override -> cache.evictUser(override.getUserId());
            case UserAccessScope scope -> cache.evictUser(scope.getUserId());
            case Role ignored -> cache.evictAll();
            case Permission ignored -> cache.evictAll();
            case null, default -> { }
        }
    }
}
