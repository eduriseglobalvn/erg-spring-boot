package com.erg.ergspringboot.platform.security;

/**
 * Request-scoped tenant/user/portal identity captured from the authenticated principal (spec §1).
 *
 * <p>Phase 0.4 groundwork for row-level security: the values held here are the ones that will be
 * pushed into PostgreSQL session GUCs ({@code app.tenant_id}/{@code app.user_id}/{@code app.portal})
 * so RLS policies can read them via {@code current_setting(...)}. RLS is NOT enforced yet — this only
 * establishes and proves the plumbing so that turning policies on in Phase 6 is a low-risk switch.
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
        String resolvedTenant = tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId.trim();
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
