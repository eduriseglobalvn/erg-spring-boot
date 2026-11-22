package com.erg.ergspringboot.authorization.internal.catalog;

import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.PermissionBinding;
import com.erg.ergspringboot.users.persistence.authorization.PermissionGroup;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverride;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Clears the cached role and permission lists ({@code authorizationRoles}, {@code authorizationPermissions}) whenever a
 * row they show changes: roles, permissions, their bindings and groups, role assignments, permission overrides and a
 * user's direct roles (the role's user count). Listening at the ORM covers every writer (admin API, provisioning,
 * seeds) like {@code PrincipalCacheInvalidationListener}; the tiered cache applies the clear after commit.
 */
@Component
class AuthorizationCatalogCacheEvictor implements PostInsertEventListener, PostUpdateEventListener,
        PostDeleteEventListener, PostCollectionRecreateEventListener, PostCollectionUpdateEventListener,
        PostCollectionRemoveEventListener {

    static final List<String> CACHES = List.of("authorizationRoles", "authorizationPermissions");

    private final CacheManager caches;

    AuthorizationCatalogCacheEvictor(
            @Qualifier("postgresEntityManagerFactory") EntityManagerFactory entityManagerFactory, CacheManager caches) {
        this.caches = caches;
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
        Object owner = event.getAffectedOwnerOrNull();
        // A user row changes on every login; only its direct roles (the role's user count) matter here.
        if (owner instanceof User) {
            String role = event.getCollection() == null ? null : event.getCollection().getRole();
            if (role != null && role.endsWith(".roles")) evict();
            return;
        }
        changed(owner);
    }

    private void changed(Object entity) {
        if (entity instanceof Role || entity instanceof Permission || entity instanceof PermissionBinding
                || entity instanceof PermissionGroup || entity instanceof UserRoleAssignment
                || entity instanceof UserPermissionOverride) {
            evict();
        }
    }

    private void evict() {
        for (String name : CACHES) {
            Cache cache = caches.getCache(name);
            if (cache != null) cache.clear();
        }
    }
}
