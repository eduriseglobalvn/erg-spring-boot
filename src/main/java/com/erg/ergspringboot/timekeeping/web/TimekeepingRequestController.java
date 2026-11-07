package com.erg.ergspringboot.timekeeping.web;

import static com.erg.ergspringboot.timekeeping.web.TimekeepingSelfController.ok;
import static com.erg.ergspringboot.timekeeping.web.TimekeepingSelfController.principal;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.TimekeepingOperations;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.ExplanationContext;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.LeavePreview;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestPage;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestView;
import com.erg.ergspringboot.timekeeping.authorization.TimekeepingSelf;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own requests (leave, explanation, WFH, business trip). Another person's request id answers 404. */
@RestController
@RequestMapping(TimekeepingPaths.REQUESTS)
public class TimekeepingRequestController {

    private final TimekeepingOperations timekeeping;

    public TimekeepingRequestController(TimekeepingOperations timekeeping) {
        this.timekeeping = timekeeping;
    }

    @PostMapping
    @TimekeepingSelf
    public ApiResponse<RequestView> create(@RequestBody RequestCommand command, HttpServletRequest request) {
        return ok("Draft created", timekeeping.createRequest(principal(), command), request);
    }

    @PutMapping("/{id}")
    @TimekeepingSelf
    public ApiResponse<RequestView> update(@PathVariable String id, @RequestBody RequestCommand command,
            HttpServletRequest request) {
        return ok("Draft updated", timekeeping.updateRequest(principal(), id, command), request);
    }

    @PostMapping("/{id}/submit")
    @TimekeepingSelf
    public ApiResponse<RequestView> submit(@PathVariable String id, HttpServletRequest request) {
        return ok("Request submitted", timekeeping.submitRequest(principal(), id), request);
    }

    @PostMapping("/{id}/cancel")
    @TimekeepingSelf
    public ApiResponse<RequestView> cancel(@PathVariable String id, HttpServletRequest request) {
        return ok("Request cancelled", timekeeping.cancelRequest(principal(), id), request);
    }

    @PostMapping("/{id}/remind")
    @TimekeepingSelf
    public ApiResponse<RequestView> remind(@PathVariable String id, HttpServletRequest request) {
        return ok("Approvers reminded", timekeeping.remindRequest(principal(), id), request);
    }

    @GetMapping("/mine")
    @TimekeepingSelf
    public ApiResponse<RequestPage> mine(@RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ok("Requests loaded", timekeeping.myRequests(principal(), status, page, size), request);
    }

    @GetMapping("/preview-leave")
    @TimekeepingSelf
    public ApiResponse<LeavePreview> previewLeave(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "FULL") DayPart part, @RequestParam LeaveKind kind,
            HttpServletRequest request) {
        return ok("Leave preview", timekeeping.previewLeave(principal(), from, to, part, kind), request);
    }

    @GetMapping("/explanation-context")
    @TimekeepingSelf
    public ApiResponse<ExplanationContext> explanationContext(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, HttpServletRequest request) {
        return ok("Explanation context", timekeeping.explanationContext(principal(), date), request);
    }

    @GetMapping("/{id}")
    @TimekeepingSelf
    public ApiResponse<RequestView> get(@PathVariable String id, HttpServletRequest request) {
        return ok("Request loaded", timekeeping.request(principal(), id), request);
    }
}
