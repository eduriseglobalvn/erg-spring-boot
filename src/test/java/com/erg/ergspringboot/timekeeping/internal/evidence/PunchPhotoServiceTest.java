package com.erg.ergspringboot.timekeeping.internal.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PunchPhotoServiceTest {

    private static final String TENANT = "tenant-a";
    private static final int TWO_MB = 2 * 1024 * 1024;

    private final PrivateObjectStorage storage = mock(PrivateObjectStorage.class);
    private final TkPunchRepository punches = mock(TkPunchRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"), ZoneOffset.UTC);
    private final PunchPhotoService service = new PunchPhotoService(storage, punches, new ErgPolicyService(), clock);

    @Test
    void photoOverTwoMegabytesIsRejected() {
        byte[] big = jpeg((int) (2.5 * 1024 * 1024));

        assertThatThrownBy(() -> service.storePunchPhoto(TENANT, "u1", big))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh tối đa 2 MB");
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void exactlyTwoMegabytesIsAcceptedAndOneMoreByteIsRejected() {
        assertThat(service.storePunchPhoto(TENANT, "u1", jpeg(TWO_MB))).endsWith(".jpg");
        assertThatThrownBy(() -> service.storePunchPhoto(TENANT, "u1", jpeg(TWO_MB + 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh tối đa 2 MB");
    }

    @Test
    void jpegIsStoredUnderPrivateTenantKeyWithContentTypeFromMagicBytes() {
        String key = service.storePunchPhoto(TENANT, "u1", jpeg(100));

        assertThat(key).matches("private/timekeeping/tenant-a/2026/11/[0-9a-f-]{36}\\.jpg");
        verify(storage).put(eq(key), any(), eq("image/jpeg"));
    }

    @Test
    void webpIsDetectedByRiffWebpHeader() {
        byte[] webp = new byte[64];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);

        String key = service.storePunchPhoto(TENANT, "u1", webp);

        assertThat(key).endsWith(".webp");
        verify(storage).put(eq(key), any(), eq("image/webp"));
    }

    @Test
    void pngTextAndEmptyPayloadsAreRejected() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        byte[] text = "not really a jpg".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.storePunchPhoto(TENANT, "u1", png))
                .isInstanceOf(BadRequestException.class).hasMessage("Ảnh không hợp lệ");
        assertThatThrownBy(() -> service.storePunchPhoto(TENANT, "u1", text))
                .isInstanceOf(BadRequestException.class).hasMessage("Ảnh không hợp lệ");
        assertThatThrownBy(() -> service.storePunchPhoto(TENANT, "u1", new byte[0]))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.storePunchPhoto(TENANT, "u1", null))
                .isInstanceOf(BadRequestException.class);
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void tenantIdThatCouldEscapeTheKeyIsRejected() {
        assertThatThrownBy(() -> service.storePunchPhoto("../other", "u1", jpeg(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discardPhotoDeletesAndSwallowsStorageFailures() {
        service.discardPhoto("k1.jpg");
        verify(storage).delete("k1.jpg");

        doThrow(new IllegalStateException("r2 down")).when(storage).delete("k2.jpg");
        assertThatCode(() -> service.discardPhoto("k2.jpg")).doesNotThrowAnyException();
        assertThatCode(() -> service.discardPhoto(null)).doesNotThrowAnyException();
    }

    @Test
    void ownerGetsFiveMinuteUrl() {
        punch("owner", "private/timekeeping/tenant-a/2026/11/p.jpg");
        when(storage.presignGet("private/timekeeping/tenant-a/2026/11/p.jpg", Duration.ofMinutes(5)))
                .thenReturn(URI.create("https://signed.example/p"));

        assertThat(service.photoUrl(principal("owner", List.of("timekeeping.self"), List.of()), "punch-1"))
                .contains(URI.create("https://signed.example/p"));
    }

    @Test
    void approverAndManagerMaySeeAnotherUsersPhoto() {
        punch("owner", "k.jpg");
        when(storage.presignGet(eq("k.jpg"), any())).thenReturn(URI.create("https://signed.example/k"));

        assertThat(service.photoUrl(
                principal("lead", List.of("timekeeping.self", "timekeeping.approve"), List.of()), "punch-1")).isPresent();
        assertThat(service.photoUrl(principal("admin", List.of("timekeeping.manage"), List.of()), "punch-1"))
                .isPresent();
    }

    @Test
    void otherTeacherWithoutApproveIsDenied() {
        punch("owner", "k.jpg");

        assertThatThrownBy(() -> service.photoUrl(principal("teacher", List.of("timekeeping.self"), List.of()), "punch-1"))
                .isInstanceOf(AccessDeniedException.class);
        verify(storage, never()).presignGet(anyString(), any());
    }

    @Test
    void wildcardDenyBeatsApproveGrant() {
        punch("owner", "k.jpg");

        assertThatThrownBy(() -> service.photoUrl(
                principal("lead", List.of("timekeeping.approve"), List.of("timekeeping.*")), "punch-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void accessIsCheckedBeforeThePurgedState() {
        punch("owner", null);

        assertThatThrownBy(() -> service.photoUrl(principal("teacher", List.of("timekeeping.self"), List.of()), "punch-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void purgedPhotoGivesEmpty() {
        punch("owner", null);

        assertThat(service.photoUrl(principal("owner", List.of(), List.of()), "punch-1")).isEmpty();
        verify(storage, never()).presignGet(anyString(), any());
    }

    @Test
    void punchOfAnotherTenantIsNotFound() {
        punch("owner", "k.jpg");
        ErgPrincipal otherTenant = ErgPrincipal.authenticated("owner", "s", "tenant-b", List.of(),
                List.of("timekeeping.manage"), List.of(), List.of(), null, null, Map.of());
        when(punches.findByIdAndTenantId("punch-1", "tenant-b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.photoUrl(otherTenant, "punch-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void punch(String ownerId, String photoKey) {
        TkPunch punch = new TkPunch();
        punch.setTenantId(TENANT);
        punch.setUserId(ownerId);
        punch.setPhotoKey(photoKey);
        when(punches.findByIdAndTenantId("punch-1", TENANT)).thenReturn(Optional.of(punch));
    }

    private static ErgPrincipal principal(String userId, List<String> permissions, List<String> denied) {
        return ErgPrincipal.authenticated(userId, "session", TENANT, List.of("lms.teacher"), permissions, denied,
                List.of(), null, null, Map.of());
    }

    private static byte[] jpeg(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
