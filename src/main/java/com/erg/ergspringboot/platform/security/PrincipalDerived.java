package com.erg.ergspringboot.platform.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Per-principal values derived once from the immutable {@link ErgPrincipal}: alias-expanded grants and denies and the
 * Spring authorities. Keyed by instance identity (weak), so a principal served from {@link PrincipalCache} is expanded once.
 */
record PrincipalDerived(List<String> grants, List<String> denies, List<SimpleGrantedAuthority> authorities) {

    static final Map<String, List<String>> ALIASES = Map.of(
            "roles.read", List.of("rbac.role.read", "rbac.permission.read"),
            "roles.create", List.of("rbac.role.create"),
            "roles.update", List.of("rbac.role.update"),
            "roles.delete", List.of("rbac.role.delete"),
            "roles.assign", List.of("rbac.role.assign", "rbac.override.manage"),
            "content.manage", List.of("content.*", "curriculum.*", "cms.*", "courses.*", "media.*"),
            "system.logs", List.of("audit.read")
    );

    private static final Cache<ErgPrincipal, PrincipalDerived> CACHE = Caffeine.newBuilder().weakKeys().build();

    static PrincipalDerived of(ErgPrincipal principal) {
        return CACHE.get(principal, PrincipalDerived::compute);
    }

    private static PrincipalDerived compute(ErgPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = Stream.concat(
                        principal.roles().stream().map(role -> new SimpleGrantedAuthority("role:" + role)),
                        principal.permissions().stream().map(permission -> new SimpleGrantedAuthority("perm:" + permission)))
                .toList();
        return new PrincipalDerived(expand(principal.permissions()), expand(principal.deniedPermissions()), authorities);
    }

    private static List<String> expand(List<String> permissions) {
        return permissions.stream().flatMap(permission -> expanded(permission, 0)).distinct().toList();
    }

    private static Stream<String> expanded(String permission, int depth) {
        if (depth > 5) {
            return Stream.of(permission);
        }
        return Stream.concat(Stream.of(permission),
                ALIASES.getOrDefault(permission, List.of()).stream().flatMap(item -> expanded(item, depth + 1)));
    }
}
