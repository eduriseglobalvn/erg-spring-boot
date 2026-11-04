package com.erg.ergspringboot.crm.internal;

import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.ApproveHandoverRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CostLine;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CreateFollowUpRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CreateHandoverRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CreateOpportunityRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.FollowUpResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.HandoverChecklistItem;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.HandoverResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.LeadResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.OpportunityResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.RevenueLine;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.UpdateFollowUpCompletionRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.UpdateLeadStatusRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.UpdateOpportunityRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.WorkspaceResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.WorkspacePage;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.crm.CrmHandover;
import com.erg.ergspringboot.model.crm.CrmSchoolActivity;
import com.erg.ergspringboot.model.crm.CrmSchoolContact;
import com.erg.ergspringboot.model.crm.CrmSchoolDeal;
import com.erg.ergspringboot.model.crm.CrmSchoolProfile;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.authorization.AuthorizationOperations;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.crm.CrmHandoverRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolActivityRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolContactRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolDealRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolProfileRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRM workspace persistence behind {@code CrmOperations}. */
@Service
public class CrmWorkspaceService {

    static final int MAX_WORKSPACE_SCHOOLS = 500;

    private static final Set<String> LEAD_STATUSES = Set.of(
            "new_lead", "consulted", "waiting_decision", "negotiating", "won_pending_setup", "active", "paused", "lost");
    private static final Set<String> OPPORTUNITY_STAGES = Set.of(
            "discovery", "proposal", "pnl_review", "contracting", "handover", "active");
    private static final Set<String> FOLLOW_UP_TYPES = Set.of("call", "meeting", "proposal", "contract", "handover");
    static final String HANDOVER_APPROVE_PERMISSION = "crm.handover.approve";

    private final EducationUnitRepository unitRepository;
    private final CrmSchoolProfileRepository profileRepository;
    private final CrmSchoolContactRepository contactRepository;
    private final CrmSchoolDealRepository dealRepository;
    private final CrmSchoolActivityRepository activityRepository;
    private final CrmHandoverRepository handoverRepository;
    private final AuthorizationOperations authorization;

    public CrmWorkspaceService(
            EducationUnitRepository unitRepository,
            CrmSchoolProfileRepository profileRepository,
            CrmSchoolContactRepository contactRepository,
            CrmSchoolDealRepository dealRepository,
            CrmSchoolActivityRepository activityRepository,
            CrmHandoverRepository handoverRepository,
            AuthorizationOperations authorization
    ) {
        this.unitRepository = unitRepository;
        this.profileRepository = profileRepository;
        this.contactRepository = contactRepository;
        this.dealRepository = dealRepository;
        this.activityRepository = activityRepository;
        this.handoverRepository = handoverRepository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse workspace(UserAccessContext access) {
        return workspace(access, 0, MAX_WORKSPACE_SCHOOLS);
    }

    /** Pages over the schools in scope; the scope filter runs in the query, before the page is cut. */
    @Transactional(readOnly = true)
    public WorkspaceResponse workspace(UserAccessContext access, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_WORKSPACE_SCHOOLS));
        List<String> scope = safe(access.managedEducationUnitIds());
        Page<EducationUnit> unitPage = scope.contains("*")
                ? unitRepository.findPageByTenantIdOrderByNameAsc(access.tenantId(), request)
                : scope.isEmpty()
                        ? Page.empty(request)
                        : unitRepository.findPageByTenantIdAndIdInOrderByNameAsc(access.tenantId(), scope, request);
        WorkspacePage pageInfo = new WorkspacePage(request.getPageNumber(), request.getPageSize(),
                unitPage.getTotalElements(), unitPage.hasNext());
        List<EducationUnit> units = unitPage.getContent();
        List<String> schoolIds = units.stream().map(EducationUnit::getId).toList();
        if (schoolIds.isEmpty()) {
            return new WorkspaceResponse(List.of(), List.of(), List.of(), List.of(), pageInfo);
        }

        String tenantId = access.tenantId();
        List<CrmSchoolProfile> profiles = profileRepository.findByTenantIdAndSchoolIdIn(tenantId, schoolIds);
        Map<String, EducationUnit> unitsById = index(units, EducationUnit::getId);
        Map<String, CrmSchoolContact> contactsBySchool = primaryContacts(tenantId, schoolIds);
        Map<String, CrmSchoolProfile> profilesBySchool = index(profiles, CrmSchoolProfile::getSchoolId);

        List<LeadResponse> leads = profiles.stream()
                .map(profile -> leadResponse(profile, unitsById.get(profile.getSchoolId()), contactsBySchool.get(profile.getSchoolId())))
                .toList();
        List<OpportunityResponse> opportunities = dealRepository
                .findByTenantIdAndSchoolIdInOrderByCreatedAtDesc(tenantId, schoolIds).stream()
                .map(deal -> opportunityResponse(deal, profilesBySchool.get(deal.getSchoolId())))
                .toList();
        List<FollowUpResponse> followUps = activityRepository
                .findByTenantIdAndSchoolIdInOrderByDueAtAsc(tenantId, schoolIds).stream()
                .map(activity -> followUpResponse(activity, profilesBySchool.get(activity.getSchoolId())))
                .toList();
        List<HandoverResponse> handovers = handoverRepository
                .findByTenantIdAndSchoolIdInOrderByCreatedAtDesc(tenantId, schoolIds).stream()
                .map(this::handoverResponse)
                .toList();
        return new WorkspaceResponse(leads, opportunities, followUps, handovers, pageInfo);
    }

    @Transactional
    public LeadResponse updateLeadStatus(UserAccessContext access, String leadId, UpdateLeadStatusRequest request) {
        CrmSchoolProfile profile = profile(access, leadId);
        EducationUnit unit = requireSchool(access, profile.getSchoolId());
        checkVersion(request.expectedVersion(), profile.getVersion());
        profile.setLeadStatus(enumValue(request.status(), LEAD_STATUSES, "lead status"));
        profile.setLastTouchAt(OffsetDateTime.now());
        CrmSchoolProfile saved = profileRepository.save(profile);
        CrmSchoolContact contact = primaryContacts(access.tenantId(), List.of(profile.getSchoolId()))
                .get(profile.getSchoolId());
        return leadResponse(saved, unit, contact);
    }

    @Transactional
    public OpportunityResponse createOpportunity(
            UserAccessContext access, String idempotencyKey, CreateOpportunityRequest request
    ) {
        CrmSchoolProfile profile = profile(access, request.leadId());
        requireSchool(access, profile.getSchoolId());
        String key = requiredIdempotencyKey(idempotencyKey);
        CrmSchoolDeal existing = dealRepository
                .findByTenantIdAndOwnerUserIdAndIdempotencyKey(access.tenantId(), access.userId(), key).orElse(null);
        if (existing != null) {
            if (!existing.getSchoolId().equals(profile.getSchoolId())) {
                throw new ConflictException("Idempotency key was already used for another CRM opportunity");
            }
            return opportunityResponse(existing, profile);
        }

        CrmSchoolDeal deal = new CrmSchoolDeal();
        deal.setTenantId(access.tenantId());
        deal.setSchoolId(profile.getSchoolId());
        deal.setOwnerUserId(access.userId());
        deal.setIdempotencyKey(key);
        applyOpportunity(deal, request.stage(), request.code(), request.academicYear(), request.contractTerm(),
                request.startMonth(), request.revenueLines(), request.costLines());
        return opportunityResponse(dealRepository.save(deal), profile);
    }

    @Transactional
    public OpportunityResponse updateOpportunity(
            UserAccessContext access, String opportunityId, UpdateOpportunityRequest request
    ) {
        CrmSchoolDeal deal = dealRepository.findByIdAndTenantId(opportunityId, access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("CRM opportunity not found"));
        requireSchool(access, deal.getSchoolId());
        checkVersion(request.expectedVersion(), deal.getVersion());
        CrmSchoolProfile profile = profileRepository.findFirstByTenantIdAndSchoolId(access.tenantId(), deal.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("CRM lead not found"));
        applyOpportunity(deal, request.stage(), deal.getCode(), request.academicYear(), request.contractTerm(),
                request.startMonth(), request.revenueLines(), request.costLines());
        return opportunityResponse(dealRepository.save(deal), profile);
    }

    @Transactional
    public FollowUpResponse createFollowUp(
            UserAccessContext access, String idempotencyKey, CreateFollowUpRequest request
    ) {
        CrmSchoolProfile profile = profile(access, request.leadId());
        requireSchool(access, profile.getSchoolId());
        String key = requiredIdempotencyKey(idempotencyKey);
        CrmSchoolActivity existing = activityRepository
                .findByTenantIdAndActorUserIdAndIdempotencyKey(access.tenantId(), access.userId(), key).orElse(null);
        if (existing != null) {
            if (!existing.getSchoolId().equals(profile.getSchoolId())) {
                throw new ConflictException("Idempotency key was already used for another CRM follow-up");
            }
            return followUpResponse(existing, profile);
        }

        if (request.opportunityId() != null && !request.opportunityId().isBlank()) {
            CrmSchoolDeal deal = dealRepository.findByIdAndTenantId(request.opportunityId(), access.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("CRM opportunity not found"));
            if (!deal.getSchoolId().equals(profile.getSchoolId())) {
                throw new BadRequestException("CRM opportunity does not belong to the lead");
            }
        }
        CrmSchoolActivity activity = new CrmSchoolActivity();
        activity.setTenantId(access.tenantId());
        activity.setSchoolId(profile.getSchoolId());
        activity.setDealId(blankToNull(request.opportunityId()));
        activity.setActorUserId(access.userId());
        activity.setType(enumValue(request.type(), FOLLOW_UP_TYPES, "follow-up type"));
        activity.setTitle(request.title().trim());
        activity.setContent(request.title().trim());
        activity.setDueAt(request.dueAt());
        activity.setDone(false);
        activity.setIdempotencyKey(key);
        return followUpResponse(activityRepository.save(activity), profile);
    }

    @Transactional
    public FollowUpResponse updateFollowUpCompletion(
            UserAccessContext access, String followUpId, UpdateFollowUpCompletionRequest request
    ) {
        CrmSchoolActivity activity = activityRepository.findByIdAndTenantId(followUpId, access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("CRM follow-up not found"));
        requireSchool(access, activity.getSchoolId());
        checkVersion(request.expectedVersion(), activity.getVersion());
        activity.setDone(request.done());
        CrmSchoolProfile profile = profileRepository.findFirstByTenantIdAndSchoolId(access.tenantId(), activity.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("CRM lead not found"));
        return followUpResponse(activityRepository.save(activity), profile);
    }

    @Transactional
    public HandoverResponse createHandover(
            UserAccessContext access, String idempotencyKey, CreateHandoverRequest request
    ) {
        CrmSchoolProfile profile = profile(access, request.leadId());
        requireSchool(access, profile.getSchoolId());
        CrmSchoolDeal deal = dealRepository.findByIdAndTenantId(request.opportunityId(), access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("CRM opportunity not found"));
        if (!deal.getSchoolId().equals(profile.getSchoolId())) {
            throw new BadRequestException("CRM opportunity does not belong to the lead");
        }
        String key = requiredIdempotencyKey(idempotencyKey);
        CrmHandover existing = handoverRepository
                .findByTenantIdAndRequestedByAndIdempotencyKey(access.tenantId(), access.userId(), key).orElse(null);
        if (existing != null) {
            if (!existing.getSchoolId().equals(profile.getSchoolId())) {
                throw new ConflictException("Idempotency key was already used for another CRM handover");
            }
            return handoverResponse(existing);
        }

        CrmHandover handover = new CrmHandover();
        handover.setTenantId(access.tenantId());
        handover.setSchoolId(profile.getSchoolId());
        handover.setLeadId(profile.getId());
        handover.setOpportunityId(deal.getId());
        handover.setRequestedBy(access.userId());
        List<HandoverChecklistItem> checklist =
                verifiedChecklist(access.tenantId(), profile.getSchoolId(), request.checklist());
        handover.setStatus(isReady(checklist) ? "ready" : "draft");
        handover.setChecklist(checklist.stream().map(this::checklistRow).toList());
        handover.setIdempotencyKey(key);
        return handoverResponse(handoverRepository.save(handover));
    }

    @Transactional
    public HandoverResponse approveHandover(
            UserAccessContext access, String handoverId, ApproveHandoverRequest request
    ) {
        CrmHandover handover = handoverRepository.findByIdAndTenantId(handoverId, access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("CRM handover not found"));
        requireSchool(access, handover.getSchoolId());
        if (!currentPrincipalHas(access, HANDOVER_APPROVE_PERMISSION)) {
            throw new ForbiddenException("missing permission " + HANDOVER_APPROVE_PERMISSION);
        }
        if (access.userId().equals(handover.getRequestedBy())) {
            throw new ForbiddenException("CRM handover requester cannot approve their own handover");
        }
        checkVersion(request.expectedVersion(), handover.getVersion());
        if (!"ready".equals(handover.getStatus())) {
            throw new ConflictException("Only ready CRM handovers can be approved");
        }
        List<HandoverChecklistItem> checklist = verifiedChecklist(
                access.tenantId(), handover.getSchoolId(), checklistItems(handover.getChecklist()));
        if (!isReady(checklist)) {
            throw new BadRequestException("CRM handover checklist is not ready for approval");
        }
        handover.setChecklist(checklist.stream().map(this::checklistRow).toList());
        handover.setStatus("approved");
        CrmSchoolProfile profile = profile(access, handover.getLeadId());
        profile.setLeadStatus("active");
        profile.setLastTouchAt(OffsetDateTime.now());
        profileRepository.save(profile);
        CrmSchoolDeal deal = dealRepository.findByIdAndTenantId(handover.getOpportunityId(), access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("CRM opportunity not found"));
        deal.setStage("active");
        dealRepository.save(deal);
        return handoverResponse(handoverRepository.save(handover));
    }

    private CrmSchoolProfile profile(UserAccessContext access, String id) {
        return profileRepository.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("CRM lead not found"));
    }

    private EducationUnit requireSchool(UserAccessContext access, String schoolId) {
        EducationUnit unit = unitRepository.findByIdAndTenantId(schoolId, access.tenantId())
                .orElseThrow(() -> new ForbiddenException("CRM school is outside the authenticated tenant"));
        if (!access.canAccessEducationUnit(schoolId)) {
            throw new ForbiddenException("CRM school is outside the authenticated education-unit scope");
        }
        return unit;
    }

    private void applyOpportunity(
            CrmSchoolDeal deal, String stage, String code, String academicYear, String contractTerm, String startMonth,
            List<RevenueLine> revenues, List<CostLine> costs
    ) {
        deal.setStage(enumValue(stage, OPPORTUNITY_STAGES, "opportunity stage"));
        deal.setCode(code.trim());
        deal.setAcademicYear(academicYear.trim());
        deal.setContractTerm(contractTerm.trim());
        deal.setStartMonth(startMonth.trim());
        deal.setRevenueLines(revenues.stream().map(this::revenueRow).toList());
        deal.setCostLines(costs.stream().map(this::costRow).toList());
        deal.setExpectedRevenue(revenues.stream().map(this::netRevenue).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal netRevenue(RevenueLine line) {
        BigDecimal gross = line.pricePerStudent()
                .multiply(BigDecimal.valueOf(line.classCount()))
                .multiply(BigDecimal.valueOf(line.studentsPerClass()))
                .multiply(BigDecimal.valueOf(line.durationMonths()));
        return gross.multiply(BigDecimal.ONE.subtract(line.discountRate()));
    }

    private LeadResponse leadResponse(CrmSchoolProfile profile, EducationUnit unit, CrmSchoolContact contact) {
        return new LeadResponse(
                profile.getId(), profile.getSchoolId(), unit == null ? null : unit.getName(), profile.getDistrict(),
                profile.getProvince(), profile.getSegment(), contact == null ? null : contact.getFullName(),
                contact == null ? null : firstNonBlank(contact.getPosition(), contact.getContactType()),
                contact == null ? null : contact.getPhone(), profile.getSource(),
                firstNonBlank(profile.getLeadOwnerUserId(), profile.getOwnerUserId()), profile.getLeadStatus(),
                value(profile.getProbability()), value(profile.getExpectedStudents()), value(profile.getExpectedClasses()),
                profile.getNextFollowUpAt(), profile.getLastTouchAt(), profile.getNote(), safe(profile.getRisks()), profile.getVersion()
        );
    }

    private OpportunityResponse opportunityResponse(CrmSchoolDeal deal, CrmSchoolProfile profile) {
        return new OpportunityResponse(
                deal.getId(), profile == null ? null : profile.getId(), deal.getCode(), deal.getStage(),
                deal.getAcademicYear(), deal.getContractTerm(), deal.getStartMonth(), deal.getOwnerUserId(),
                revenueLines(deal.getRevenueLines()), costLines(deal.getCostLines()), deal.getVersion()
        );
    }

    private FollowUpResponse followUpResponse(CrmSchoolActivity activity, CrmSchoolProfile profile) {
        return new FollowUpResponse(
                activity.getId(), profile == null ? null : profile.getId(), activity.getType(),
                firstNonBlank(activity.getTitle(), activity.getContent()), activity.getDueAt(), activity.getActorUserId(),
                Boolean.TRUE.equals(activity.getDone()), activity.getVersion()
        );
    }

    private HandoverResponse handoverResponse(CrmHandover handover) {
        return new HandoverResponse(
                handover.getId(), handover.getLeadId(), handover.getOpportunityId(), handover.getRequestedBy(),
                handover.getCreatedAt(), handover.getStatus(), checklistItems(handover.getChecklist()), handover.getVersion()
        );
    }

    private Map<String, CrmSchoolContact> primaryContacts(String tenantId, Collection<String> schoolIds) {
        Map<String, CrmSchoolContact> contacts = new LinkedHashMap<>();
        for (CrmSchoolContact contact : contactRepository
                .findByTenantIdAndSchoolIdInOrderByIsPrimaryDescCreatedAtAsc(tenantId, schoolIds)) {
            contacts.putIfAbsent(contact.getSchoolId(), contact);
        }
        return contacts;
    }

    private CrmSchoolDeal.RevenueLine revenueRow(RevenueLine line) {
        return new CrmSchoolDeal.RevenueLine(line.id(), line.program(), line.packageName(), line.classCount(),
                line.studentsPerClass(), line.pricePerStudent(), line.discountRate(), line.durationMonths());
    }

    private CrmSchoolDeal.CostLine costRow(CostLine line) {
        return new CrmSchoolDeal.CostLine(line.id(), line.category(), line.description(), line.quantity(),
                line.unitCost(), line.durationMonths(), line.requiredForHandover());
    }

    private CrmSchoolDeal.ChecklistItem checklistRow(HandoverChecklistItem item) {
        return new CrmSchoolDeal.ChecklistItem(item.id(), item.label(), item.done());
    }

    private List<RevenueLine> revenueLines(List<CrmSchoolDeal.RevenueLine> rows) {
        return safe(rows).stream().map(row -> new RevenueLine(
                text(row.id()), text(row.program()), text(row.packageName()), value(row.classCount()),
                value(row.studentsPerClass()), amount(row.pricePerStudent()), amount(row.discountRate()),
                value(row.durationMonths()))).toList();
    }

    private List<CostLine> costLines(List<CrmSchoolDeal.CostLine> rows) {
        return safe(rows).stream().map(row -> new CostLine(
                text(row.id()), text(row.category()), text(row.description()), value(row.quantity()),
                amount(row.unitCost()), value(row.durationMonths()), Boolean.TRUE.equals(row.requiredForHandover())))
                .toList();
    }

    private List<HandoverChecklistItem> checklistItems(List<CrmSchoolDeal.ChecklistItem> rows) {
        return safe(rows).stream()
                .map(row -> new HandoverChecklistItem(text(row.id()), text(row.label()), Boolean.TRUE.equals(row.done())))
                .toList();
    }

    private boolean isReady(List<HandoverChecklistItem> checklist) {
        if (checklist == null || checklist.isEmpty()) {
            return false;
        }
        return checklist.stream().allMatch(HandoverChecklistItem::done);
    }

    /**
     * Client-declared {@code done} flags are not trusted where the server holds the evidence:
     * a checklist item whose id names a follow-up of the same school takes that follow-up's
     * completion state.
     */
    private List<HandoverChecklistItem> verifiedChecklist(
            String tenantId, String schoolId, List<HandoverChecklistItem> checklist
    ) {
        List<HandoverChecklistItem> items = safe(checklist);
        List<String> ids = items.stream()
                .map(HandoverChecklistItem::id)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (ids.isEmpty()) {
            return items;
        }
        Map<String, Boolean> followUps = activityRepository.findByTenantIdAndSchoolIdAndIdIn(tenantId, schoolId, ids)
                .stream()
                .collect(Collectors.toMap(CrmSchoolActivity::getId,
                        activity -> Boolean.TRUE.equals(activity.getDone()), (left, right) -> left));
        return items.stream()
                .map(item -> followUps.containsKey(item.id())
                        ? new HandoverChecklistItem(item.id(), item.label(), followUps.get(item.id()))
                        : item)
                .toList();
    }

    private boolean currentPrincipalHas(UserAccessContext access, String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof ErgPrincipal principal
                && access.userId().equals(principal.userId())
                && authorization.isAllowed(principal, permission, null, access.tenantId());
    }

    private void checkVersion(Long expected, long actual) {
        if (expected == null || expected.longValue() != actual) {
            throw new ConflictException("CRM record changed; reload before saving");
        }
    }

    private String requiredIdempotencyKey(String value) {
        String key = blankToNull(value);
        if (key == null) {
            throw new BadRequestException("X-Idempotency-Key is required for CRM create commands");
        }
        if (key.length() > 192) {
            throw new BadRequestException("X-Idempotency-Key must not exceed 192 characters");
        }
        return key;
    }

    private String enumValue(String value, Set<String> allowed, String label) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BadRequestException("Unsupported CRM " + label + ": " + value);
        }
        return normalized;
    }

    private <T> Map<String, T> index(Collection<T> values, Function<T, String> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
