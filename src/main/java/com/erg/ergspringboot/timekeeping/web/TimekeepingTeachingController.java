package com.erg.ergspringboot.timekeeping.web;

import static com.erg.ergspringboot.timekeeping.web.TimekeepingSelfController.ok;
import static com.erg.ergspringboot.timekeeping.web.TimekeepingSelfController.principal;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.timekeeping.TeachingHoursOperations;
import com.erg.ergspringboot.timekeeping.TeachingViews.ConfirmTeachingCommand;
import com.erg.ergspringboot.timekeeping.TeachingViews.MyTeachingMonth;
import com.erg.ergspringboot.timekeeping.TeachingViews.ReportTeachingIssueCommand;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingConfirmationView;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingExport;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingMonthSheet;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingPeriodPage;
import com.erg.ergspringboot.timekeeping.authorization.TimekeepingApprove;
import com.erg.ergspringboot.timekeeping.authorization.TimekeepingManage;
import com.erg.ergspringboot.timekeeping.authorization.TimekeepingSelf;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teaching-hours (cong tiet day) sheets, KE_HOACH_CONG_BAO_GIANG.md 4.2.
 * {@code month} is {@code yyyy-MM}; {@code role} is {@code MAIN} (default) or {@code ASSISTANT}. The tenant and the
 * caller come only from the security context.
 */
@RestController
public class TimekeepingTeachingController {

    private final TeachingHoursOperations teaching;

    public TimekeepingTeachingController(TeachingHoursOperations teaching) {
        this.teaching = teaching;
    }

    @GetMapping(TimekeepingPaths.ME_TEACHING + "/{month}")
    @TimekeepingSelf
    public ApiResponse<MyTeachingMonth> myMonth(@PathVariable String month, HttpServletRequest request) {
        return ok("Teaching hours loaded", teaching.myMonth(principal(), month), request);
    }

    @GetMapping(TimekeepingPaths.ME_TEACHING + "/{month}/periods")
    @TimekeepingSelf
    public ApiResponse<TeachingPeriodPage> myPeriods(@PathVariable String month,
            @RequestParam(defaultValue = "MAIN") String role, @RequestParam(defaultValue = "0") int page,
            HttpServletRequest request) {
        return ok("Teaching periods loaded", teaching.myPeriods(principal(), month, role, page), request);
    }

    @PostMapping(TimekeepingPaths.ME_TEACHING + "/{month}/confirm")
    @TimekeepingSelf
    public ApiResponse<TeachingConfirmationView> confirm(@PathVariable String month,
            @RequestBody ConfirmTeachingCommand command, HttpServletRequest request) {
        return ok("Teaching hours confirmed", teaching.confirm(principal(), month, command), request);
    }

    @PostMapping(TimekeepingPaths.ME_TEACHING + "/{month}/issue")
    @TimekeepingSelf
    public ApiResponse<TeachingConfirmationView> reportIssue(@PathVariable String month,
            @RequestBody ReportTeachingIssueCommand command, HttpServletRequest request) {
        return ok("Teaching hours issue reported", teaching.reportIssue(principal(), month, command), request);
    }

    @GetMapping(TimekeepingPaths.ADMIN_TEACHING + "/periods")
    @TimekeepingApprove
    public ApiResponse<TeachingPeriodPage> periods(@RequestParam(defaultValue = "MAIN") String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String schoolId, @RequestParam(required = false) String teacherId,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            HttpServletRequest request) {
        return ok("Teaching periods loaded", teaching.periods(principal(), role, from, to, schoolId, teacherId, q, page),
                request);
    }

    @GetMapping(TimekeepingPaths.ADMIN_TEACHING + "/months/{month}")
    @TimekeepingApprove
    public ApiResponse<TeachingMonthSheet> monthSheet(@PathVariable String month,
            @RequestParam(defaultValue = "MAIN") String role, HttpServletRequest request) {
        return ok("Teaching month loaded", teaching.monthSheet(principal(), month, role), request);
    }

    @PostMapping(TimekeepingPaths.ADMIN_TEACHING + "/months/{month}/close")
    @TimekeepingManage
    public ApiResponse<TeachingMonthSheet> close(@PathVariable String month, HttpServletRequest request) {
        return ok("Teaching month closed", teaching.close(principal(), month), request);
    }

    @GetMapping(TimekeepingPaths.ADMIN_TEACHING + "/export")
    @TimekeepingApprove
    public ApiResponse<TeachingExport> teachingExport(@RequestParam int year, @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) Integer month, HttpServletRequest request) {
        return ok("Teaching export loaded", teaching.export(principal(), year, schoolId, month), request);
    }
}
