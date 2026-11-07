package com.erg.ergspringboot.timekeeping.web;

import static com.erg.ergspringboot.timekeeping.web.TimekeepingSelfController.ok;
import static com.erg.ergspringboot.timekeeping.web.TimekeepingSelfController.principal;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.timekeeping.ApprovalBox;
import com.erg.ergspringboot.timekeeping.DueFilter;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ApprovalComment;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ApprovalDetail;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ApprovalPage;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.BulkApproveCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.BulkApproveResult;
import com.erg.ergspringboot.timekeeping.TimekeepingOperations;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestView;
import com.erg.ergspringboot.timekeeping.authorization.TimekeepingApprove;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Approval inbox (A03). {@code siteId} is the school filter. A request the caller neither approves nor manages
 * answers 404; a manage holder who is not the current step's approver acts on behalf (audited).
 */
@RestController
@RequestMapping(TimekeepingPaths.APPROVALS)
public class TimekeepingApprovalController {

    private final TimekeepingOperations timekeeping;

    public TimekeepingApprovalController(TimekeepingOperations timekeeping) {
        this.timekeeping = timekeeping;
    }

    @GetMapping
    @TimekeepingApprove
    public ApiResponse<ApprovalPage> list(@RequestParam(defaultValue = "MINE") ApprovalBox box,
            @RequestParam(required = false) RequestType type, @RequestParam(required = false) String siteId,
            @RequestParam(required = false) DueFilter due, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        return ok("Approvals loaded", timekeeping.approvals(principal(), box, type, siteId, due, page, size), request);
    }

    @PostMapping("/bulk-approve")
    @TimekeepingApprove
    public ApiResponse<BulkApproveResult> bulkApprove(@RequestBody BulkApproveCommand command,
            HttpServletRequest request) {
        return ok("Bulk approve done", timekeeping.bulkApprove(principal(), command == null ? null : command.requestIds()),
                request);
    }

    @GetMapping("/{requestId}")
    @TimekeepingApprove
    public ApiResponse<ApprovalDetail> detail(@PathVariable String requestId, HttpServletRequest request) {
        return ok("Approval loaded", timekeeping.approval(principal(), requestId), request);
    }

    @PostMapping("/{requestId}/approve")
    @TimekeepingApprove
    public ApiResponse<RequestView> approve(@PathVariable String requestId,
            @RequestBody(required = false) ApprovalComment body, HttpServletRequest request) {
        return ok("Request approved", timekeeping.approve(principal(), requestId, body == null ? null : body.comment()),
                request);
    }

    @PostMapping("/{requestId}/reject")
    @TimekeepingApprove
    public ApiResponse<RequestView> reject(@PathVariable String requestId,
            @RequestBody(required = false) ApprovalComment body, HttpServletRequest request) {
        return ok("Request rejected", timekeeping.reject(principal(), requestId, body == null ? null : body.comment()),
                request);
    }
}
