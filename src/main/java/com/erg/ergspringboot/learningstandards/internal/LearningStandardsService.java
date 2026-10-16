package com.erg.ergspringboot.learningstandards.internal;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.learningstandards.Hashing;
import com.erg.ergspringboot.learningstandards.LearningPackageQuarantined;
import com.erg.ergspringboot.learningstandards.LearningStandardsArtifactStorage;
import com.erg.ergspringboot.learningstandards.LearningStandardsPort;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.CreateLtiRegistrationRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.CreatePackageRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.LtiLaunchRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.ValidatePackageRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.XapiStatementRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.LtiLaunchResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.LtiRegistrationResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.PackageResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.XapiStatementResponse;
import com.erg.ergspringboot.learningstandards.LearningPackageReady;
import com.erg.ergspringboot.learningstandards.LtiLaunchAccepted;
import com.erg.ergspringboot.learningstandards.LtiLaunchTokenVerifier;
import com.erg.ergspringboot.learningstandards.ScormPackageValidator;
import com.erg.ergspringboot.learningstandards.XapiStatementAccepted;
import com.erg.ergspringboot.learningstandards.XapiStatementPolicy;
import com.erg.ergspringboot.model.learningstandards.LearningPackage;
import com.erg.ergspringboot.model.learningstandards.LearningStandardsCommandReceipt;
import com.erg.ergspringboot.model.learningstandards.LtiRegistration;
import com.erg.ergspringboot.model.learningstandards.XapiStatement;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.learningstandards.LearningPackageRepository;
import com.erg.ergspringboot.repository.learningstandards.LearningStandardsCommandReceiptRepository;
import com.erg.ergspringboot.repository.learningstandards.LtiRegistrationRepository;
import com.erg.ergspringboot.repository.learningstandards.XapiStatementRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application boundary for SCORM/xAPI/LTI. Protocol payloads are validated and
 * reduced to bounded metadata before persistence; adapters never leak into the
 * rest of the backend.
 */
@Service
public class LearningStandardsService implements LearningStandardsPort {
    private static final String UPLOADING = "UPLOADING";
    private static final String VALIDATING = "VALIDATING";
    private static final String READY = "READY";
    private static final String QUARANTINED = "QUARANTINED";
    private static final String ACTIVE = "ACTIVE";
    private static final String ACCEPTED = "ACCEPTED";
    private static final int MAX_PACKAGE_TITLE = 255;
    private static final long MAX_PACKAGE_BYTES = 512L * 1024L * 1024L;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final LearningPackageRepository packages;
    private final LtiRegistrationRepository registrations;
    private final XapiStatementRepository statements;
    private final LearningStandardsCommandReceiptRepository receipts;
    private final LearningStandardsArtifactStorage storage;
    private final LtiLaunchTokenVerifier ltiVerifier;
    private final ApplicationEventPublisher events;
    private final AuditOperations audit;

    @Autowired
    public LearningStandardsService(
            LearningPackageRepository packages,
            LtiRegistrationRepository registrations,
            XapiStatementRepository statements,
            LearningStandardsCommandReceiptRepository receipts,
            LearningStandardsArtifactStorage storage,
            LtiLaunchTokenVerifier ltiVerifier,
            ApplicationEventPublisher events,
            AuditOperations audit) {
        this.packages = packages;
        this.registrations = registrations;
        this.statements = statements;
        this.receipts = receipts;
        this.storage = storage;
        this.ltiVerifier = ltiVerifier;
        this.events = events;
        this.audit = audit;
    }

    @Override
    @Transactional("postgresTransactionManager")
    public PackageResponse createPackage(UserAccessContext access, CreatePackageRequest request,
                                         String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureManage();
        CreatePackageRequest safe = Objects.requireNonNull(request, "request");
        String title = required(safe.title(), "title", MAX_PACKAGE_TITLE);
        String standard = required(safe.standard(), "standard", 32).toUpperCase(Locale.ROOT);
        if (!standard.equals("SCORM_12") && !standard.equals("SCORM_2004")) {
            throw new BadRequestException("standard must be SCORM_12 or SCORM_2004");
        }
        String artifactKey = required(safe.artifactKey(), "artifactKey", 255);
        String packageVersion = required(safe.packageVersion(), "packageVersion", 96);
        if (!ScormPackageValidator.safeArtifactKey(artifactKey)) throw new BadRequestException("artifactKey is unsafe");
        String artifactSha = sha(required(safe.artifactSha256(), "artifactSha256", 64), "artifactSha256");
        String manifestSha = sha(required(safe.manifestSha256(), "manifestSha256", 64), "manifestSha256");
        long byteSize = safe.byteSize() == null ? -1 : safe.byteSize();
        if (byteSize <= 0 || byteSize > MAX_PACKAGE_BYTES) throw new BadRequestException("byteSize is outside the allowed range");
        String launchPath = required(safe.launchPath(), "launchPath", 255);
        if (!ScormPackageValidator.safeLaunchPath(launchPath)) throw new BadRequestException("launchPath is unsafe");
        String key = key(idempotencyKey);
        String hash = Hashing.sha256(String.join("\u001f", title, standard, packageVersion, artifactKey,
                artifactSha, Long.toString(byteSize), manifestSha, launchPath));
        LearningStandardsCommandReceipt replay = begin(tenant, "CREATE_PACKAGE", key, hash);
        if (replay != null) return packageResponse(findPackage(tenant, replay.getAggregateId()));
        if (packages.findByTenantIdAndArtifactKey(tenant, artifactKey).isPresent()) {
            throw new ConflictException("artifactKey is already registered");
        }
        LearningPackage value = new LearningPackage();
        value.setTenantId(tenant);
        value.setTitle(title);
        value.setStandard(standard);
        value.setPackageVersion(packageVersion);
        value.setArtifactKey(artifactKey);
        value.setArtifactSha256(artifactSha);
        value.setByteSize(byteSize);
        value.setManifestSha256(manifestSha);
        value.setLaunchPath(launchPath);
        value.setStatus(UPLOADING);
        value.setCreatedBy(access.userId());
        value.setUpdatedBy(access.userId());
        LearningPackage saved = packages.saveAndFlush(value);
        saveReceipt(tenant, "CREATE_PACKAGE", key, hash, "LEARNING_PACKAGE", saved.getId());
        audit(access, "LEARNING_PACKAGE_CREATED", "LEARNING_PACKAGE", saved.getId(), key,
                Map.of("standard", standard, "byteSize", byteSize));
        return packageResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<PackageResponse> packages(UserAccessContext access, String status) {
        String tenant = tenant(access);
        ensureRead();
        String requested = trimToNull(status);
        List<LearningPackage> values = requested == null
                ? packages.findTop100ByTenantIdOrderByUpdatedAtDesc(tenant)
                : packages.findTop100ByTenantIdAndStatusOrderByUpdatedAtDesc(tenant, requested.toUpperCase(Locale.ROOT));
        return values.stream().map(this::packageResponse).toList();
    }

    @Override
    @Transactional("postgresTransactionManager")
    public PackageResponse validatePackage(UserAccessContext access, String packageId,
                                           ValidatePackageRequest request, String idempotencyKey,
                                           String correlationId) {
        String tenant = tenant(access);
        ensureManage();
        ValidatePackageRequest safe = Objects.requireNonNull(request, "request");
        if (safe.expectedVersion() == null) throw new BadRequestException("expectedVersion is required");
        LearningPackage value = findPackage(tenant, required(packageId, "packageId", 96));
        if (version(value) != safe.expectedVersion()) throw new ConflictException("package changed; reload before validation");
        String manifest = required(safe.manifestXml(), "manifestXml", ScormPackageValidator.MAX_MANIFEST_CHARS);
        String key = key(idempotencyKey);
        String hash = Hashing.sha256("validate-package\u001f" + value.getId() + "\u001f"
                + safe.expectedVersion() + "\u001f" + Hashing.sha256(manifest));
        LearningStandardsCommandReceipt replay = begin(tenant, "VALIDATE_PACKAGE", key, hash);
        if (replay != null) return packageResponse(findPackage(tenant, replay.getAggregateId()));
        value.setStatus(VALIDATING);
        value.setQuarantineReason(null);
        ScormPackageValidator.Result manifestResult = ScormPackageValidator.validate(
                value.getStandard(), manifest, value.getLaunchPath());
        Optional<LearningStandardsArtifactStorage.ArtifactMetadata> artifact =
                storage.head(tenant, value.getArtifactKey());
        String failure = artifact.isEmpty() ? "artifact is not available in storage" : null;
        if (failure == null && (artifact.get().byteSize() != value.getByteSize()
                || !artifact.get().sha256().equalsIgnoreCase(value.getArtifactSha256()))) {
            failure = "artifact metadata does not match the registered digest";
        }
        if (failure == null && !manifestResult.valid()) failure = manifestResult.reason();
        if (failure == null && !Hashing.sha256(manifest).equalsIgnoreCase(value.getManifestSha256())) {
            failure = "manifest digest does not match the registered digest";
        }
        value.setStatus(failure == null ? READY : QUARANTINED);
        value.setQuarantineReason(failure);
        value.setManifestIdentifier(failure == null ? manifestResult.identifier() : null);
        value.setUpdatedBy(access.userId());
        LearningPackage saved = packages.saveAndFlush(value);
        saveReceipt(tenant, "VALIDATE_PACKAGE", key, hash, "LEARNING_PACKAGE", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelation = correlation(correlationId, eventId);
        if (failure == null) {
            events.publishEvent(new LearningPackageReady(eventId, tenant, saved.getId(), access.userId(),
                    version(saved), now(), 1, eventCorrelation, eventCorrelation));
            audit(access, "LEARNING_PACKAGE_READY", "LEARNING_PACKAGE", saved.getId(), key,
                    Map.of("standard", saved.getStandard()));
        } else {
            events.publishEvent(new LearningPackageQuarantined(eventId, tenant, saved.getId(), failure,
                    access.userId(), version(saved), now(), 1, eventCorrelation, eventCorrelation));
            audit(access, "LEARNING_PACKAGE_QUARANTINED", "LEARNING_PACKAGE", saved.getId(), key,
                    Map.of("reason", failure));
        }
        return packageResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public PackageResponse packageById(UserAccessContext access, String packageId) {
        String tenant = tenant(access);
        ensureRead();
        return packageResponse(findPackage(tenant, required(packageId, "packageId", 96)));
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public PackageResponse launchPackage(UserAccessContext access, String packageId) {
        String tenant = tenant(access);
        ensureLaunch();
        LearningPackage value = findPackage(tenant, required(packageId, "packageId", 96));
        if (!READY.equals(value.getStatus())) throw new ConflictException("package is not launchable");
        return packageResponse(value);
    }

    @Override
    @Transactional("postgresTransactionManager")
    public LtiRegistrationResponse createLtiRegistration(UserAccessContext access,
                                                          CreateLtiRegistrationRequest request,
                                                          String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureManage();
        CreateLtiRegistrationRequest safe = Objects.requireNonNull(request, "request");
        String issuer = required(safe.issuer(), "issuer", 255);
        String clientId = required(safe.clientId(), "clientId", 255);
        String deploymentId = required(safe.deploymentId(), "deploymentId", 255);
        String host = required(safe.allowedTargetHost(), "allowedTargetHost", 255).toLowerCase(Locale.ROOT);
        String pem = required(safe.publicKeyPem(), "publicKeyPem", 16_384);
        LtiLaunchTokenVerifier.Registration registration = new LtiLaunchTokenVerifier.Registration(
                issuer, clientId, deploymentId, pem, host);
        try {
            ltiVerifier.validateRegistration(registration);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
        String key = key(idempotencyKey);
        String hash = Hashing.sha256(String.join("\u001f", issuer, clientId, deploymentId, pem, host));
        LearningStandardsCommandReceipt replay = begin(tenant, "CREATE_LTI_REGISTRATION", key, hash);
        if (replay != null) return ltiResponse(findRegistration(tenant, replay.getAggregateId()));
        if (registrations.findByTenantIdAndIssuerAndClientId(tenant, issuer, clientId).isPresent()) {
            throw new ConflictException("LTI registration already exists");
        }
        LtiRegistration value = new LtiRegistration();
        value.setTenantId(tenant);
        value.setIssuer(issuer);
        value.setClientId(clientId);
        value.setDeploymentId(deploymentId);
        value.setPublicKeyPem(pem);
        value.setAllowedTargetHost(host);
        value.setStatus(ACTIVE);
        value.setCreatedBy(access.userId());
        value.setUpdatedBy(access.userId());
        LtiRegistration saved = registrations.saveAndFlush(value);
        saveReceipt(tenant, "CREATE_LTI_REGISTRATION", key, hash, "LTI_REGISTRATION", saved.getId());
        audit(access, "LTI_REGISTRATION_CREATED", "LTI_REGISTRATION", saved.getId(), key,
                Map.of("issuer", issuer, "clientId", clientId));
        return ltiResponse(saved);
    }

    @Override
    @Transactional("postgresTransactionManager")
    public LtiLaunchResponse launchLti(UserAccessContext access, String registrationId,
                                       LtiLaunchRequest request, String idempotencyKey,
                                       String correlationId) {
        String tenant = tenant(access);
        ensureLaunch();
        LtiLaunchRequest safe = Objects.requireNonNull(request, "request");
        String signedToken = required(safe.signedToken(), "signedToken", 64_000);
        String nonce = required(safe.nonce(), "nonce", 255);
        LtiRegistration registration = findRegistration(tenant, required(registrationId, "registrationId", 96));
        if (!ACTIVE.equals(registration.getStatus())) throw new ForbiddenException("LTI registration is inactive");
        String key = key(idempotencyKey == null ? Hashing.sha256(signedToken) : idempotencyKey);
        String hash = Hashing.sha256("lti-launch\u001f" + registration.getId() + "\u001f" + nonce
                + "\u001f" + Hashing.sha256(signedToken));
        LearningStandardsCommandReceipt replay = begin(tenant, "LTI_LAUNCH", key, hash);
        if (replay != null) return new LtiLaunchResponse(registration.getId(), "replayed", "", ACCEPTED, now());
        LtiLaunchTokenVerifier.VerifiedLaunch verified;
        try {
            verified = ltiVerifier.verify(signedToken, nonce, new LtiLaunchTokenVerifier.Registration(
                    registration.getIssuer(), registration.getClientId(), registration.getDeploymentId(),
                    registration.getPublicKeyPem(), registration.getAllowedTargetHost()));
        } catch (IllegalArgumentException exception) {
            throw new ForbiddenException("LTI launch proof is invalid");
        }
        saveReceipt(tenant, "LTI_LAUNCH", key, hash, "LTI_REGISTRATION", registration.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelation = correlation(correlationId, eventId);
        events.publishEvent(new LtiLaunchAccepted(eventId, tenant, registration.getId(), verified.subject(),
                access.userId(), version(registration), now(), 1, eventCorrelation, eventCorrelation));
        audit(access, "LTI_LAUNCH_ACCEPTED", "LTI_REGISTRATION", registration.getId(), key,
                Map.of("subjectHash", Hashing.sha256(verified.subject())));
        return new LtiLaunchResponse(registration.getId(), verified.subject(), verified.targetLinkUri(), ACCEPTED, now());
    }

    @Override
    @Transactional("postgresTransactionManager")
    public XapiStatementResponse ingestXapi(UserAccessContext access, XapiStatementRequest request,
                                             String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureManage();
        XapiStatementRequest safe = Objects.requireNonNull(request, "request");
        XapiStatementPolicy.Validation validation = XapiStatementPolicy.validate(safe.statementId(), safe.actorId(),
                safe.verb(), safe.objectId(), safe.payloadSha256());
        if (!validation.valid()) throw new BadRequestException(validation.reason());
        String statementId = required(safe.statementId(), "statementId", 96);
        String payloadHash = sha(required(safe.payloadSha256(), "payloadSha256", 64), "payloadSha256");
        String key = key(idempotencyKey == null ? statementId : idempotencyKey);
        String hash = Hashing.sha256(String.join("\u001f", statementId, safe.actorId(), safe.verb(), safe.objectId(), payloadHash,
                safe.occurredAt() == null ? "" : safe.occurredAt()));
        LearningStandardsCommandReceipt replay = begin(tenant, "XAPI_STATEMENT", key, hash);
        if (replay != null) return xapiResponse(findStatement(tenant, replay.getAggregateId()));
        Optional<XapiStatement> existing = statements.findByTenantIdAndStatementId(tenant, statementId);
        if (existing.isPresent()) {
            if (!payloadHash.equalsIgnoreCase(existing.get().getPayloadSha256())) {
                throw new ConflictException("xAPI statementId was already used with another payload");
            }
            saveReceipt(tenant, "XAPI_STATEMENT", key, hash, "XAPI_STATEMENT", existing.get().getId());
            return xapiResponse(existing.get());
        }
        XapiStatement value = new XapiStatement();
        value.setTenantId(tenant);
        value.setStatementId(statementId);
        value.setActorHash(XapiStatementPolicy.actorHash(required(safe.actorId(), "actorId", 512)));
        value.setVerb(required(safe.verb(), "verb", 512));
        value.setObjectId(required(safe.objectId(), "objectId", 512));
        value.setPayloadSha256(payloadHash);
        value.setStatus(ACCEPTED);
        value.setOccurredAt(parseTime(safe.occurredAt()));
        value.setReceivedAt(now());
        value.setAcceptedBy(access.userId());
        XapiStatement saved = statements.saveAndFlush(value);
        saveReceipt(tenant, "XAPI_STATEMENT", key, hash, "XAPI_STATEMENT", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelation = correlation(correlationId, eventId);
        events.publishEvent(new XapiStatementAccepted(eventId, tenant, saved.getStatementId(), saved.getActorHash(),
                version(saved), now(), 1, eventCorrelation, eventCorrelation));
        audit(access, "XAPI_STATEMENT_ACCEPTED", "XAPI_STATEMENT", saved.getId(), key,
                Map.of("statementId", statementId, "actorHash", saved.getActorHash()));
        return xapiResponse(saved);
    }

    private LearningStandardsCommandReceipt begin(String tenant, String operation, String key, String hash) {
        Optional<LearningStandardsCommandReceipt> existing = receipts
                .findByTenantIdAndIdempotencyKeyAndOperation(tenant, key, operation);
        if (existing.isEmpty()) return null;
        if (!hash.equals(existing.get().getPayloadHash())) throw new ConflictException("IDEMPOTENCY_CONFLICT");
        return existing.get();
    }

    private void saveReceipt(String tenant, String operation, String key, String hash,
                             String aggregateType, String aggregateId) {
        LearningStandardsCommandReceipt receipt = new LearningStandardsCommandReceipt();
        receipt.setTenantId(tenant);
        receipt.setOperation(operation);
        receipt.setIdempotencyKey(key);
        receipt.setPayloadHash(hash);
        receipt.setAggregateType(aggregateType);
        receipt.setAggregateId(aggregateId);
        receipts.saveAndFlush(receipt);
    }

    private void audit(UserAccessContext access, String action, String resourceType, String resourceId,
                       String key, Map<String, Object> details) {
        Map<String, Object> bounded = new LinkedHashMap<>();
        details.forEach((name, value) -> {
            if (bounded.size() < 12 && name != null && value != null) bounded.put(name, String.valueOf(value));
        });
        audit.record(new AuditRecord(access.tenantId(), access.userId(), "TENANT", access.tenantId(), action,
                resourceType, resourceId, AuditOutcome.SUCCESS, null, null, null, bounded,
                "learning-standards:" + key, now(), null));
    }

    private String tenant(UserAccessContext access) {
        if (access == null || access.userId() == null || access.userId().isBlank()) {
            throw new ForbiddenException("authenticated access context is required");
        }
        return AuthenticatedTenant.require(access.tenantId());
    }

    private void ensureManage() {
        if (!permission("learning.standards.manage")) throw new ForbiddenException("learning standards management is required");
    }

    private void ensureRead() {
        if (!permission("learning.standards.read") && !permission("learning.standards.manage")) {
            throw new ForbiddenException("learning standards read permission is required");
        }
    }

    private void ensureLaunch() {
        if (!permission("learning.standards.launch") && !permission("learning.standards.manage")) {
            throw new ForbiddenException("learning standards launch permission is required");
        }
    }

    private boolean permission(String expected) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal)) return false;
        if (principal.deniedPermissions().stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals("*") || value.equals(expected))) return false;
        return principal.permissions().stream().map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals("*") || value.equals(expected));
    }

    private LearningPackage findPackage(String tenant, String id) {
        return packages.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("learning-standard package not found"));
    }

    private LtiRegistration findRegistration(String tenant, String id) {
        return registrations.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("LTI registration not found"));
    }

    private XapiStatement findStatement(String tenant, String id) {
        return statements.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("xAPI statement not found"));
    }

    private PackageResponse packageResponse(LearningPackage value) {
        return new PackageResponse(value.getId(), value.getTenantId(), value.getTitle(), value.getStandard(),
                value.getPackageVersion(), value.getArtifactKey(), value.getArtifactSha256(), value.getByteSize(),
                value.getManifestSha256(), value.getManifestIdentifier(), value.getLaunchPath(), value.getStatus(),
                value.getQuarantineReason(), version(value), value.getCreatedAt(), value.getUpdatedAt());
    }

    private LtiRegistrationResponse ltiResponse(LtiRegistration value) {
        return new LtiRegistrationResponse(value.getId(), value.getTenantId(), value.getIssuer(), value.getClientId(),
                value.getDeploymentId(), value.getAllowedTargetHost(), value.getStatus(), version(value),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private XapiStatementResponse xapiResponse(XapiStatement value) {
        return new XapiStatementResponse(value.getId(), value.getTenantId(), value.getStatementId(), value.getActorHash(),
                value.getVerb(), value.getObjectId(), value.getPayloadSha256(), value.getStatus(), value.getOccurredAt(),
                value.getReceivedAt(), version(value));
    }

    private long version(Object value) {
        return switch (value) {
            case LearningPackage packageValue -> packageValue.getRecordVersion() == null ? 0 : packageValue.getRecordVersion();
            case LtiRegistration registration -> registration.getRecordVersion() == null ? 0 : registration.getRecordVersion();
            case XapiStatement statement -> statement.getRecordVersion() == null ? 0 : statement.getRecordVersion();
            default -> 0;
        };
    }

    private String required(String value, String name, int max) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new BadRequestException(name + " is required");
        if (normalized.length() > max) throw new BadRequestException(name + " is too long");
        return normalized;
    }

    private String key(String value) {
        return required(value, "idempotencyKey", 255);
    }

    private String sha(String value, String field) {
        if (!SHA256.matcher(value).matches()) throw new BadRequestException(field + " must be a SHA-256 hex digest");
        return value.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private OffsetDateTime parseTime(String value) {
        if (trimToNull(value) == null) return now();
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(value.trim());
            if (parsed.isAfter(now().plusMinutes(5))) throw new BadRequestException("occurredAt is in the future");
            return parsed;
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BadRequestException("occurredAt must be ISO-8601");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String correlation(String value, String fallback) {
        return trimToNull(value) == null ? fallback : value.trim();
    }
}
