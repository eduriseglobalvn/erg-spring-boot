package com.erg.ergspringboot.timekeeping.web;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.TimekeepingOperations;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.ClockView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.DayDetailView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.MonthView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchResult;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.TodayView;
import com.erg.ergspringboot.timekeeping.authorization.TimekeepingSelf;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The caller's own timekeeping. Who the caller is comes only from the security context; times only from the server. */
@RestController
@RequestMapping(TimekeepingPaths.ME)
public class TimekeepingSelfController {

    private final TimekeepingOperations timekeeping;

    public TimekeepingSelfController(TimekeepingOperations timekeeping) {
        this.timekeeping = timekeeping;
    }

    @GetMapping("/clock")
    @TimekeepingSelf
    public ApiResponse<ClockView> clock(HttpServletRequest request) {
        principal();
        return ok("Server time", timekeeping.clock(), request);
    }

    @GetMapping("/today")
    @TimekeepingSelf
    public ApiResponse<TodayView> today(HttpServletRequest request) {
        return ok("Today loaded", timekeeping.today(principal()), request);
    }

    /** Multipart on purpose: the selfie travels with the punch. Any client time field is not bound and so ignored. */
    @PostMapping(value = "/punches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @TimekeepingSelf
    public ApiResponse<PunchResult> punch(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam PunchDirection direction,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double accuracyM,
            @RequestParam(defaultValue = "false") boolean mockReported,
            @RequestParam(required = false) String reason,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            HttpServletRequest request) {
        PunchCommand command = new PunchCommand(direction, lat, lng, accuracyM, mockReported, reason, bytes(photo));
        return ok("Punch recorded", timekeeping.punch(principal(), idempotencyKey, command), request);
    }

    @PostMapping("/face-consent")
    @TimekeepingSelf
    public ApiResponse<Void> faceConsent(HttpServletRequest request) {
        timekeeping.recordFaceConsent(principal());
        return ok("Face consent recorded", null, request);
    }

    @GetMapping("/month")
    @TimekeepingSelf
    public ApiResponse<MonthView> month(@RequestParam String period, HttpServletRequest request) {
        return ok("Month loaded", timekeeping.month(principal(), period), request);
    }

    @GetMapping("/days/{date}")
    @TimekeepingSelf
    public ApiResponse<DayDetailView> day(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, HttpServletRequest request) {
        return ok("Day loaded", timekeeping.day(principal(), date), request);
    }

    private static byte[] bytes(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) return null;
        try {
            return photo.getBytes();
        } catch (IOException unreadable) {
            throw new TimekeepingException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO", "Ảnh không hợp lệ");
        }
    }

    private static ErgPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ErgPrincipal principal)) {
            throw new ForbiddenException("Authentication is required.");
        }
        AuthenticatedTenant.require(principal.tenantId());
        return principal;
    }

    private static <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), RequestCorrelation.current(request));
    }
}
