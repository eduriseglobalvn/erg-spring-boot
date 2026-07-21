package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPermissionsResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Maps authenticated permission state to Calendar workspace capabilities. */
@Service
public class CalendarPermissionsQueryUseCase {

    public CalendarPermissionsResponse permissions(ErgPrincipal principal) {
        if (principal == null) {
            return new CalendarPermissionsResponse(false, false, false, false, false);
        }
        Set<String> permissions = principal.permissions() != null
                ? new HashSet<>(principal.permissions()) : Set.of();
        Set<String> denied = principal.deniedPermissions() != null
                ? new HashSet<>(principal.deniedPermissions()) : Set.of();
        boolean wildcard = permissions.contains("*");
        boolean canManage = wildcard || (permissions.contains("lms.schedule.manage")
                && !denied.contains("lms.schedule.manage") && !denied.contains("*"));
        boolean canRead = wildcard || (permissions.contains("lms.schedule.read")
                && !denied.contains("lms.schedule.read") && !denied.contains("*"));
        return new CalendarPermissionsResponse(
                canManage, canManage, canManage, canManage, canRead);
    }
}
