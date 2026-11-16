package com.erg.ergspringboot.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Teaching-schedule failure with a stable code from KE_HOACH_LICH_DAY.md 4.2 (STALE_VERSION, NOT_FOUND, ...). */
public class TeachingScheduleException extends ApiException {

    private final Map<String, Object> extensions;

    public TeachingScheduleException(HttpStatus status, String code, String message, Map<String, Object> extensions) {
        super(status, code, code, message);
        this.extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }

    public static TeachingScheduleException notFound(String message) {
        return new TeachingScheduleException(HttpStatus.NOT_FOUND, "NOT_FOUND", message, null);
    }

    public static TeachingScheduleException staleVersion() {
        return new TeachingScheduleException(HttpStatus.CONFLICT, "STALE_VERSION",
                "Lịch vừa được người khác sửa. Tải lại để xem bản mới.", null);
    }

    public static TeachingScheduleException conflict(String code, String message) {
        return new TeachingScheduleException(HttpStatus.CONFLICT, code, message, null);
    }

    public static TeachingScheduleException badRequest(String code, String message) {
        return new TeachingScheduleException(HttpStatus.BAD_REQUEST, code, message, null);
    }

    /** Extra GraphQL {@code extensions} entries, e.g. the blocking conflict count. */
    public Map<String, Object> extensions() {
        return extensions;
    }
}
