package com.erg.ergspringboot.controller.calendar.graphql;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.MyCalendarRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.MyStudentCalendarInput;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.calendar.CalendarOperations;
import com.erg.ergspringboot.service.calendar.CalendarReadService;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class CalendarLmsQueryGraphQlController {

    private final CalendarOperations calendar;
    private final GraphQlAccessSupport accessSupport;

    @org.springframework.beans.factory.annotation.Autowired
    public CalendarLmsQueryGraphQlController(
            CalendarOperations calendar,
            GraphQlAccessSupport accessSupport
    ) {
        this.calendar = calendar;
        this.accessSupport = accessSupport;
    }

    /** Compatibility constructor retained for existing focused adapter tests. */
    @Deprecated
    public CalendarLmsQueryGraphQlController(CalendarReadService reads,
            GraphQlAccessSupport accessSupport) {
        this(new CalendarOperations(reads), accessSupport);
    }

    @SchemaMapping(typeName = "LmsQuery", field = "myTeachingCalendar")
    public CalendarWorkspaceResponse myTeachingCalendar(@Argument MyCalendarRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.read_self", "lms.schedule.read",
                "lms.schedule.manage", "lms.manage", "*");
        MyCalendarRequest safeInput = input == null
                ? new MyCalendarRequest(null, null, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of())
                : input;
        String tenantId = accessSupport.verifiedTenantId(principal, safeInput.tenantId());
        String teacherUserId = requirePrincipalUserId(principal);

        return calendar.teacherWorkspace(
                tenantId, teacherUserId,
                safeInput.from(), safeInput.to(), safeInput.viewMode(),
                safeInput.schoolIds(), safeInput.classIds(), safeInput.subjectIds(),
                safeInput.levelIds(), safeInput.statuses(),
                principal);
    }

    @SchemaMapping(typeName = "LmsQuery", field = "myStudentCalendar")
    public CalendarWorkspaceResponse myStudentCalendar(@Argument MyStudentCalendarInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.read_self", "lms.schedule.read",
                "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input == null ? null : input.tenantId());
        String studentUserId = requirePrincipalUserId(principal);

        return calendar.studentWorkspace(
                tenantId, studentUserId,
                input == null ? null : input.from(),
                input == null ? null : input.to(),
                input == null ? null : input.viewMode(),
                input == null ? null : input.subjectIds(),
                input == null ? null : input.levelIds(),
                input == null ? null : input.statuses(),
                principal);
    }

    private String requirePrincipalUserId(ErgPrincipal principal) {
        String userId = accessSupport.blankToNull(principal == null ? null : principal.userId());
        if (userId == null) {
            throw new ForbiddenException("GraphQL my teaching calendar requires a scoped principal.");
        }
        return userId;
    }
}
