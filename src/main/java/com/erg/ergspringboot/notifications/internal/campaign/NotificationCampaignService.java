package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.NotificationCampaignRequests.CampaignDraftRequest;
import com.erg.ergspringboot.notifications.NotificationCampaignResponses.CampaignResponse;
import com.erg.ergspringboot.notifications.NotificationPriority;
import com.erg.ergspringboot.notifications.internal.CampaignDelivery;
import com.erg.ergspringboot.notifications.internal.NotificationCommandService;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.platform.security.WorkspaceCapabilityMapper;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campaign writes. Every write locks the campaign row and checks the sender's scope (AudienceScopeGuard) before
 * anything is stored; a send writes all recipient messages and the SENT status in one transaction.
 */
@Service
public class NotificationCampaignService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCampaignService.class);
    private static final Set<String> RECIPIENT_ROLES = Set.of("TEACHER", "STUDENT", "STAFF");
    private static final int MAX_LIST_SIZE = NotificationAudienceResolver.MAX_RECIPIENTS;
    private static final int MAX_SCHEDULE_DAYS = 30;
    static final String SEND_PERMISSION = "notification.campaign.send";

    private final NotificationCampaignRepository campaigns;
    private final AudienceScopeGuard guard;
    private final NotificationAudienceResolver resolver;
    private final NotificationCommandService commands;
    private final NotificationCampaignQueryService queries;
    private final UserAuthorizationPort authorizations;
    private final UserDirectoryPort users;
    private final Clock clock;

    @Autowired
    public NotificationCampaignService(NotificationCampaignRepository campaigns, AudienceScopeGuard guard,
            NotificationAudienceResolver resolver, NotificationCommandService commands,
            NotificationCampaignQueryService queries, UserAuthorizationPort authorizations, UserDirectoryPort users) {
        this(campaigns, guard, resolver, commands, queries, authorizations, users, Clock.systemUTC());
    }

    NotificationCampaignService(NotificationCampaignRepository campaigns, AudienceScopeGuard guard,
            NotificationAudienceResolver resolver, NotificationCommandService commands,
            NotificationCampaignQueryService queries, UserAuthorizationPort authorizations, UserDirectoryPort users,
            Clock clock) {
        this.campaigns = campaigns;
        this.guard = guard;
        this.resolver = resolver;
        this.commands = commands;
        this.queries = queries;
        this.authorizations = authorizations;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AudienceBreakdown estimate(CampaignActor actor, CampaignAudience requested) {
        CampaignAudience audience = validated(requested);
        guard.assertAllowed(actor, audience);
        return resolver.resolve(actor.tenantId(), audience).breakdown();
    }

    @Transactional
    public CampaignResponse create(CampaignActor actor, CampaignDraftRequest request) {
        NotificationPriority priority = priority(request.priority());
        CampaignAudience audience = validated(request.audience());
        guard.assertAllowed(actor, audience);
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setTenantId(actor.tenantId());
        campaign.setCreatedBy(actor.userId());
        campaign.setSenderRole(guard.isAdmin(actor) ? "ADMIN" : "TEACHER");
        campaign.setStatus(CampaignStatus.DRAFT);
        apply(campaign, request, priority, audience);
        campaign.setEstimatedCount(resolver.resolve(actor.tenantId(), audience).breakdown().total());
        return queries.toResponse(campaigns.save(campaign));
    }

    @Transactional
    public CampaignResponse update(CampaignActor actor, String id, CampaignDraftRequest request) {
        NotificationPriority priority = priority(request.priority());
        CampaignAudience audience = validated(request.audience());
        NotificationCampaignEntity campaign = lockOwned(actor, id);
        requireEditable(campaign, "Chỉ sửa được thông báo nháp hoặc đang hẹn giờ");
        guard.assertAllowed(actor, audience);
        apply(campaign, request, priority, audience);
        campaign.setEstimatedCount(resolver.resolve(actor.tenantId(), audience).breakdown().total());
        return queries.toResponse(campaigns.save(campaign));
    }

    @Transactional
    public CampaignResponse send(CampaignActor actor, String id) {
        NotificationCampaignEntity campaign = lockOwned(actor, id);
        requireEditable(campaign, "Thông báo đã được gửi hoặc đã huỷ");
        guard.assertAllowed(actor, campaign.getAudience());
        deliver(campaign);
        return queries.toResponse(campaign);
    }

    @Transactional
    public CampaignResponse schedule(CampaignActor actor, String id, OffsetDateTime scheduledAt) {
        OffsetDateTime now = now();
        if (scheduledAt == null || !scheduledAt.isAfter(now)) {
            throw new BadRequestException("Thời điểm hẹn phải ở tương lai");
        }
        if (scheduledAt.isAfter(now.plusDays(MAX_SCHEDULE_DAYS))) {
            throw new BadRequestException("Chỉ hẹn tối đa 30 ngày tới");
        }
        NotificationCampaignEntity campaign = lockOwned(actor, id);
        requireEditable(campaign, "Thông báo đã được gửi hoặc đã huỷ");
        guard.assertAllowed(actor, campaign.getAudience());
        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus(CampaignStatus.SCHEDULED);
        return queries.toResponse(campaigns.save(campaign));
    }

    @Transactional
    public CampaignResponse cancel(CampaignActor actor, String id) {
        NotificationCampaignEntity campaign = lockOwned(actor, id);
        if (campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new ConflictException("Chỉ huỷ được thông báo đang hẹn giờ");
        }
        campaign.setStatus(CampaignStatus.CANCELLED);
        return queries.toResponse(campaigns.save(campaign));
    }

    /**
     * Job path: the conditional SCHEDULED -> SENDING update is the double-dispatch guard (0 rows: someone else
     * has it, or it was cancelled). Recipients are resolved now, against the creator's current roles and scope.
     */
    @Transactional
    public boolean dispatchDue(String tenantId, String id) {
        if (campaigns.claimDue(tenantId, id, now()) == 0) {
            return false;
        }
        NotificationCampaignEntity campaign = campaigns.findByTenantIdAndId(tenantId, id).orElseThrow();
        try {
            guard.assertAllowed(creator(tenantId, campaign.getCreatedBy()), campaign.getAudience());
        } catch (AccessDeniedException denied) {
            log.warn("Scheduled campaign {} failed: creator {} is no longer allowed to reach its audience",
                    id, campaign.getCreatedBy());
            campaign.setStatus(CampaignStatus.FAILED);
            campaigns.save(campaign);
            return false;
        }
        deliver(campaign);
        return true;
    }

    /** After a rolled-back dispatch: terminal FAILED so the job does not retry it every minute. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String tenantId, String id) {
        campaigns.transition(tenantId, id, List.of(CampaignStatus.SCHEDULED, CampaignStatus.SENDING),
                CampaignStatus.FAILED, now());
    }

    private void deliver(NotificationCampaignEntity campaign) {
        List<String> recipients = resolver.resolve(campaign.getTenantId(), campaign.getAudience()).userIds();
        if (recipients.isEmpty()) {
            throw new BadRequestException("Không có người nhận nào trong phạm vi đã chọn");
        }
        commands.enqueueCampaign(new CampaignDelivery(campaign.getTenantId(), campaign.getCreatedBy(),
                campaign.getId(), campaign.getTitle(), campaign.getBody(), campaign.getPriority(),
                campaign.isPushEnabled(), recipients));
        campaign.setResolvedCount(recipients.size());
        campaign.setSentAt(now());
        campaign.setStatus(CampaignStatus.SENT);
        campaigns.save(campaign);
    }

    /**
     * The creator as they are now. An inactive or missing account, or one that no longer holds the send permission
     * (nor the platform-wide '*'), is nobody, so the guard denies and the campaign ends FAILED.
     */
    private CampaignActor creator(String tenantId, String userId) {
        boolean active = users.findByTenantIdAndIds(tenantId, List.of(userId)).stream()
                .anyMatch(user -> user.status() == null || "ACTIVE".equalsIgnoreCase(user.status()));
        if (!active) {
            return CampaignActor.none(tenantId, userId);
        }
        return authorizations.find(userId, tenantId)
                .map(grants -> CampaignActor.of(tenantId, userId, grants.roleNames(), grants.effectivePermissions(),
                        grants.deniedPermissions()))
                .filter(actor -> actor.permissions().contains(SEND_PERMISSION) || WorkspaceCapabilityMapper
                        .isPlatformWide(List.copyOf(actor.roles()), actor.permissions(), actor.deniedPermissions()))
                .orElseGet(() -> CampaignActor.none(tenantId, userId));
    }

    private NotificationCampaignEntity lockOwned(CampaignActor actor, String id) {
        return campaigns.lockByTenantIdAndId(actor.tenantId(), id)
                .filter(campaign -> guard.isAdmin(actor) || actor.userId().equals(campaign.getCreatedBy()))
                .orElseThrow(NotificationCampaignQueryService::notFound);
    }

    private static void requireEditable(NotificationCampaignEntity campaign, String message) {
        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new ConflictException(message);
        }
    }

    private static void apply(NotificationCampaignEntity campaign, CampaignDraftRequest request,
            NotificationPriority priority, CampaignAudience audience) {
        campaign.setTitle(request.title().trim());
        campaign.setBody(request.body().trim());
        campaign.setPriority(priority);
        campaign.setAudience(audience);
        campaign.setRequireReadAck(request.requireReadAck());
        campaign.setPushEnabled(request.pushEnabled() == null || request.pushEnabled());
    }

    /** Legacy inbox values ('normal', 'high') stay readable but are never written by campaigns. */
    private static NotificationPriority priority(String value) {
        if (value == null || value.isBlank()) {
            return NotificationPriority.INFO;
        }
        try {
            return NotificationPriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("Mức độ chỉ gồm INFO, IMPORTANT, URGENT");
        }
    }

    private static CampaignAudience validated(CampaignAudience audience) {
        if (audience == null) {
            throw new BadRequestException("Chọn ít nhất một người nhận");
        }
        List<String> roles = audience.roles().stream().map(role -> role.trim().toUpperCase(Locale.ROOT))
                .distinct().toList();
        if (!RECIPIENT_ROLES.containsAll(roles)) {
            throw new BadRequestException("Vai trò nhận chỉ gồm TEACHER, STUDENT, STAFF");
        }
        for (List<String> ids : List.of(audience.schoolIds(), audience.gradeIds(), audience.classIds(),
                audience.groupIds(), audience.userIds())) {
            if (ids.size() > MAX_LIST_SIZE) {
                throw new BadRequestException("Mỗi danh sách người nhận tối đa 5.000 mục");
            }
        }
        CampaignAudience normalized = new CampaignAudience(audience.wholeTenant(), audience.schoolIds(),
                audience.gradeIds(), audience.classIds(), audience.groupIds(), roles, audience.userIds());
        if (!normalized.wholeTenant() && roles.isEmpty() && normalized.schoolIds().isEmpty()
                && normalized.gradeIds().isEmpty() && normalized.classIds().isEmpty()
                && normalized.groupIds().isEmpty() && normalized.userIds().isEmpty()) {
            throw new BadRequestException("Chọn ít nhất một người nhận");
        }
        return normalized;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
