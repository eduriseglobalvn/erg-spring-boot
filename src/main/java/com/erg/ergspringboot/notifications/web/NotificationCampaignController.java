package com.erg.ergspringboot.notifications.web;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.ResolvePeopleRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.notifications.NotificationCampaignRequests.CampaignDraftRequest;
import com.erg.ergspringboot.notifications.NotificationCampaignRequests.ScheduleCampaignRequest;
import com.erg.ergspringboot.notifications.NotificationCampaignResponses.CampaignRecipientResponse;
import com.erg.ergspringboot.notifications.NotificationCampaignResponses.CampaignResponse;
import com.erg.ergspringboot.notifications.NotificationCampaignResponses.CampaignSummaryResponse;
import com.erg.ergspringboot.notifications.NotificationSecuritySupport;
import com.erg.ergspringboot.notifications.RecipientLookupResponses.RecipientCard;
import com.erg.ergspringboot.notifications.RecipientLookupResponses.ResolvePeopleResult;
import com.erg.ergspringboot.notifications.authorization.NotificationCampaignSend;
import com.erg.ergspringboot.notifications.internal.campaign.AudienceBreakdown;
import com.erg.ergspringboot.notifications.internal.campaign.CampaignActor;
import com.erg.ergspringboot.notifications.internal.campaign.NotificationCampaignQueryService;
import com.erg.ergspringboot.notifications.internal.campaign.NotificationCampaignService;
import com.erg.ergspringboot.notifications.internal.campaign.RecipientLookupService;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Module-owned REST adapter for notification campaigns (compose, estimate, send, schedule, sent box). */
@RestController
@RequestMapping("/api/v1/notification-campaigns")
public class NotificationCampaignController {

    private static final int PEOPLE_SEARCH_LIMIT = 20;

    private final NotificationCampaignService campaigns;
    private final NotificationCampaignQueryService queries;
    private final RecipientLookupService people;
    private final NotificationSecuritySupport security;

    public NotificationCampaignController(NotificationCampaignService campaigns, NotificationCampaignQueryService queries,
            RecipientLookupService people, NotificationSecuritySupport security) {
        this.campaigns = campaigns;
        this.queries = queries;
        this.people = people;
        this.security = security;
    }

    @NotificationCampaignSend
    @PostMapping
    public ApiResponse<CampaignResponse> createCampaign(@Valid @RequestBody CampaignDraftRequest body, HttpServletRequest request) {
        return ok("Campaign draft saved", campaigns.create(actor(), body), request);
    }

    @NotificationCampaignSend
    @PutMapping("/{id}")
    public ApiResponse<CampaignResponse> updateCampaign(
            @PathVariable String id, @Valid @RequestBody CampaignDraftRequest body, HttpServletRequest request) {
        return ok("Campaign updated", campaigns.update(actor(), id, body), request);
    }

    @NotificationCampaignSend
    @PostMapping("/{id}/send")
    public ApiResponse<CampaignResponse> sendCampaign(@PathVariable String id, HttpServletRequest request) {
        return ok("Campaign sent", campaigns.send(actor(), id), request);
    }

    @NotificationCampaignSend
    @PostMapping("/{id}/schedule")
    public ApiResponse<CampaignResponse> scheduleCampaign(
            @PathVariable String id, @Valid @RequestBody ScheduleCampaignRequest body, HttpServletRequest request) {
        return ok("Campaign scheduled", campaigns.schedule(actor(), id, body.scheduledAt()), request);
    }

    @NotificationCampaignSend
    @PostMapping("/{id}/cancel")
    public ApiResponse<CampaignResponse> cancelCampaign(@PathVariable String id, HttpServletRequest request) {
        return ok("Campaign cancelled", campaigns.cancel(actor(), id), request);
    }

    @NotificationCampaignSend
    @GetMapping
    public ApiResponse<PageResponse<CampaignResponse>> listCampaigns(
            @RequestParam(required = false) String box,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ok("Campaigns loaded", queries.list(actor(), box, mine, page, size), request);
    }

    @NotificationCampaignSend
    @GetMapping("/{id}")
    public ApiResponse<CampaignResponse> getCampaign(@PathVariable String id, HttpServletRequest request) {
        return ok("Campaign loaded", queries.detail(actor(), id), request);
    }

    @NotificationCampaignSend
    @GetMapping("/{id}/recipients")
    public ApiResponse<PageResponse<CampaignRecipientResponse>> listCampaignRecipients(
            @PathVariable String id,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ok("Campaign recipients loaded", queries.recipients(actor(), id, filter, page, size), request);
    }

    @NotificationCampaignSend
    @PostMapping("/estimate")
    public ApiResponse<AudienceBreakdown> estimateAudience(@RequestBody CampaignAudience body, HttpServletRequest request) {
        return ok("Audience estimated", campaigns.estimate(actor(), body), request);
    }

    @NotificationCampaignSend
    @GetMapping("/people")
    public ApiResponse<List<RecipientCard>> searchPeople(
            @RequestParam(required = false) String q, HttpServletRequest request) {
        CampaignActor actor = actor();
        return ok("People found",
                people.search(actor.tenantId(), actor.userId(), List.copyOf(actor.roles()), q, PEOPLE_SEARCH_LIMIT),
                request);
    }

    @NotificationCampaignSend
    @PostMapping("/people/resolve")
    public ApiResponse<ResolvePeopleResult> resolvePeople(
            @Valid @RequestBody ResolvePeopleRequest body, HttpServletRequest request) {
        CampaignActor actor = actor();
        return ok("People resolved",
                people.resolveLines(actor.tenantId(), actor.userId(), List.copyOf(actor.roles()), body.lines()),
                request);
    }

    @NotificationCampaignSend
    @GetMapping("/summary")
    public ApiResponse<CampaignSummaryResponse> campaignSummary(HttpServletRequest request) {
        return ok("Campaign summary loaded", queries.summary(actor()), request);
    }

    private CampaignActor actor() {
        return CampaignActor.of(security.principal());
    }

    private static <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), RequestCorrelation.current(request));
    }
}
