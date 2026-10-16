package com.erg.ergspringboot.platform.security;

/**
 * Request-scoped tenant/user/portal identity captured from the authenticated principal (spec §1).
 *
 * <p>The values held here are pushed into PostgreSQL session GUCs ({@code app.tenant_id}/
 * {@code app.user_id}/{@code app.portal}) so strict quiz-domain RLS policies can read them via
 * {@code current_setting(...)}. Policy coverage for legacy tables remains an explicit rollout gate.
 *
 * <p>Held in a {@link ThreadLocal} because request threads own a single DB connection for the span of
 * a transaction; {@link TenantContextFilter} sets it at the start of each request and clears it after.
 */
public final class TenantContext {

    public static final String DEFAULT_TENANT_ID = "erg";

    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    private final String tenantId;
    private final String userId;
    private final String portal;
    private final boolean maintenance;

    private TenantContext(String tenantId, String userId, String portal) {
        this(tenantId, userId, portal, false);
    }

    private TenantContext(String tenantId, String userId, String portal, boolean maintenance) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.portal = portal;
        this.maintenance = maintenance;
    }

    public static TenantContext of(String tenantId, String userId, String portal) {
        String resolvedTenant = AuthenticatedTenant.require(tenantId);
        return new TenantContext(resolvedTenant, blankToNull(userId), blankToNull(portal));
    }

    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    /** Explicit context for bounded internal maintenance workers, never for HTTP requests. */
    public static TenantContext maintenance() {
        return new TenantContext("__maintenance__", null, "system", true);
    }

    /** Returns the current context, or {@code null} when called outside a request (e.g. background jobs). */
    public static TenantContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** Opens a nested context and restores the previous value when closed. */
    public static Scope scope(TenantContext context) {
        TenantContext previous = current();
        if (context == null) {
            clear();
        } else {
            set(context);
        }
        return new Scope(previous);
    }

    /** Captures only tenant/correlation identity; it never captures a principal, token or SecurityContext. */
    public static Carrier capture() {
        TenantContext context = current();
        return context == null ? null : new Carrier(context.tenantId, context.userId, context.portal, context.maintenance);
    }

    /** Installs a previously captured tenant carrier and guarantees cleanup/restoration. */
    public static Scope install(Carrier carrier) {
        return scope(carrier == null ? null : carrier.toContext());
    }

    public String tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public String portal() {
        return portal;
    }

    public boolean isMaintenance() {
        return maintenance;
    }

    public record Carrier(String tenantId, String userId, String portal, boolean maintenance) {
        private TenantContext toContext() {
            return new TenantContext(tenantId, userId, portal, maintenance);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final TenantContext previous;
        private boolean closed;

        private Scope(TenantContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) clear(); else set(previous);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
