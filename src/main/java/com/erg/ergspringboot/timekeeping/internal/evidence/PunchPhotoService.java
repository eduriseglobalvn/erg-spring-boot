package com.erg.ergspringboot.timekeeping.internal.evidence;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Stores punch selfies privately and hands out 5-minute URLs to the owner and to approvers/managers only.
 *
 * <p>Caller contract (T4 punch API): call {@link #storePunchPhoto} only after the punch passed validation and
 * the idempotency check, inside the punch transaction, and register a
 * {@code TransactionSynchronization.afterCompletion} that calls {@link #discardPhoto} on {@code STATUS_ROLLED_BACK}
 * so a failed punch leaves no orphan selfie. The 60-day purge only sees keys that were committed.
 */
@Service
public class PunchPhotoService {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final Duration URL_TTL = Duration.ofMinutes(5);
    private static final List<String> REVIEW_PERMISSIONS = List.of("timekeeping.approve", "timekeeping.manage");
    private static final Pattern SAFE_TENANT = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Logger log = LoggerFactory.getLogger(PunchPhotoService.class);

    private final PrivateObjectStorage storage;
    private final TkPunchRepository punches;
    private final ErgPolicyService policy;
    private final Clock clock;

    PunchPhotoService(PrivateObjectStorage storage, TkPunchRepository punches, ErgPolicyService policy, Clock clock) {
        this.storage = storage;
        this.punches = punches;
        this.policy = policy;
        this.clock = clock;
    }

    /** Returns the private key; the type comes from the bytes, never from a client header. */
    public String storePunchPhoto(String tenantId, String userId, byte[] photo) {
        if (photo == null || photo.length == 0) throw new BadRequestException("Ảnh không hợp lệ");
        if (photo.length > MAX_BYTES) throw new BadRequestException("Ảnh tối đa 2 MB");
        String extension = isJpeg(photo) ? "jpg" : isWebp(photo) ? "webp" : null;
        if (extension == null) throw new BadRequestException("Ảnh không hợp lệ");
        if (tenantId == null || !SAFE_TENANT.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("tenant id is not usable in a storage key");
        }
        ZonedDateTime now = clock.instant().atZone(ZoneOffset.UTC);
        String key = "%s%s/%04d/%02d/%s.%s".formatted(LocalPrivateObjectStorage.KEY_PREFIX, tenantId,
                now.getYear(), now.getMonthValue(), UUID.randomUUID(), extension);
        storage.put(key, photo, "jpg".equals(extension) ? "image/jpeg" : "image/webp");
        return key;
    }

    /** Best-effort delete of a photo whose punch was rolled back; never throws, never logs the key. */
    public void discardPhoto(String key) {
        if (key == null) return;
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Discarding an uncommitted punch photo failed: {}", ex.getClass().getSimpleName());
        }
    }

    /**
     * For the caller's verified principal (tenant and grants come from it, never from request input).
     * Empty once the photo is purged; throws when the caller is neither the owner nor an approver/manager.
     */
    public Optional<URI> photoUrl(ErgPrincipal principal, String punchId) {
        if (principal == null || principal.userId() == null || principal.tenantId() == null) {
            throw new AccessDeniedException("Bạn không có quyền xem ảnh chấm công này");
        }
        String tenantId = principal.tenantId();
        TkPunch punch = punches.findByIdAndTenantId(punchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt chấm công"));
        if (!principal.userId().equals(punch.getUserId())
                && !policy.isAllowedAny(principal, null, tenantId, REVIEW_PERMISSIONS)) {
            throw new AccessDeniedException("Bạn không có quyền xem ảnh chấm công này");
        }
        if (punch.getPhotoKey() == null) return Optional.empty();
        return Optional.of(storage.presignGet(punch.getPhotoKey(), URL_TTL));
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
