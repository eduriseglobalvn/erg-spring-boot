package com.erg.ergspringboot.timekeeping.web;

import static com.erg.ergspringboot.timekeeping.web.TimekeepingSelfController.ok;
import static com.erg.ergspringboot.timekeeping.web.TimekeepingSelfController.principal;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayConfig;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PeriodLockCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PeriodLockView;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PolicyCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PolicyConfig;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PolicyVersions;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PunchReviewCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RecentPunch;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RemindCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RemindResult;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RemoteRuleCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RemoteRuleConfig;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ShiftCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ShiftConfig;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.SiteCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.SiteConfig;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.Timesheet;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.TodayDashboard;
import com.erg.ergspringboot.timekeeping.TimekeepingOperations;
import com.erg.ergspringboot.timekeeping.authorization.TimekeepingManage;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Timekeeping administration (A01 today, A02 month sheet and period lock, punch review, A04 configuration).
 * Every route needs timekeeping.manage; the tenant comes from the principal and every id is looked up with it.
 * {@code siteId} on /today is the "Cơ sở" filter, i.e. a school id (alias of {@code schoolId}).
 */
@RestController
@RequestMapping(TimekeepingPaths.ADMIN)
public class TimekeepingAdminController {

    private final TimekeepingOperations timekeeping;

    public TimekeepingAdminController(TimekeepingOperations timekeeping) {
        this.timekeeping = timekeeping;
    }

    @GetMapping("/today")
    @TimekeepingManage
    public ApiResponse<TodayDashboard> today(@RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        return ok("Today loaded", timekeeping.adminToday(principal(), schoolId != null ? schoolId : siteId, date),
                request);
    }

    @PostMapping("/remind")
    @TimekeepingManage
    public ApiResponse<RemindResult> remind(@RequestBody RemindCommand command, HttpServletRequest request) {
        return ok("Reminders sent", timekeeping.remind(principal(), command == null ? null : command.userIds()), request);
    }

    @GetMapping("/timesheet")
    @TimekeepingManage
    public ApiResponse<Timesheet> timesheet(@RequestParam String schoolId, @RequestParam String period,
            @RequestParam(required = false) String team, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        return ok("Timesheet loaded", timekeeping.timesheet(principal(), schoolId, period, team, q, page, size), request);
    }

    @PostMapping("/periods/{period}/lock")
    @TimekeepingManage
    public ApiResponse<PeriodLockView> lock(@PathVariable String period, @RequestBody PeriodLockCommand command,
            HttpServletRequest request) {
        return ok("Period locked", timekeeping.lockPeriod(principal(), command == null ? null : command.schoolId(),
                period), request);
    }

    @PostMapping("/periods/{period}/reopen")
    @TimekeepingManage
    public ApiResponse<PeriodLockView> reopen(@PathVariable String period, @RequestBody PeriodLockCommand command,
            HttpServletRequest request) {
        return ok("Period reopened", timekeeping.reopenPeriod(principal(), command == null ? null : command.schoolId(),
                period, command == null ? null : command.reason()), request);
    }

    @PostMapping("/punches/{id}/review")
    @TimekeepingManage
    public ApiResponse<RecentPunch> review(@PathVariable String id, @RequestBody PunchReviewCommand command,
            HttpServletRequest request) {
        return ok("Punch reviewed", timekeeping.reviewPunch(principal(), id, command), request);
    }

    @GetMapping("/sites")
    @TimekeepingManage
    public ApiResponse<List<SiteConfig>> sites(@RequestParam(required = false) String schoolId,
            HttpServletRequest request) {
        return ok("Sites loaded", timekeeping.sites(principal(), schoolId), request);
    }

    @PostMapping("/sites")
    @TimekeepingManage
    public ApiResponse<SiteConfig> createSite(@RequestBody SiteCommand command, HttpServletRequest request) {
        return ok("Site created", timekeeping.saveSite(principal(), null, command), request);
    }

    @PutMapping("/sites/{id}")
    @TimekeepingManage
    public ApiResponse<SiteConfig> updateSite(@PathVariable String id, @RequestBody SiteCommand command,
            HttpServletRequest request) {
        return ok("Site updated", timekeeping.saveSite(principal(), id, command), request);
    }

    @DeleteMapping("/sites/{id}")
    @TimekeepingManage
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSite(@PathVariable String id) {
        timekeeping.deleteSite(principal(), id);
    }

    @GetMapping("/shifts")
    @TimekeepingManage
    public ApiResponse<List<ShiftConfig>> shifts(@RequestParam(required = false) String schoolId,
            HttpServletRequest request) {
        return ok("Shifts loaded", timekeeping.shifts(principal(), schoolId), request);
    }

    @PostMapping("/shifts")
    @TimekeepingManage
    public ApiResponse<ShiftConfig> createShift(@RequestBody ShiftCommand command, HttpServletRequest request) {
        return ok("Shift created", timekeeping.saveShift(principal(), null, command), request);
    }

    @PutMapping("/shifts/{id}")
    @TimekeepingManage
    public ApiResponse<ShiftConfig> updateShift(@PathVariable String id, @RequestBody ShiftCommand command,
            HttpServletRequest request) {
        return ok("Shift updated", timekeeping.saveShift(principal(), id, command), request);
    }

    @DeleteMapping("/shifts/{id}")
    @TimekeepingManage
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShift(@PathVariable String id) {
        timekeeping.deleteShift(principal(), id);
    }

    @GetMapping("/policies/{schoolId}")
    @TimekeepingManage
    public ApiResponse<PolicyVersions> policy(@PathVariable String schoolId, HttpServletRequest request) {
        return ok("Policy loaded", timekeeping.policy(principal(), schoolId), request);
    }

    @PutMapping("/policies/{schoolId}")
    @TimekeepingManage
    public ApiResponse<PolicyConfig> savePolicy(@PathVariable String schoolId, @RequestBody PolicyCommand command,
            HttpServletRequest request) {
        return ok("Policy scheduled", timekeeping.savePolicy(principal(), schoolId, command), request);
    }

    @GetMapping("/holidays")
    @TimekeepingManage
    public ApiResponse<List<HolidayConfig>> holidays(@RequestParam(required = false) String schoolId,
            @RequestParam(required = false) Integer year, HttpServletRequest request) {
        return ok("Holidays loaded", timekeeping.holidays(principal(), schoolId, year), request);
    }

    @PostMapping("/holidays")
    @TimekeepingManage
    public ApiResponse<HolidayConfig> createHoliday(@RequestBody HolidayCommand command, HttpServletRequest request) {
        return ok("Holiday created", timekeeping.createHoliday(principal(), command), request);
    }

    @DeleteMapping("/holidays/{id}")
    @TimekeepingManage
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable String id) {
        timekeeping.deleteHoliday(principal(), id);
    }

    @GetMapping("/remote-rules")
    @TimekeepingManage
    public ApiResponse<List<RemoteRuleConfig>> remoteRules(@RequestParam(required = false) String schoolId,
            HttpServletRequest request) {
        return ok("Remote rules loaded", timekeeping.remoteRules(principal(), schoolId), request);
    }

    @PostMapping("/remote-rules")
    @TimekeepingManage
    public ApiResponse<RemoteRuleConfig> createRemoteRule(@RequestBody RemoteRuleCommand command,
            HttpServletRequest request) {
        return ok("Remote rule created", timekeeping.saveRemoteRule(principal(), null, command), request);
    }

    @PutMapping("/remote-rules/{id}")
    @TimekeepingManage
    public ApiResponse<RemoteRuleConfig> updateRemoteRule(@PathVariable String id,
            @RequestBody RemoteRuleCommand command, HttpServletRequest request) {
        return ok("Remote rule updated", timekeeping.saveRemoteRule(principal(), id, command), request);
    }

    @DeleteMapping("/remote-rules/{id}")
    @TimekeepingManage
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRemoteRule(@PathVariable String id) {
        timekeeping.deleteRemoteRule(principal(), id);
    }
}
