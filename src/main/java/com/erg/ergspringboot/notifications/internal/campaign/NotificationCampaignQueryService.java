package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.NotificationCampaignResponses.CampaignRecipientResponse;
import com.erg.ergspringboot.notifications.NotificationCampaignResponses.CampaignResponse;
import com.erg.ergspringboot.notifications.NotificationCampaignResponses.CampaignSummaryResponse;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.notifications.persistence.projection.CampaignDeliveryCounts;
import com.erg.ergspringboot.notifications.persistence.projection.CampaignRecipientRow;
import com.erg.ergspringboot.notifications.persistence.projection.CountPair;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of campaigns: boxes, detail with delivery counts, recipients and the W1 KPIs. */
@Service
public class NotificationCampaignQueryService {

    /** Months for "sent this month" start at local midnight in Vietnam, not UTC. */
    static final ZoneId CAMPAIGN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final CampaignDeliveryCounts NONE = new CampaignDeliveryCounts(null, 0L, 0L, 0L);

    private final NotificationCampaignRepository campaigns;
    private final NotificationMessageRepository messages;
    private final AudienceScopeGuard guard;
    private final UserDirectoryPort users;
    private final Clock clock;

    @Autowired
    public NotificationCampaignQueryService(NotificationCampaignRepository campaigns,
            NotificationMessageRepository messages, AudienceScopeGuard guard, UserDirectoryPort users) {
        this(campaigns, messages, guard, users, Clock.systemUTC());
    }

    NotificationCampaignQueryService(NotificationCampaignRepository campaigns, NotificationMessageRepository messages,
            AudienceScopeGuard guard, UserDirectoryPort users, Clock clock) {
        this.campaigns = campaigns;
        this.messages = messages;
        this.guard = guard;
        this.users = users;
        this.clock = clock;
    }

    /** Non-admins only ever see their own campaigns, whatever {@code mine} says. */
    @Transactional(readOnly = true)
    public PageResponse<CampaignResponse> list(CampaignActor actor, String box, boolean mine, int page, int size) {
        String normalizedBox = box == null || box.isBlank() ? "SENT" : box.trim().toUpperCase(java.util.Locale.ROOT);
        List<CampaignStatus> statuses;
        Sort sort;
        switch (normalizedBox) {
            case "SENT" -> {
                statuses = List.of(CampaignStatus.SENDING, CampaignStatus.SENT, CampaignStatus.FAILED);
                sort = Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("updatedAt"));
            }
            case "SCHEDULED" -> {
                statuses = List.of(CampaignStatus.SCHEDULED);
                sort = Sort.by(Sort.Order.asc("scheduledAt"));
            }
            case "DRAFT" -> {
                statuses = List.of(CampaignStatus.DRAFT);
                sort = Sort.by(Sort.Order.desc("updatedAt"));
            }
            default -> throw new BadRequestException("box chỉ gồm SENT, SCHEDULED, DRAFT");
        }
        String createdBy = guard.isAdmin(actor.roles()) && !mine ? null : actor.userId();
        Page<NotificationCampaignEntity> result = campaigns.findPage(actor.tenantId(), statuses, createdBy,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), sort));
        Map<String, CampaignDeliveryCounts> counts = counts(actor.tenantId(),
                result.getContent().stream().map(NotificationCampaignEntity::getId).toList());
        List<CampaignResponse> items = result.getContent().stream()
                .map(campaign -> toResponse(campaign, counts.getOrDefault(campaign.getId(), NONE)))
                .toList();
        return page(result, items);
    }

    @Transactional(readOnly = true)
    public CampaignResponse detail(CampaignActor actor, String id) {
        return toResponse(owned(actor, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CampaignRecipientResponse> recipients(
            CampaignActor actor, String id, String filter, int page, int size) {
        String normalized = filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("ALL", "UNREAD", "UNACKED").contains(normalized)) {
            throw new BadRequestException("filter chỉ gồm ALL, UNREAD, UNACKED");
        }
        NotificationCampaignEntity campaign = owned(actor, id);
        Page<CampaignRecipientRow> rows = messages.findCampaignRecipients(actor.tenantId(), campaign.getId(),
                "UNREAD".equals(normalized), "UNACKED".equals(normalized),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));
        Map<String, String> names = users.findByTenantIdAndIds(actor.tenantId(),
                        rows.getContent().stream().map(CampaignRecipientRow::userId).toList()).stream()
                .collect(Collectors.toMap(UserAccountProjection::id, UserAccountProjection::displayName, (a, b) -> a));
        List<CampaignRecipientResponse> items = rows.getContent().stream()
                .map(row -> new CampaignRecipientResponse(row.userId(), names.getOrDefault(row.userId(), row.userId()),
                        row.readAt(), row.ackedAt()))
                .toList();
        return page(rows, items);
    }

    @Transactional(readOnly = true)
    public CampaignSummaryResponse summary(CampaignActor actor) {
        String createdBy = guard.isAdmin(actor.roles()) ? null : actor.userId();
        OffsetDateTime monthStart = LocalDate.now(clock.withZone(CAMPAIGN_ZONE)).withDayOfMonth(1)
                .atStartOfDay(CAMPAIGN_ZONE).toOffsetDateTime();
        long sent = campaigns.countSentSince(actor.tenantId(), monthStart, createdBy);
        CountPair viewed = messages.viewedSince(actor.tenantId(), monthStart, createdBy);
        CountPair unacked = messages.unacked(actor.tenantId(), createdBy);
        double rate = viewed.firstOrZero() == 0 ? 0 : (double) viewed.secondOrZero() / viewed.firstOrZero();
        return new CampaignSummaryResponse(sent, rate, unacked.firstOrZero(), unacked.secondOrZero());
    }

    /** Admins reach every campaign in the tenant; anyone else only their own (404, not 403, to hide existence). */
    NotificationCampaignEntity owned(CampaignActor actor, String id) {
        return campaigns.findByTenantIdAndId(actor.tenantId(), id)
                .filter(campaign -> canAccess(actor, campaign))
                .orElseThrow(NotificationCampaignQueryService::notFound);
    }

    static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy thông báo");
    }

    boolean canAccess(CampaignActor actor, NotificationCampaignEntity campaign) {
        return guard.isAdmin(actor.roles()) || actor.userId().equals(campaign.getCreatedBy());
    }

    public CampaignResponse toResponse(NotificationCampaignEntity campaign) {
        return toResponse(campaign, counts(campaign.getTenantId(), List.of(campaign.getId())).getOrDefault(campaign.getId(), NONE));
    }

    private Map<String, CampaignDeliveryCounts> counts(String tenantId, List<String> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        return messages.countByCampaigns(tenantId, campaignIds).stream()
                .collect(Collectors.toMap(CampaignDeliveryCounts::campaignId, Function.identity()));
    }

    private static CampaignResponse toResponse(NotificationCampaignEntity campaign, CampaignDeliveryCounts counts) {
        return new CampaignResponse(campaign.getId(), campaign.getTitle(), campaign.getBody(), campaign.getPriority(),
                campaign.getAudience(), campaign.getStatus(), campaign.getSenderRole(), campaign.getCreatedBy(),
                campaign.isRequireReadAck(), campaign.isPushEnabled(), campaign.getEstimatedCount(),
                campaign.getResolvedCount(), campaign.getScheduledAt(), campaign.getSentAt(), campaign.getCreatedAt(),
                campaign.getUpdatedAt(), zero(counts.recipients()), zero(counts.viewed()), zero(counts.acked()));
    }

    private static long zero(Long value) {
        return value == null ? 0 : value;
    }

    private static <T> PageResponse<T> page(Page<?> source, List<T> items) {
        return new PageResponse<>(items, source.getNumber(), source.getSize(), source.getTotalElements(),
                source.getTotalPages(), source.hasNext(), source.hasPrevious());
    }
}
