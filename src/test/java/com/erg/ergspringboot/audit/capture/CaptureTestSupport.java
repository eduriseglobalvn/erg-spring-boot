package com.erg.ergspringboot.audit.capture;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

final class CaptureTestSupport {
    private CaptureTestSupport() {}

    static ErgPrincipal principal() {
        return new ErgPrincipal("u-1", "s-1", "tenant-a", List.of("pdt_operator"), List.of("users.manage"),
                List.of(), List.of(), "staff", "admin", Map.of());
    }

    static void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal(), null, List.of()));
    }

    static void signOut() {
        SecurityContextHolder.clearContext();
    }
}
