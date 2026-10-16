package com.erg.ergspringboot.platform.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.IDN;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * BE-064 deterministic, provider-free harness.
 *
 * <p>These invariants intentionally live in test scope. They are the gate for
 * a future productization implementation and do not add runtime beans, tables,
 * provider SDKs or network calls while ERG remains single-tenant.</p>
 */
class SaasProductizationGateHarnessTest {

    @Test
    void tenantIsolationRejectsCrossTenantReadsAndWrites() {
        IsolationHarness harness = new IsolationHarness();
        harness.put(new Resource("resource-1", "tenant-a", "secret-a"));

        assertThat(harness.read(new Identity("tenant-a", "user-a"), "resource-1"))
                .contains(new Resource("resource-1", "tenant-a", "secret-a"));
        assertThat(harness.read(new Identity("tenant-b", "user-b"), "resource-1"))
                .isEmpty();
        assertThatThrownBy(() -> harness.write(
                new Identity("tenant-b", "user-b"), "resource-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void entitlementDecisionsAreTenantScopedAndExpire() {
        Instant now = Instant.parse("2026-08-09T00:00:00Z");
        EntitlementLedger ledger = new EntitlementLedger();
        ledger.grant(new Grant("tenant-a", "user-a", "lms.analytics", now.plusSeconds(60), 4));

        assertThat(ledger.allows(new Identity("tenant-a", "user-a"), "lms.analytics", now)).isTrue();
        assertThat(ledger.allows(new Identity("tenant-b", "user-a"), "lms.analytics", now)).isFalse();
        assertThat(ledger.allows(new Identity("tenant-a", "user-a"), "lms.analytics", now.plusSeconds(61)))
                .isFalse();
        assertThat(ledger.allows(new Identity("tenant-a", "user-a"), "lms.grading", now)).isFalse();
    }

    @Test
    void billingLifecycleIsMonotonicAndIdempotent() {
        BillingLedger ledger = new BillingLedger();
        Identity owner = new Identity("tenant-a", "billing-admin");
        BillingCommand activate = new BillingCommand("tenant-a", "sub-1", "evt-1", "hash-a", State.ACTIVE, 0);

        BillingResult first = ledger.apply(owner, activate);
        BillingResult replay = ledger.apply(owner, activate);
        assertThat(replay).isEqualTo(first);

        assertThatThrownBy(() -> ledger.apply(owner,
                new BillingCommand("tenant-a", "sub-1", "evt-1", "hash-b", State.ACTIVE, 0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("idempotency key");
        assertThatThrownBy(() -> ledger.apply(new Identity("tenant-b", "billing-admin"), activate))
                .isInstanceOf(AccessDeniedException.class);

        BillingResult pastDue = ledger.apply(owner,
                new BillingCommand("tenant-a", "sub-1", "evt-2", "hash-c", State.PAST_DUE, 1));
        assertThat(pastDue.state()).isEqualTo(State.PAST_DUE);
        assertThatThrownBy(() -> ledger.apply(owner,
                new BillingCommand("tenant-a", "sub-1", "evt-3", "hash-d", State.PENDING, 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("transition");
    }

    @Test
    void customDomainOwnershipIsNormalizedUniqueAndChallengeHashed() {
        DomainRegistry domains = new DomainRegistry();
        Identity owner = new Identity("tenant-a", "domain-admin");
        String token = "verification-token-a";

        DomainRecord record = domains.register(owner, "WWW.Example.COM.", token);
        assertThat(record.domain()).isEqualTo("www.example.com");
        assertThat(record.challengeHash()).isNotEqualTo(token);
        assertThat(domains.verify(owner, "www.example.com", token)).isTrue();
        assertThat(domains.verify(owner, "www.example.com", "wrong-token")).isFalse();
        assertThat(domains.verify(new Identity("tenant-b", "domain-admin"), "www.example.com", token))
                .isFalse();
        assertThatThrownBy(() -> domains.register(
                new Identity("tenant-b", "domain-admin"), "www.example.com", "other-token"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("owned");
    }

    @Test
    void harnessDoesNotCreateRuntimeSaaSSurface() throws Exception {
        assertThat(java.nio.file.Files.exists(
                java.nio.file.Path.of("src/main/java/com/erg/ergspringboot/platform/saas"))).isFalse();
        try (var migrations = java.nio.file.Files.list(
                java.nio.file.Path.of("src/main/resources/db/migration/postgres"))) {
            assertThat(migrations.noneMatch(path -> path.getFileName().toString().contains("saas"))).isTrue();
        }
    }

    record Identity(String tenantId, String subjectId) {
        Identity {
            if (tenantId == null || tenantId.isBlank() || subjectId == null || subjectId.isBlank()) {
                throw new IllegalArgumentException("authenticated identity is required");
            }
        }
    }

    record Resource(String id, String tenantId, String value) {
    }

    static final class IsolationHarness {
        private final Map<String, Resource> resources = new HashMap<>();

        void put(Resource resource) {
            resources.put(resource.id(), resource);
        }

        Optional<Resource> read(Identity actor, String id) {
            Resource resource = resources.get(id);
            return resource != null && resource.tenantId().equals(actor.tenantId())
                    ? Optional.of(resource) : Optional.empty();
        }

        void write(Identity actor, String id) {
            if (read(actor, id).isEmpty()) {
                throw new AccessDeniedException("tenant isolation denied");
            }
        }
    }

    record Grant(String tenantId, String subjectId, String feature, Instant expiresAt, long version) {
    }

    static final class EntitlementLedger {
        private final Map<String, Grant> grants = new HashMap<>();

        void grant(Grant grant) {
            grants.put(key(grant.tenantId(), grant.subjectId(), grant.feature()), grant);
        }

        boolean allows(Identity actor, String feature, Instant now) {
            Grant grant = grants.get(key(actor.tenantId(), actor.subjectId(), feature));
            return grant != null && grant.expiresAt().isAfter(now);
        }

        private String key(String tenant, String subject, String feature) {
            return tenant + "|" + subject + "|" + feature;
        }
    }

    enum State { PENDING, ACTIVE, PAST_DUE, CANCELED }

    record BillingCommand(
            String tenantId,
            String subscriptionId,
            String eventId,
            String payloadHash,
            State target,
            long expectedVersion) {
    }

    record BillingResult(String tenantId, String subscriptionId, String eventId, State state, long version) {
    }

    static final class BillingLedger {
        private final Map<String, BillingResult> appliedEvents = new HashMap<>();
        private final Map<String, BillingResult> subscriptions = new HashMap<>();
        private final Map<String, String> eventPayloadHashes = new HashMap<>();

        BillingResult apply(Identity actor, BillingCommand command) {
            if (!actor.tenantId().equals(command.tenantId())) {
                throw new AccessDeniedException("billing tenant mismatch");
            }
            String eventKey = command.tenantId() + "|" + command.eventId();
            BillingResult existing = appliedEvents.get(eventKey);
            if (existing != null) {
                if (!command.payloadHash().equals(eventPayloadHashes.get(eventKey))) {
                    throw new ConflictException("idempotency key payload conflict");
                }
                return existing;
            }

            String aggregateKey = command.tenantId() + "|" + command.subscriptionId();
            BillingResult current = subscriptions.getOrDefault(aggregateKey,
                    new BillingResult(command.tenantId(), command.subscriptionId(), "initial", State.PENDING, 0));
            if (current.version() != command.expectedVersion()
                    || !transitionAllowed(current.state(), command.target())) {
                throw new ConflictException("invalid billing transition or version");
            }
            BillingResult result = new BillingResult(command.tenantId(), command.subscriptionId(),
                    command.eventId(), command.target(), current.version() + 1);
            subscriptions.put(aggregateKey, result);
            appliedEvents.put(eventKey, result);
            eventPayloadHashes.put(eventKey, command.payloadHash());
            return result;
        }

        private boolean transitionAllowed(State from, State to) {
            return switch (from) {
                case PENDING -> to == State.ACTIVE || to == State.CANCELED;
                case ACTIVE -> to == State.PAST_DUE || to == State.CANCELED;
                case PAST_DUE -> to == State.ACTIVE || to == State.CANCELED;
                case CANCELED -> false;
            };
        }
    }

    record DomainRecord(String domain, String tenantId, long ownershipEpoch, String challengeHash) {
    }

    static final class DomainRegistry {
        private final Map<String, DomainRecord> records = new HashMap<>();

        DomainRecord register(Identity actor, String rawDomain, String challenge) {
            String domain = normalize(rawDomain);
            DomainRecord existing = records.get(domain);
            if (existing != null && !existing.tenantId().equals(actor.tenantId())) {
                throw new ConflictException("domain is already owned");
            }
            DomainRecord record = new DomainRecord(domain, actor.tenantId(),
                    existing == null ? 1 : existing.ownershipEpoch() + 1, hash(challenge));
            records.put(domain, record);
            return record;
        }

        boolean verify(Identity actor, String rawDomain, String challenge) {
            DomainRecord record;
            try {
                record = records.get(normalize(rawDomain));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            return record != null && record.tenantId().equals(actor.tenantId())
                    && MessageDigest.isEqual(record.challengeHash().getBytes(), hash(challenge).getBytes());
        }

        private String normalize(String rawDomain) {
            if (rawDomain == null || rawDomain.isBlank()) throw new IllegalArgumentException("domain is required");
            String candidate = rawDomain.trim().toLowerCase(Locale.ROOT);
            if (candidate.endsWith(".")) candidate = candidate.substring(0, candidate.length() - 1);
            if (candidate.contains("*") || candidate.length() > 253) {
                throw new IllegalArgumentException("domain must be a concrete hostname");
            }
            return IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES);
        }

        private String hash(String challenge) {
            if (challenge == null || challenge.isBlank()) throw new IllegalArgumentException("challenge is required");
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(challenge.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception exception) {
                throw new IllegalStateException("SHA-256 is required", exception);
            }
        }
    }

    static final class AccessDeniedException extends RuntimeException {
        AccessDeniedException(String message) { super(message); }
    }

    static final class ConflictException extends RuntimeException {
        ConflictException(String message) { super(message); }
    }
}
