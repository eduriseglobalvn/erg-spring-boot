package com.erg.ergspringboot.timekeeping.internal.evidence;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import com.erg.ergspringboot.users.UserAuthorizationProjection;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Stores punch selfies privately and hands out 5-minute URLs to the owner and to approvers/managers only. */
@Service
public class PunchPhotoService {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final Duration URL_TTL = Duration.ofMinutes(5);
    private static final List<String> REVIEW_PERMISSIONS = List.of("timekeeping.approve", "timekeeping.manage");
    private static final Pattern SAFE_TENANT = Pattern.compile("[A-Za-z0-9_-]+");

    private final PrivateObjectStorage storage;
    private final TkPunchRepository punches;
    private final UserAuthorizationPort users;
    private final ErgPolicyService policy;
    private final Clock clock;

    PunchPhotoService(PrivateObjectStorage storage, TkPunchRepository punches, UserAuthorizationPort users,
            ErgPolicyService policy, Clock clock) {
        this.storage = storage;
        this.punches = punches;
        this.users = users;
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

    /** Empty once the photo is purged; throws when the viewer is neither the owner nor an approver/manager. */
    public Optional<URI> photoUrl(String tenantId, String punchId, String viewerId) {
        TkPunch punch = punches.findByIdAndTenantId(punchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt chấm công"));
        if (viewerId == null || (!viewerId.equals(punch.getUserId()) && !canReview(tenantId, viewerId))) {
            throw new AccessDeniedException("Bạn không có quyền xem ảnh chấm công này");
        }
        if (punch.getPhotoKey() == null) return Optional.empty();
        return Optional.of(storage.presignGet(punch.getPhotoKey(), URL_TTL));
    }

    private boolean canReview(String tenantId, String viewerId) {
        return users.find(viewerId, tenantId)
                .map(snapshot -> policy.isAllowedAny(principal(snapshot), null, tenantId, REVIEW_PERMISSIONS))
                .orElse(false);
    }

    // Grants and denies stay separate so the policy honours wildcard denies (e.g. "timekeeping.*").
    private static ErgPrincipal principal(UserAuthorizationProjection snapshot) {
        List<String> grants = Stream.concat(snapshot.rolePermissions().stream(), snapshot.grantedPermissions().stream())
                .distinct().toList();
        return ErgPrincipal.authenticated(snapshot.userId(), null, snapshot.tenantId(), snapshot.roleNames(),
                grants, snapshot.deniedPermissions(), List.of(), null, null, Map.of());
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
