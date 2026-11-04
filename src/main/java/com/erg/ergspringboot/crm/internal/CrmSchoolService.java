package com.erg.ergspringboot.crm.internal;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolPerformanceResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolSummaryResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.SchoolContactResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.SchoolReferrerResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.crm.persistence.CrmSchoolContact;
import com.erg.ergspringboot.crm.persistence.CrmSchoolDeal;
import com.erg.ergspringboot.crm.persistence.CrmSchoolProfile;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.crm.persistence.CrmSchoolContactRepository;
import com.erg.ergspringboot.crm.persistence.CrmSchoolDealRepository;
import com.erg.ergspringboot.crm.persistence.CrmSchoolDealRepository.DealPerformanceAggregate;
import com.erg.ergspringboot.crm.persistence.CrmSchoolProfileRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRM reporting reads behind {@code CrmOperations}. */
@Service
public class CrmSchoolService {

    static final String ANALYTICS_PERMISSION = "crm.analytics.read";
    private static final String SIGNED = "signed";
    private static final String LOST = "lost";

    private final EducationUnitRepository educationUnitRepository;
    private final CrmSchoolProfileRepository profileRepository;
    private final CrmSchoolDealRepository dealRepository;
    private final CrmSchoolContactRepository contactRepository;
    private final AuthorizationOperations authorization;

    public CrmSchoolService(
            EducationUnitRepository educationUnitRepository,
            CrmSchoolProfileRepository profileRepository,
            CrmSchoolDealRepository dealRepository,
            CrmSchoolContactRepository contactRepository,
            AuthorizationOperations authorization
    ) {
        this.educationUnitRepository = educationUnitRepository;
        this.profileRepository = profileRepository;
        this.dealRepository = dealRepository;
        this.contactRepository = contactRepository;
        this.authorization = authorization;
    }

    /**
     * Lists schools from the SEO's pipeline. Reading another SEO's pipeline needs
     * {@code crm.analytics.read}; results are always restricted to the caller's tenant
     * and education-unit scope.
     */
    @Transactional(readOnly = true)
    public List<CrmSchoolSummaryResponse> schoolsBySeo(UserAccessContext access, String seoUserId, boolean signedOnly) {
        requireSeoAccess(access, seoUserId);
        String tenantId = access.tenantId();
        List<CrmSchoolDeal> deals = (signedOnly
                ? dealRepository.findByTenantIdAndOwnerUserIdAndStageIgnoreCaseOrderBySignedAtDesc(tenantId, seoUserId, SIGNED)
                : dealRepository.findByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(tenantId, seoUserId))
                .stream()
                .filter(deal -> access.canAccessEducationUnit(deal.getSchoolId()))
                .toList();
        List<String> schoolIds = deals.stream().map(CrmSchoolDeal::getSchoolId).distinct().toList();
        if (schoolIds.isEmpty()) {
            return List.of();
        }
        Map<String, EducationUnit> educationUnits = byId(
                educationUnitRepository.findByTenantIdAndIdInOrderByNameAsc(tenantId, schoolIds, PageRequest.of(0, schoolIds.size())),
                EducationUnit::getId);
        Map<String, CrmSchoolProfile> profiles = byId(
                profileRepository.findByTenantIdAndSchoolIdIn(tenantId, schoolIds), CrmSchoolProfile::getSchoolId);
        Map<String, CrmSchoolContact> primaryContacts = primaryContacts(tenantId, schoolIds);

        return deals.stream()
                .filter(deal -> educationUnits.containsKey(deal.getSchoolId()))
                .map(deal -> summary(deal, educationUnits.get(deal.getSchoolId()), profiles.get(deal.getSchoolId()), primaryContacts.get(deal.getSchoolId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CrmSchoolPerformanceResponse performance(
            UserAccessContext access, String seoUserId, OffsetDateTime from, OffsetDateTime to
    ) {
        requireSeoAccess(access, seoUserId);
        String tenantId = access.tenantId();
        boolean tenantWideScope = access.managedEducationUnitIds().contains("*");
        DealPerformanceAggregate aggregate = tenantWideScope
                ? dealRepository.aggregatePerformance(tenantId, seoUserId, from, to)
                : null;
        if (aggregate != null) {
            long total = value(aggregate.getTotalCount());
            long signed = value(aggregate.getSignedCount());
            long lost = value(aggregate.getLostCount());
            long inProgress = Math.max(0, total - signed - lost);
            BigDecimal revenue = aggregate.getSignedRevenue() == null ? BigDecimal.ZERO : aggregate.getSignedRevenue();
            BigDecimal conversion = total == 0
                    ? BigDecimal.ZERO.setScale(2)
                    : BigDecimal.valueOf(signed).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            BigDecimal average = signed == 0
                    ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(signed), 2, RoundingMode.HALF_UP).stripTrailingZeros();
            return new CrmSchoolPerformanceResponse(total, signed, lost, inProgress, conversion, revenue, average);
        }
        List<CrmSchoolDeal> deals = dealRepository.findByTenantIdAndOwnerUserIdAndCreatedAtBetween(tenantId, seoUserId, from, to)
                .stream()
                .filter(deal -> access.canAccessEducationUnit(deal.getSchoolId()))
                .toList();
        long total = deals.size();
        long signed = deals.stream().filter(deal -> stageIs(deal, SIGNED)).count();
        long lost = deals.stream().filter(deal -> stageIs(deal, LOST)).count();
        long inProgress = Math.max(0, total - signed - lost);
        BigDecimal revenue = deals.stream()
                .filter(deal -> stageIs(deal, SIGNED))
                .map(CrmSchoolDeal::getSignedRevenue)
                .map(value -> value == null ? BigDecimal.ZERO : value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal conversion = total == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(signed).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        BigDecimal average = signed == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(signed), 2, RoundingMode.HALF_UP).stripTrailingZeros();

        return new CrmSchoolPerformanceResponse(total, signed, lost, inProgress, conversion, revenue, average);
    }

    private void requireSeoAccess(UserAccessContext access, String seoUserId) {
        if (access.userId().equals(seoUserId)) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null
                && authentication.getPrincipal() instanceof ErgPrincipal principal
                && access.userId().equals(principal.userId())
                && authorization.isAllowed(principal, ANALYTICS_PERMISSION, null, access.tenantId());
        if (!allowed) {
            throw new ForbiddenException("missing permission " + ANALYTICS_PERMISSION + " to read another SEO pipeline");
        }
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private CrmSchoolSummaryResponse summary(CrmSchoolDeal deal, EducationUnit school, CrmSchoolProfile profile, CrmSchoolContact contact) {
        return new CrmSchoolSummaryResponse(
                deal.getSchoolId(),
                school == null ? null : school.getName(),
                school == null ? deal.getStage() : school.getStatus(),
                deal.getSignedAt(),
                deal.getSignedRevenue() == null ? BigDecimal.ZERO : deal.getSignedRevenue(),
                contactResponse(contact),
                referrerResponse(profile)
        );
    }

    private SchoolContactResponse contactResponse(CrmSchoolContact contact) {
        if (contact == null) {
            return null;
        }
        return new SchoolContactResponse(
                contact.getId(),
                contact.getFullName(),
                firstNonBlank(contact.getPosition(), contact.getContactType()),
                contact.getPhone(),
                contact.getEmail(),
                Boolean.TRUE.equals(contact.getIsPrimary())
        );
    }

    private SchoolReferrerResponse referrerResponse(CrmSchoolProfile profile) {
        if (profile == null) {
            return null;
        }
        return new SchoolReferrerResponse(
                profile.getIntroducedByUserId(),
                profile.getIntroducedByName(),
                profile.getIntroducedByPhone(),
                profile.getIntroducedByNote()
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

    private <T> Map<String, T> byId(Collection<T> items, Function<T, String> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private boolean stageIs(CrmSchoolDeal deal, String stage) {
        return deal.getStage() != null && deal.getStage().trim().toLowerCase(Locale.ROOT).equals(stage);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
