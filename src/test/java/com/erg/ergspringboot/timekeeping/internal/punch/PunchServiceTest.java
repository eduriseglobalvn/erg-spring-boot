package com.erg.ergspringboot.timekeeping.internal.punch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.timekeeping.internal.schedule.TeacherPeriodReader;
import org.springframework.transaction.PlatformTransactionManager;
import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchResult;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.evidence.FaceConsentService;
import com.erg.ergspringboot.timekeeping.internal.evidence.PunchPhotoService;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkSite;
import com.erg.ergspringboot.timekeeping.persistence.TkSiteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PunchServiceTest {

    private static final String T = "tenant-a";
    private static final String U = "teacher-1";
    private static final String SCHOOL = "school-1";
    /** Thursday 12/11/2026 07:40 in Ho Chi Minh: 10 minutes after a 07:30 shift start. */
    private static final Instant NOW = Instant.parse("2026-11-12T00:40:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
    private static final String PHOTO_KEY = "private/timekeeping/tenant-a/2026/11/p.jpg";

    private final TkPunchRepository punches = mock(TkPunchRepository.class);
    private final TkSiteRepository sites = mock(TkSiteRepository.class);
    private final TkShiftRepository shifts = mock(TkShiftRepository.class);
    private final WorkContextResolver context = mock(WorkContextResolver.class);
    private final PunchPhotoService photos = mock(PunchPhotoService.class);
    private final FaceConsentService consents = mock(FaceConsentService.class);
    private final PunchCodeGenerator codes = mock(PunchCodeGenerator.class);
    private final DayAggregationService days = mock(DayAggregationService.class);
    private final CalendarReadPort calendar = mock(CalendarReadPort.class);
    private final TkPolicy policy = new TkPolicy();
    private final TkShift shift = shift();
    private final List<TkPunch> todays = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(context.schoolOf(T, U)).thenReturn(Optional.of(SCHOOL));
        when(context.shiftFor(T, SCHOOL, DAY)).thenReturn(Optional.of(shift));
        when(context.policyFor(T, SCHOOL, DAY)).thenReturn(policy);
        when(context.activeSites(T, SCHOOL)).thenReturn(List.of(site()));
        when(sites.findById("site-1")).thenReturn(Optional.of(site()));
        when(shifts.findById("shift-1")).thenReturn(Optional.of(shift));
        when(punches.findByTenantIdAndUserIdAndIdempotencyKey(eq(T), eq(U), anyString())).thenReturn(Optional.empty());
        when(punches.findByTenantIdAndUserIdAndWorkDateOrderByServerTimeAscCodeAsc(T, U, DAY)).thenReturn(todays);
        when(punches.saveAndFlush(any(TkPunch.class))).thenAnswer(call -> {
            TkPunch p = call.getArgument(0);
            p.setId("punch-new");
            return p;
        });
        when(consents.hasConsent(T, U)).thenReturn(true);
        when(photos.storePunchPhoto(eq(T), eq(U), any())).thenReturn(PHOTO_KEY);
        when(codes.next(T, DAY)).thenReturn("CC-121126-0001");
        when(calendar.teacherPeriods(eq(T), eq(U), any(), any())).thenReturn(List.of(new TeacherPeriod(
                Instant.parse("2026-11-12T01:00:00Z"), Instant.parse("2026-11-12T01:45:00Z"), "7A", "Toán", "P201")));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void validInStoresTheServerClockTimeAndRecomputesTheDay() {
        PunchResult result = service(NOW).punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO));

        assertThat(result.verdict()).isEqualTo(PunchStatus.VALID);
        assertThat(result.alreadyPunched()).isFalse();
        assertThat(result.punch().serverTime()).isEqualTo(NOW);
        assertThat(result.punch().code()).isEqualTo("CC-121126-0001");
        assertThat(result.punch().method()).isEqualTo("GPS_FACE");
        assertThat(result.punch().siteName()).isEqualTo("Cơ sở Cầu Giấy");
        assertThat(result.punch().distanceM()).isZero();
        assertThat(result.minutesVsShift()).isEqualTo(10);
        assertThat(result.nextPeriod().className()).isEqualTo("7A");
        TkPunch saved = savedPunch();
        assertThat(saved.getServerTime()).isEqualTo(NOW);
        assertThat(saved.getWorkDate()).isEqualTo(DAY);
        assertThat(saved.getPhotoKey()).isEqualTo(PHOTO_KEY);
        assertThat(saved.getSchoolId()).isEqualTo(SCHOOL);
        assertThat(saved.getShiftId()).isEqualTo("shift-1");
        assertThat(saved.getLat()).isEqualByComparingTo("21.0285");
        assertThat(saved.getLng()).isEqualByComparingTo("105.8542");
        assertThat(saved.getAccuracyM()).isEqualTo(20);
        assertThat(saved.getSiteId()).isEqualTo("site-1");
        verify(days).recompute(T, U, DAY);
    }

    @Test
    void calendarFailureDoesNotFailThePunch() {
        when(calendar.teacherPeriods(eq(T), eq(U), any(), any())).thenThrow(new IllegalStateException("calendar down"));

        PunchResult result = service(NOW).punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO));

        assertThat(result.punch().code()).isEqualTo("CC-121126-0001");
        assertThat(result.nextPeriod()).isNull();
        verify(days).recompute(T, U, DAY);
    }

    @Test
    void sameIdempotencyKeyReturnsTheStoredPunchWithoutANewRow() {
        PunchResult original = service(NOW).punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO));
        TkPunch stored = savedPunch();
        when(punches.findByTenantIdAndUserIdAndIdempotencyKey(T, U, "key-1")).thenReturn(Optional.of(stored));
        org.mockito.Mockito.clearInvocations(punches, photos, codes);

        PunchResult result = service(Instant.parse("2026-11-12T00:55:00Z"))
                .punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO));

        assertThat(result.punch()).isEqualTo(original.punch());
        assertThat(result.punch().siteName()).isEqualTo("Cơ sở Cầu Giấy");
        assertThat(result.minutesVsShift()).isEqualTo(original.minutesVsShift()).isEqualTo(10);
        assertThat(result.nextPeriod()).isEqualTo(original.nextPeriod()).isNotNull();
        assertThat(service(NOW).replay(T, U, "key-1")).hasValueSatisfying(r -> {
            assertThat(r.punch()).isEqualTo(original.punch());
            assertThat(r.minutesVsShift()).isEqualTo(10);
        });
        verify(punches, never()).saveAndFlush(any());
        verifyNoInteractions(photos, codes);
    }

    @Test
    void secondInWithAnotherKeyReturnsTheFirstInAsAlreadyPunched() {
        todays.add(punch("p-1", PunchDirection.IN, PunchStatus.VALID, "CC-121126-0001"));

        PunchResult result = service(NOW).punch(T, U, "key-2", in(21.0285, 105.8542, 20.0, null, PHOTO));

        assertThat(result.alreadyPunched()).isTrue();
        assertThat(result.punch().code()).isEqualTo("CC-121126-0001");
        assertThat(result.punch().siteName()).isEqualTo("Cơ sở Cầu Giấy");
        assertThat(result.minutesVsShift()).isEqualTo(-5);
        assertThat(result.nextPeriod().className()).isEqualTo("7A");
        verify(punches, never()).saveAndFlush(any());
        verifyNoInteractions(photos, codes);
    }

    @Test
    void inThatAppearsBeforeTheCounterLockDiscardsTheUploadedPhotoAndIsAlreadyPunched() {
        when(codes.next(T, DAY)).thenAnswer(call -> {
            todays.add(punch("p-race", PunchDirection.IN, PunchStatus.VALID, "CC-121126-0001"));
            return "CC-121126-0002";
        });

        PunchResult result = service(NOW).punch(T, U, "key-2", in(21.0285, 105.8542, 20.0, null, PHOTO));

        assertThat(result.alreadyPunched()).isTrue();
        assertThat(result.punch().id()).isEqualTo("p-race");
        verify(punches, never()).saveAndFlush(any());
        verify(photos).discardPhoto(PHOTO_KEY);
        verify(days, never()).recompute(any(), any(), any());
    }

    @Test
    void photoIsUploadedBeforeTheCounterLockIsTaken() {
        service(NOW).punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(photos, codes, punches);
        order.verify(photos).storePunchPhoto(T, U, PHOTO);
        order.verify(codes).next(T, DAY);
        order.verify(punches).saveAndFlush(any());
        verify(photos, never()).discardPhoto(anyString());
    }

    @Test
    void repeatedOutStoresANewRowSoTheLatestOutCounts() {
        todays.add(punch("p-1", PunchDirection.IN, PunchStatus.VALID, "CC-121126-0001"));
        todays.add(punch("p-2", PunchDirection.OUT, PunchStatus.VALID, "CC-121126-0002"));

        PunchResult result = service(Instant.parse("2026-11-12T04:35:00Z"))
                .punch(T, U, "key-3", command(PunchDirection.OUT, 21.0285, 105.8542, 20.0, null, PHOTO));

        assertThat(result.alreadyPunched()).isFalse();
        assertThat(result.minutesVsShift()).isEqualTo(5);
        assertThat(savedPunch().getDirection()).isEqualTo(PunchDirection.OUT);
    }

    @Test
    void requiredFaceWithoutPhotoIsABadRequest() {
        assertThatThrownBy(() -> service(NOW).punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, null)))
                .isInstanceOfSatisfying(TimekeepingException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Cần chụp ảnh khuôn mặt");
                });
        verify(punches, never()).saveAndFlush(any());
    }

    @Test
    void missingFaceConsentIsAConflictAndNothingIsUploaded() {
        when(consents.hasConsent(T, U)).thenReturn(false);

        assertThatThrownBy(() -> service(NOW).punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO)))
                .isInstanceOfSatisfying(TimekeepingException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("FACE_CONSENT_REQUIRED");
                });
        verifyNoInteractions(photos);
    }

    @Test
    void lockedPeriodIsAConflict() {
        when(context.isLocked(T, SCHOOL, DAY)).thenReturn(true);

        assertThatThrownBy(() -> service(NOW).punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO)))
                .isInstanceOfSatisfying(TimekeepingException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("PERIOD_LOCKED");
                    assertThat(ex.getMessage()).isEqualTo("Kỳ công đã chốt");
                });
        verifyNoInteractions(photos);
    }

    @Test
    void remoteRuleDropsTheLocationEvenWhenTheClientSentIt() {
        when(context.isRemote(T, SCHOOL, U, DAY, "shift-1")).thenReturn(true);

        PunchResult result = service(NOW).punch(T, U, "key-1", in(10.0, 106.0, 20.0, null, PHOTO));

        assertThat(result.verdict()).isEqualTo(PunchStatus.VALID);
        assertThat(result.punch().remote()).isTrue();
        assertThat(result.punch().distanceM()).isNull();
        assertThat(result.punch().method()).isEqualTo("REMOTE_FACE");
        TkPunch saved = savedPunch();
        assertThat(saved.getLat()).isNull();
        assertThat(saved.getLng()).isNull();
        assertThat(saved.getAccuracyM()).isNull();
        assertThat(saved.getDistanceM()).isNull();
        assertThat(saved.getSiteId()).isNull();
    }

    @Test
    void outsideTheZoneWithoutPermissionIsRejectedAndNotStored() {
        assertThatThrownBy(() -> service(NOW).punch(T, U, "key-1", in(21.0385, 105.8542, 20.0, null, PHOTO)))
                .isInstanceOfSatisfying(TimekeepingException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getCode()).isEqualTo("PUNCH_REJECTED");
                    assertThat(ex.getMessage()).startsWith("Bạn đang ở ngoài vùng chấm (cách ");
                });
        verify(punches, never()).saveAndFlush(any());
        verifyNoInteractions(photos);
    }

    @Test
    void outsideTheZoneWhenAllowedNeedsAReasonAndIsFlagged() {
        policy.setAllowOutside(true);

        assertThatThrownBy(() -> service(NOW).punch(T, U, "key-1", in(21.0385, 105.8542, 20.0, " ", PHOTO)))
                .isInstanceOfSatisfying(TimekeepingException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo("Nhập lý do chấm ngoài vùng"));

        PunchResult result = service(NOW).punch(T, U, "key-2", in(21.0385, 105.8542, 20.0, "Họp phụ huynh", PHOTO));
        assertThat(result.verdict()).isEqualTo(PunchStatus.FLAGGED);
        assertThat(savedPunch().getReason()).isEqualTo("Họp phụ huynh");
    }

    @Test
    void punchOutsideTheShiftWindowIsUnprocessable() {
        // 05:29 is before 07:30 - 120 min; 12:31 is after 11:30 + 60 min.
        assertThatThrownBy(() -> service(Instant.parse("2026-11-11T22:29:00Z"))
                .punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO)))
                .isInstanceOfSatisfying(TimekeepingException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("OUTSIDE_PUNCH_WINDOW"));
        assertThatThrownBy(() -> service(Instant.parse("2026-11-12T05:31:00Z"))
                .punch(T, U, "key-1", command(PunchDirection.OUT, 21.0285, 105.8542, 20.0, null, PHOTO)))
                .isInstanceOfSatisfying(TimekeepingException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getCode()).isEqualTo("OUTSIDE_PUNCH_WINDOW");
                });
        // IN closes at the shift end, OUT opens at the shift start.
        assertThatThrownBy(() -> service(Instant.parse("2026-11-12T04:31:00Z"))
                .punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO)))
                .isInstanceOf(TimekeepingException.class);
        assertThat(service(Instant.parse("2026-11-11T22:30:00Z"))
                .punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO)).verdict())
                .isEqualTo(PunchStatus.VALID);
    }

    @Test
    void noShiftTodayIsUnprocessable() {
        when(context.shiftFor(T, SCHOOL, DAY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(NOW).punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO)))
                .isInstanceOfSatisfying(TimekeepingException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NO_SHIFT"));
    }

    @Test
    void idempotencyKeyMustBePresentAndAtMostOneHundredCharacters() {
        for (String bad : new String[] {null, "", "  ", "k".repeat(101)}) {
            assertThatThrownBy(() -> service(NOW).punch(T, U, bad, in(21.0285, 105.8542, 20.0, null, PHOTO)))
                    .isInstanceOfSatisfying(TimekeepingException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThat(service(NOW).punch(T, U, "k".repeat(100), in(21.0285, 105.8542, 20.0, null, PHOTO)).verdict())
                .isEqualTo(PunchStatus.VALID);
    }

    @Test
    void coordinatesAndAccuracyOutOfRangeAreBadRequests() {
        double[][] bad = {{90.5, 105, 20}, {-91, 105, 20}, {21, 180.1, 20}, {21, -181, 20}, {21, 105, -1},
                {21, 105, Double.NaN}, {Double.NaN, 105, 20}, {21, Double.POSITIVE_INFINITY, 20}};
        for (double[] v : bad) {
            assertThatThrownBy(() -> service(NOW).punch(T, U, "key-1", in(v[0], v[1], v[2], null, PHOTO)))
                    .isInstanceOfSatisfying(TimekeepingException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(punches, never()).saveAndFlush(any());
    }

    @Test
    void uploadedPhotoIsDiscardedWhenThePunchTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        service(NOW).punch(T, U, "key-1", in(21.0285, 105.8542, 20.0, null, PHOTO));
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(photos, never()).discardPhoto(anyString());
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(photos).discardPhoto(PHOTO_KEY);
    }

    private PunchService service(Instant now) {
        return new PunchService(punches, sites, shifts, context, photos, consents, codes, days,
                new TeacherPeriodReader(calendar, mock(PlatformTransactionManager.class)),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private TkPunch savedPunch() {
        ArgumentCaptor<TkPunch> captor = ArgumentCaptor.forClass(TkPunch.class);
        verify(punches, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static PunchCommand in(double lat, double lng, double accuracy, String reason, byte[] photo) {
        return command(PunchDirection.IN, lat, lng, accuracy, reason, photo);
    }

    private static PunchCommand command(
            PunchDirection direction, double lat, double lng, double accuracy, String reason, byte[] photo) {
        return new PunchCommand(direction, lat, lng, accuracy, false, reason, photo);
    }

    private static TkShift shift() {
        TkShift s = new TkShift();
        s.setId("shift-1");
        s.setTenantId(T);
        s.setSchoolId(SCHOOL);
        s.setName("Ca sáng");
        s.setStartTime(LocalTime.of(7, 30));
        s.setEndTime(LocalTime.of(11, 30));
        s.setWorkUnits(BigDecimal.ONE);
        s.setWeekdays(new Integer[] {1, 2, 3, 4, 5});
        return s;
    }

    private static TkSite site() {
        TkSite s = new TkSite();
        s.setId("site-1");
        s.setTenantId(T);
        s.setSchoolId(SCHOOL);
        s.setName("Cơ sở Cầu Giấy");
        s.setLat(new BigDecimal("21.028500"));
        s.setLng(new BigDecimal("105.854200"));
        s.setRadiusM(150);
        return s;
    }

    private static TkPunch punch(String id, PunchDirection direction, PunchStatus status, String code) {
        TkPunch p = new TkPunch();
        p.setId(id);
        p.setTenantId(T);
        p.setUserId(U);
        p.setSchoolId(SCHOOL);
        p.setShiftId("shift-1");
        p.setSiteId("site-1");
        p.setWorkDate(DAY);
        p.setDirection(direction);
        p.setStatus(status);
        p.setCode(code);
        p.setServerTime(Instant.parse("2026-11-12T00:25:00Z"));
        p.setMethod("GPS_FACE");
        p.setIdempotencyKey("old-" + id);
        return p;
    }
}
