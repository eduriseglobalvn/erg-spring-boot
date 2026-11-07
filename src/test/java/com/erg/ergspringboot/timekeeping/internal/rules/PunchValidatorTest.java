package com.erg.ergspringboot.timekeeping.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.timekeeping.PunchStatus;
import org.junit.jupiter.api.Test;

class PunchValidatorTest {

    private static final double LAT = 21.0285;
    private static final double LNG = 105.8542;
    private static final SitePolicy STRICT = new SitePolicy(LAT, LNG, 150, false);
    private static final SitePolicy LENIENT = new SitePolicy(LAT, LNG, 150, true);

    /** A point due north of the site, roughly {@code meters} away. */
    private static PunchInput at(double meters, Double accuracy, boolean mock, String reason) {
        return new PunchInput(LAT + meters / 111_195.0, LNG, accuracy, mock, false, reason);
    }

    private static int distanceOf(PunchInput in) {
        return (int) Math.round(GeoDistance.haversineMeters(LAT, LNG, in.lat(), in.lng()));
    }

    @Test
    void insideZoneWithGoodAccuracyIsValid() {
        PunchCheck c = PunchValidator.check(at(120, 20.0, false, null), STRICT);
        assertThat(c.verdict()).isEqualTo(PunchStatus.VALID);
        assertThat(c.distanceM()).isEqualTo(120);
    }

    @Test
    void outsideZoneIsRejected() {
        PunchCheck c = PunchValidator.check(at(151, 20.0, false, null), STRICT);
        assertThat(c.verdict()).isEqualTo(PunchStatus.REJECTED);
        assertThat(c.distanceM()).isEqualTo(151);
        assertThat(c.messageVi()).isEqualTo("Bạn đang ở ngoài vùng chấm (cách 151 m)");
    }

    @Test
    void outsideZoneAllowedWithReasonIsFlagged() {
        PunchCheck c = PunchValidator.check(at(151, 20.0, false, "Đi công tác"), LENIENT);
        assertThat(c.verdict()).isEqualTo(PunchStatus.FLAGGED);
        assertThat(c.distanceM()).isEqualTo(151);
    }

    @Test
    void outsideZoneAllowedWithoutReasonIsRejected() {
        for (String reason : new String[] {null, "", "   "}) {
            PunchCheck c = PunchValidator.check(at(151, 20.0, false, reason), LENIENT);
            assertThat(c.verdict()).isEqualTo(PunchStatus.REJECTED);
            assertThat(c.messageVi()).isEqualTo("Nhập lý do chấm ngoài vùng");
        }
    }

    @Test
    void poorAccuracyNeedsReview() {
        assertThat(PunchValidator.check(at(10, 180.0, false, null), STRICT).verdict())
                .isEqualTo(PunchStatus.PENDING_REVIEW);
    }

    @Test
    void accuracyBoundary() {
        assertThat(PunchValidator.check(at(10, 100.0, false, null), STRICT).verdict()).isEqualTo(PunchStatus.VALID);
        assertThat(PunchValidator.check(at(10, 101.0, false, null), STRICT).verdict())
                .isEqualTo(PunchStatus.PENDING_REVIEW);
    }

    @Test
    void missingAccuracyNeedsReview() {
        assertThat(PunchValidator.check(at(10, null, false, null), STRICT).verdict())
                .isEqualTo(PunchStatus.PENDING_REVIEW);
    }

    @Test
    void mockedLocationNeedsReview() {
        assertThat(PunchValidator.check(at(10, 20.0, true, null), STRICT).verdict())
                .isEqualTo(PunchStatus.PENDING_REVIEW);
    }

    @Test
    void mockWinsOverOutsideZone() {
        PunchCheck c = PunchValidator.check(at(500, 20.0, true, null), STRICT);
        assertThat(c.verdict()).isEqualTo(PunchStatus.PENDING_REVIEW);
        assertThat(c.distanceM()).isEqualTo(500);
    }

    @Test
    void distanceExactlyOnRadiusIsValid() {
        PunchInput in = at(150, 20.0, false, null);
        int d = distanceOf(in);
        assertThat(PunchValidator.check(in, new SitePolicy(LAT, LNG, d, false)).verdict()).isEqualTo(PunchStatus.VALID);
        assertThat(PunchValidator.check(in, new SitePolicy(LAT, LNG, d - 1, false)).verdict())
                .isEqualTo(PunchStatus.REJECTED);
    }

    @Test
    void remoteWithoutLocationIsValid() {
        PunchCheck c = PunchValidator.check(new PunchInput(null, null, null, false, true, null), STRICT);
        assertThat(c.verdict()).isEqualTo(PunchStatus.VALID);
        assertThat(c.distanceM()).isNull();
    }

    @Test
    void remoteWithMockIsStillValid() {
        PunchCheck c = PunchValidator.check(new PunchInput(LAT, LNG, 500.0, true, true, null), STRICT);
        assertThat(c.verdict()).isEqualTo(PunchStatus.VALID);
    }

    @Test
    void noLocationAndNotRemoteIsRejected() {
        for (PunchInput in : new PunchInput[] {
            new PunchInput(null, null, null, false, false, null),
            new PunchInput(LAT, null, 10.0, false, false, null),
            new PunchInput(null, LNG, 10.0, false, false, null)
        }) {
            PunchCheck c = PunchValidator.check(in, STRICT);
            assertThat(c.verdict()).isEqualTo(PunchStatus.REJECTED);
            assertThat(c.messageVi()).isEqualTo("Chưa lấy được vị trí");
            assertThat(c.distanceM()).isNull();
        }
    }
}
