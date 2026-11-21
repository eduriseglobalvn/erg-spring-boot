package com.erg.ergspringboot.audit.internal;

import com.erg.ergspringboot.audit.persistence.AuditEvent;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nightly re-hash of every tenant's audit chain; a break is logged at ERROR and counted. */
@Service
@ConditionalOnProperty(name = "erg.audit.chain-verify.enabled", havingValue = "true", matchIfMissing = true)
public class AuditChainVerifyJob {
    static final String JOB_NAME = "audit-chain-verify";
    private static final Logger log = LoggerFactory.getLogger(AuditChainVerifyJob.class);
    private static final int PAGE = 1000;

    private final EntityManager entityManager;
    private final AuditHashChain hashChain;
    private final ClusterJobLock jobLock;
    private final Optional<MeterRegistry> meters;

    @Autowired
    public AuditChainVerifyJob(EntityManager entityManager, AuditHashChain hashChain,
            ObjectProvider<ClusterJobLock> jobLock, ObjectProvider<MeterRegistry> meters) {
        this(entityManager, hashChain, jobLock.getIfAvailable(), Optional.ofNullable(meters.getIfAvailable()));
    }

    AuditChainVerifyJob(EntityManager entityManager, AuditHashChain hashChain, ClusterJobLock jobLock,
            Optional<MeterRegistry> meters) {
        this.entityManager = entityManager;
        this.hashChain = hashChain;
        this.jobLock = jobLock;
        this.meters = meters;
    }

    @Scheduled(cron = "${erg.audit.chain-verify.cron:0 30 2 * * *}")
    public void runScheduled() {
        if (jobLock == null) verifyAll();
        else jobLock.runExclusively(JOB_NAME, this::verifyAll);
    }

    /** @return number of broken tenants. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public int verifyAll() {
        entityManager.createNativeQuery("select set_config('app.maintenance_mode','true',true)").getSingleResult();
        List<String> tenants = entityManager
                .createQuery("select distinct e.tenantId from AuditEvent e", String.class).getResultList();
        int broken = 0;
        for (String tenant : tenants) {
            String id = firstBreak(tenant);
            if (id != null) {
                broken++;
                log.error("Audit hash chain is broken for tenant {} at event {}", tenant, id);
                meters.ifPresent(m -> m.counter("erg.audit.chain.broken").increment());
            }
        }
        return broken;
    }

    private String firstBreak(String tenant) {
        String previous = null;
        String carry = null;
        OffsetDateTime afterAt = null;
        String afterId = null;
        for (;;) {
            // Keyset page on the verified order (occurredAt, id): no OFFSET rescans.
            var query = entityManager.createQuery("select e from AuditEvent e where e.tenantId = :t"
                    + (afterAt == null ? "" : " and (e.occurredAt > :at or (e.occurredAt = :at and e.id > :id))")
                    + " order by e.occurredAt, e.id", AuditEvent.class).setParameter("t", tenant);
            if (afterAt != null) query.setParameter("at", afterAt).setParameter("id", afterId);
            List<AuditEvent> page = query.setMaxResults(PAGE).getResultList();
            if (page.isEmpty()) return null;
            // The carried tail links this page to the previous one; null means the very first row.
            List<AuditEvent> window = carry == null ? page : prepend(carry, previous, page);
            String broken = firstBreak(window, hashChain, carry == null);
            if (broken != null) return broken;
            AuditEvent last = page.get(page.size() - 1);
            carry = last.getEventHash();
            previous = last.getId();
            afterAt = last.getOccurredAt();
            afterId = last.getId();
            entityManager.clear();
            if (page.size() < PAGE) return null;
        }
    }

    private static List<AuditEvent> prepend(String tailHash, String tailId, List<AuditEvent> page) {
        AuditEvent tail = new AuditEvent();
        tail.setId(tailId);
        tail.setEventHash(tailHash);
        java.util.ArrayList<AuditEvent> all = new java.util.ArrayList<>(page.size() + 1);
        all.add(tail);
        all.addAll(page);
        return all;
    }

    /**
     * Returns the id of the first event whose hash or link is wrong, else null. Retention purges the oldest
     * rows, so the first row's own previousHash is not checked, only its hash. When {@code startsAtHead} is
     * false the first element is a link-only stub carrying the previous page's tail.
     */
    static String firstBreak(List<AuditEvent> ordered, AuditHashChain chain, boolean startsAtHead) {
        for (int i = 0; i < ordered.size(); i++) {
            AuditEvent event = ordered.get(i);
            boolean stub = !startsAtHead && i == 0;
            if (stub) continue;
            if (i > 0 && !java.util.Objects.equals(event.getPreviousHash(), ordered.get(i - 1).getEventHash())) {
                return event.getId();
            }
            if (!chain.hash(event).equals(event.getEventHash())) return event.getId();
        }
        return null;
    }
}
