package com.erg.ergspringboot.timekeeping.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SchoolScopeTest {

    private static ErgPrincipal principal(List<String> roles, List<String> permissions, Object managedSchools) {
        return new ErgPrincipal("u", "s", "t", roles, permissions, List.of(), List.of(), "STAFF", null,
                managedSchools == null ? Map.of() : Map.of("managedEducationUnitIds", managedSchools));
    }

    @Test
    void aPlatformAdminAndAWildcardManagerKeepTheWholeTenant() {
        assertThat(SchoolScope.of(principal(List.of("system.super_admin"), List.of("*"), null)).wide()).isTrue();
        assertThat(SchoolScope.of(principal(List.of(), List.of("timekeeping.read"), List.of("*"))).wide()).isTrue();
        assertThat(SchoolScope.TENANT.schoolsFor(null)).isNull();
        assertThat(SchoolScope.TENANT.schoolsFor("any")).containsExactly("any");
    }

    @Test
    void anyoneElseIsLimitedToTheirManagedSchools() {
        SchoolScope scope = SchoolScope.of(principal(List.of(), List.of("timekeeping.approve"), List.of("a", "b")));

        assertThat(scope.wide()).isFalse();
        assertThat(scope.schoolsFor(null)).containsExactlyInAnyOrder("a", "b");
        assertThat(scope.schoolsFor("a")).containsExactly("a");
        assertThat(scope.restrict(Set.of("a", "c"))).containsExactly("a");
        assertThatThrownBy(() -> scope.schoolsFor("c")).isInstanceOfSatisfying(TimekeepingException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> scope.requireRow(null)).isInstanceOf(TimekeepingException.class);
        SchoolScope.TENANT.requireRow(null);
        // Closing a pay period is for whoever runs the whole tenant (Phòng đào tạo), never a single-school manager.
        assertThatThrownBy(scope::requireWide).isInstanceOf(TimekeepingException.class);
        assertThat(SchoolScope.TENANT.requireWide().wide()).isTrue();
    }

    @Test
    void aCallerWithoutAnyManagedSchoolIsForbidden() {
        assertThatThrownBy(() -> SchoolScope.of(principal(List.of(), List.of("timekeeping.read"), null)))
                .isInstanceOfSatisfying(TimekeepingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
