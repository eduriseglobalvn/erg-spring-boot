package com.erg.ergspringboot.timekeeping.internal.rules;

import com.erg.ergspringboot.timekeeping.PunchStatus;

/** Decides whether a punch counts, needs a manager's review, or is refused. */
public final class PunchValidator {

    /** GPS accuracy worse than this (metres) cannot be trusted to place the person inside the zone. */
    static final double MAX_ACCURACY_M = 100;

    private PunchValidator() {}

    /**
     * Checks in this order, first match wins: remote is VALID without any location; no location is REJECTED;
     * a mocked location or missing/poor accuracy is PENDING_REVIEW; outside the radius is FLAGGED (with a reason,
     * when the site allows it) or REJECTED; otherwise VALID. Distance is rounded to the nearest metre and that
     * rounded value is what is compared with the radius, so the message never contradicts the verdict.
     */
    public static PunchCheck check(PunchInput in, SitePolicy site) {
        if (in.remote()) {
            return new PunchCheck(PunchStatus.VALID, null, null);
        }
        if (in.lat() == null || in.lng() == null) {
            return new PunchCheck(PunchStatus.REJECTED, null, "Chưa lấy được vị trí");
        }
        int distance = (int) Math.round(GeoDistance.haversineMeters(site.lat(), site.lng(), in.lat(), in.lng()));
        if (in.mockReported()) {
            return new PunchCheck(PunchStatus.PENDING_REVIEW, distance, "Vị trí nghi giả lập, chờ quản lý duyệt");
        }
        if (in.accuracyM() == null || in.accuracyM() > MAX_ACCURACY_M) {
            return new PunchCheck(PunchStatus.PENDING_REVIEW, distance, "Độ chính xác GPS thấp, chờ quản lý duyệt");
        }
        if (distance > site.radiusM()) {
            if (!site.allowOutside()) {
                return new PunchCheck(
                        PunchStatus.REJECTED, distance, "Bạn đang ở ngoài vùng chấm (cách " + distance + " m)");
            }
            if (in.reason() == null || in.reason().isBlank()) {
                return new PunchCheck(PunchStatus.REJECTED, distance, "Nhập lý do chấm ngoài vùng");
            }
            return new PunchCheck(PunchStatus.FLAGGED, distance, "Chấm ngoài vùng (cách " + distance + " m)");
        }
        return new PunchCheck(PunchStatus.VALID, distance, null);
    }
}
