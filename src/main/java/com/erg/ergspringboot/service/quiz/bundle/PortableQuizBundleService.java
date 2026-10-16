package com.erg.ergspringboot.service.quiz.bundle;

import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lcms.LcmsQuizBundleResponse;
import com.erg.ergspringboot.lcms.LcmsQuizPackageManifest;
import com.erg.ergspringboot.lcms.LcmsQuizPackageUpdateFeedResponse;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.repository.quiz.version.QuizPackageRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

/**
 * Builds the versioned portable-exam manifest consumed by the Tauri player.
 *
 * <p>The service is deliberately read-only. A package is an immutable publish
 * snapshot; revocation/expiry only makes it unavailable to a new export. The
 * authenticated tenant is supplied by the controller and is checked again at
 * the package/version boundary so a corrupted relation cannot cross tenants.
 */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class PortableQuizBundleService {

    private static final String MODE_CLIENT_FIRST = "client-first";
    private static final String MODE_SERVER_AUTH = "server-authoritative";
    private static final int BUNDLE_FORMAT_VERSION = 1;
    private static final int MAX_ASSET_NODES = 10_000;
    private static final long MAX_ASSET_BYTES = 1_073_741_824L;
    private static final int MAX_UPDATE_ITEMS = 100;
    private static final Pattern SHA256 = Pattern.compile("(?i)^(?:sha256:)?[0-9a-f]{64}$");
    private static final Set<String> PACKAGE_FORMATS = Set.of("single", "bundle");
    private static final Set<String> RELEASE_CHANNELS = Set.of("stable", "beta", "canary");

    private final QuizVersionRepository quizVersionRepository;
    private final QuizPackageRepository packageRepository;

    public PortableQuizBundleService(
            QuizVersionRepository quizVersionRepository,
            QuizPackageRepository packageRepository
    ) {
        this.quizVersionRepository = quizVersionRepository;
        this.packageRepository = packageRepository;
    }

    /**
     * Returns the newest usable published package. If the active package was
     * revoked/expired, an older immutable version is a safe rollback target.
     */
    public Map<String, Object> exportActive(String tenantId, String quizId) {
        QuizVersion active = quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz has no active published version"));

        List<QuizVersion> candidates = new ArrayList<>();
        candidates.add(active);
        quizVersionRepository.findByQuizIdAndTenantIdOrderByVersionNumberDesc(quizId, tenantId)
                .stream()
                .filter(candidate -> !candidate.getId().equals(active.getId()))
                .forEach(candidates::add);

        for (QuizVersion candidate : candidates) {
            try {
                return export(tenantId, candidate);
            } catch (ResourceNotFoundException ignored) {
                // A revoked/expired/missing package is not a reason to expose
                // a different tenant or draft; continue only within lineage.
            }
        }
        throw new ResourceNotFoundException("quiz has no usable portable package");
    }

    /** Exports a specific published version; superseded versions remain immutable. */
    public Map<String, Object> exportVersion(String tenantId, String quizVersionId) {
        QuizVersion version = quizVersionRepository.findByIdAndTenantId(quizVersionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz version not found"));
        return export(tenantId, version);
    }

    /** Returns only signed package metadata; no package JSON or answer key is returned. */
    public LcmsQuizPackageManifest manifest(String tenantId, String packageId) {
        QuizPackage pkg = packageRepository.findByIdAndTenantId(packageId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("portable package not found"));
        QuizVersion version = quizVersionRepository.findByIdAndTenantId(pkg.getQuizVersionId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("published version not found"));
        LcmsQuizBundleResponse bundle = LcmsQuizBundleResponse.fromLegacy(export(tenantId, version));
        if (!packageId.equals(bundle.packageId())) {
            throw new ResourceNotFoundException("portable package lineage is invalid");
        }
        return LcmsQuizPackageManifest.fromBundle(bundle);
    }

    /**
     * Produces a bounded, server-authoritative update feed for one quiz. Revoked,
     * expired, unsigned or runtime-incompatible packages are never advertised.
     */
    public LcmsQuizPackageUpdateFeedResponse updates(
            String tenantId,
            String quizId,
            String releaseChannel,
            Integer currentVersionNumber,
            String runtimeVersion
    ) {
        String channel = normalizeChannel(releaseChannel);
        if (!RELEASE_CHANNELS.contains(channel)) {
            throw new BadRequestException("release channel must be stable, beta or canary");
        }
        if (currentVersionNumber != null && currentVersionNumber < 0) {
            throw new BadRequestException("current version number must be non-negative");
        }
        if (runtimeVersion != null && runtimeVersion.length() > 64) {
            throw new BadRequestException("runtime version is too long");
        }

        List<LcmsQuizPackageManifest> available = new ArrayList<>();
        List<QuizVersion> versions = quizVersionRepository
                .findByQuizIdAndTenantIdOrderByVersionNumberDesc(quizId, tenantId, PageRequest.of(0, MAX_UPDATE_ITEMS));
        for (QuizVersion version : versions) {
            if (version.getPublishedAt() == null
                    || (currentVersionNumber != null && version.getVersionNumber() <= currentVersionNumber)) {
                continue;
            }
            try {
                LcmsQuizPackageManifest manifest = manifestForVersion(tenantId, version);
                if (!manifest.portableReady()
                        || !channel.equals(normalizeChannel(manifest.releaseChannel()))
                        || !runtimeCompatible(manifest.minRuntimeVersion(), runtimeVersion)) {
                    continue;
                }
                available.add(manifest);
            } catch (ResourceNotFoundException ignored) {
                // Missing, expired and revoked packages are not update candidates.
            }
        }

        String etag = feedEtag(tenantId, quizId, channel, currentVersionNumber, runtimeVersion, available);
        return new LcmsQuizPackageUpdateFeedResponse(
                "1",
                tenantId,
                quizId,
                channel,
                currentVersionNumber,
                runtimeVersion,
                available.isEmpty() ? null : available.get(0),
                available,
                etag,
                OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    /** Idempotently revokes a package for one authenticated tenant. */
    @Transactional(value = "postgresTransactionManager", readOnly = false)
    public QuizPackage revoke(String tenantId, String packageId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 256) {
            throw new BadRequestException("revocation reason must be between 1 and 256 characters");
        }
        QuizPackage pkg = packageRepository.findByIdAndTenantId(packageId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("portable package not found"));
        if (pkg.getRevokedAt() == null) {
            pkg.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
            pkg.setRevocationReason(reason.trim());
            return packageRepository.save(pkg);
        }
        return pkg;
    }

    private Map<String, Object> export(String tenantId, QuizVersion version) {
        requirePublishedLineage(tenantId, version);
        QuizPackage pkg = packageRepository.findByQuizVersionIdAndTenantId(version.getId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("published version has no compiled package"));
        requirePackageLineage(tenantId, version, pkg);
        if (pkg.getRevokedAt() != null || isExpired(pkg)) {
            throw new ResourceNotFoundException("portable package is no longer available");
        }

        String gradingMode = normalizedMode(pkg.getGradingMode());
        if (!gradingMode.equals(normalizedMode(version.getGradingMode()))) {
            throw new ResourceNotFoundException("package grading policy does not match published version");
        }
        boolean serverAuthoritative = MODE_SERVER_AUTH.equals(gradingMode);
        Map<String, Object> sourcePackage = pkg.getPackageJson() == null
                ? Map.of()
                : pkg.getPackageJson();
        AssetBounds assetBounds = inspectAssets(pkg.getAssetsManifest());
        if (!assetBounds.valid()) {
            throw new ResourceNotFoundException("portable package assets exceed the supported bounds");
        }

        Map<String, Object> runtime = copyAndSanitize(sourcePackage, serverAuthoritative);
        String contentHash = text(pkg.getContentHash());
        String signature = text(pkg.getSignature());
        String publicKeyId = text(pkg.getPublicKeyId());
        String signatureAlgorithm = text(pkg.getSignatureAlgorithm());
        boolean portableReady = isPortableReady(pkg, contentHash, signature, publicKeyId, signatureAlgorithm);

        Map<String, Object> signatureBlock = new LinkedHashMap<>();
        signatureBlock.put("contentHash", contentHash);
        signatureBlock.put("signature", signature);
        signatureBlock.put("publicKeyId", publicKeyId);
        signatureBlock.put("algorithm", signatureAlgorithm);

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("bundleFormatVersion", BUNDLE_FORMAT_VERSION);
        bundle.put("tenantId", tenantId);
        bundle.put("quizId", version.getQuizId());
        bundle.put("quizVersionId", version.getId());
        bundle.put("versionLabel", version.getVersionLabel());
        bundle.put("versionNumber", version.getVersionNumber());
        bundle.put("schemaVersion", version.getSchemaVersion());
        bundle.put("gradingMode", gradingMode);
        bundle.put("offlineGradable", MODE_CLIENT_FIRST.equals(gradingMode));
        bundle.put("packageId", pkg.getId());
        bundle.put("packageFormat", defaultValue(pkg.getPackageFormat(), "single"));
        bundle.put("package", runtime);
        bundle.put("assetsManifest", pkg.getAssetsManifest() == null ? Map.of() : pkg.getAssetsManifest());
        bundle.put("signatureBlock", signatureBlock);
        bundle.put("signatureAlgorithm", signatureAlgorithm);
        bundle.put("minRuntimeVersion", text(pkg.getMinRuntimeVersion()));
        bundle.put("downloadUrl", safeDownloadUrl(pkg.getDownloadUrl()));
        bundle.put("sizeBytes", pkg.getSizeBytes());
        bundle.put("releaseChannel", defaultValue(pkg.getReleaseChannel(), "stable"));
        bundle.put("expiresAt", pkg.getExpiresAt());
        bundle.put("portableReady", portableReady);
        bundle.put("etag", etag(contentHash, pkg.getId()));
        bundle.put("generatedAt", pkg.getGeneratedAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC).toString()
                : pkg.getGeneratedAt().withOffsetSameInstant(ZoneOffset.UTC).toString());
        return bundle;
    }

    private LcmsQuizPackageManifest manifestForVersion(String tenantId, QuizVersion version) {
        return LcmsQuizPackageManifest.fromBundle(
                LcmsQuizBundleResponse.fromLegacy(export(tenantId, version)));
    }

    private String normalizeChannel(String value) {
        return defaultValue(value, "stable").trim().toLowerCase(Locale.ROOT);
    }

    private boolean runtimeCompatible(String minimum, String actual) {
        if (!nonBlank(minimum)) return true;
        if (!nonBlank(actual)) return false;
        int[] required = parseVersion(minimum);
        int[] installed = parseVersion(actual);
        if (required == null || installed == null) return false;
        for (int i = 0; i < required.length; i++) {
            if (installed[i] != required[i]) return installed[i] > required[i];
        }
        return true;
    }

    private int[] parseVersion(String value) {
        String normalized = value == null ? "" : value.trim().replaceFirst("^[vV]", "");
        String[] parts = normalized.split("\\.", -1);
        if (parts.length == 0 || parts.length > 3) return null;
        int[] result = new int[] {0, 0, 0};
        try {
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isBlank() || !parts[i].matches("\\d+")) return null;
                result[i] = Integer.parseInt(parts[i]);
            }
            return result;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String feedEtag(
            String tenantId,
            String quizId,
            String channel,
            Integer currentVersionNumber,
            String runtimeVersion,
            List<LcmsQuizPackageManifest> available
    ) {
        StringBuilder canonical = new StringBuilder()
                .append(defaultValue(tenantId, ""))
                .append('\u001f').append(defaultValue(quizId, ""))
                .append('\u001f').append(channel)
                .append('\u001f').append(currentVersionNumber == null ? "" : currentVersionNumber)
                .append('\u001f').append(defaultValue(runtimeVersion, ""));
        available.forEach(item -> canonical.append('\u001f').append(defaultValue(item.etag(), item.packageId())));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to compute package update feed etag", ex);
        }
    }

    private void requirePublishedLineage(String tenantId, QuizVersion version) {
        if (tenantId == null || tenantId.isBlank()
                || version == null
                || !tenantId.equals(version.getTenantId())
                || version.getPublishedAt() == null
                || version.getQuizId() == null
                || version.getId() == null) {
            throw new ResourceNotFoundException("quiz version not found");
        }
    }

    private void requirePackageLineage(String tenantId, QuizVersion version, QuizPackage pkg) {
        if (pkg == null
                || !tenantId.equals(pkg.getTenantId())
                || !version.getId().equals(pkg.getQuizVersionId())
                || !version.getQuizId().equals(pkg.getQuizId())
                || pkg.getId() == null
                || !PACKAGE_FORMATS.contains(defaultValue(pkg.getPackageFormat(), "single").toLowerCase(Locale.ROOT))) {
            throw new ResourceNotFoundException("published version has no valid portable package");
        }
    }

    private boolean isPortableReady(
            QuizPackage pkg,
            String contentHash,
            String signature,
            String publicKeyId,
            String signatureAlgorithm
    ) {
        // Ed25519 is the only public-key algorithm currently accepted for an
        // offline verifier. Legacy rows may be returned with portableReady=false
        // until a signed package is published; they are never claimed secure.
        return isSha256(contentHash)
                && nonBlank(signature)
                && nonBlank(publicKeyId)
                && "Ed25519".equalsIgnoreCase(signatureAlgorithm)
                && pkg.getRevokedAt() == null
                && !isExpired(pkg)
                && !containsExternalAssetReference(pkg.getPackageJson(), "");
    }

    private boolean containsExternalAssetReference(Object value, String field) {
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            return ("url".equalsIgnoreCase(field) || "src".equalsIgnoreCase(field))
                    && (normalized.startsWith("https://") || normalized.startsWith("http://"));
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (containsExternalAssetReference(entry.getValue(), String.valueOf(entry.getKey()))) return true;
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (containsExternalAssetReference(item, field)) return true;
            }
        }
        return false;
    }

    private boolean isExpired(QuizPackage pkg) {
        return pkg.getExpiresAt() != null && !pkg.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private AssetBounds inspectAssets(Object value) {
        AssetBounds bounds = new AssetBounds();
        inspectAssets(value, bounds, 0);
        return bounds;
    }

    @SuppressWarnings("unchecked")
    private void inspectAssets(Object value, AssetBounds bounds, int depth) {
        if (!bounds.valid() || value == null || depth > 32) return;
        if (value instanceof Map<?, ?> map) {
            bounds.nodes += map.size();
            if (map.size() > MAX_ASSET_NODES || bounds.nodes > MAX_ASSET_NODES) {
                bounds.valid = false;
                return;
            }
            Object bytes = map.get("byteSize") != null ? map.get("byteSize") : map.get("sizeBytes");
            if (bytes instanceof Number number) {
                bounds.bytes += Math.max(0, number.longValue());
                if (bounds.bytes > MAX_ASSET_BYTES) bounds.valid = false;
            }
            for (Object child : map.values()) inspectAssets(child, bounds, depth + 1);
        } else if (value instanceof Collection<?> collection) {
            bounds.nodes += collection.size();
            if (bounds.nodes > MAX_ASSET_NODES) {
                bounds.valid = false;
                return;
            }
            for (Object child : collection) inspectAssets(child, bounds, depth + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyAndSanitize(Object value, boolean stripAnswerKey) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (isSensitiveKey(key) || (stripAnswerKey && isAnswerKey(key))) continue;
            copy.put(key, sanitizeValue(entry.getValue(), stripAnswerKey));
        }
        return copy;
    }

    private Object sanitizeValue(Object value, boolean stripAnswerKey) {
        if (value instanceof Map<?, ?>) return copyAndSanitize(value, stripAnswerKey);
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> sanitizeValue(item, stripAnswerKey)).toList();
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.equals("accesstoken")
                || normalized.equals("refreshtoken")
                || normalized.equals("password")
                || normalized.equals("clientsecret")
                || normalized.equals("apikey")
                || normalized.equals("privatekey")
                || normalized.equals("credential")
                || normalized.equals("credentials");
    }

    private boolean isAnswerKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.equals("answerkey") || normalized.equals("answerkeys");
    }

    private String safeDownloadUrl(String value) {
        if (!nonBlank(value)) return null;
        String normalized = value.trim();
        return normalized.startsWith("https://") ? normalized : null;
    }

    private String etag(String contentHash, String packageId) {
        return nonBlank(contentHash) ? contentHash : defaultValue(packageId, "empty");
    }

    private boolean isSha256(String value) {
        return nonBlank(value) && SHA256.matcher(value.trim()).matches();
    }

    private String normalizedMode(String value) {
        return defaultValue(value, MODE_CLIENT_FIRST).trim().toLowerCase(Locale.ROOT);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultValue(String value, String fallback) {
        return nonBlank(value) ? value : fallback;
    }

    private static final class AssetBounds {
        private int nodes;
        private long bytes;
        private boolean valid = true;

        private boolean valid() {
            return valid && nodes <= MAX_ASSET_NODES && bytes <= MAX_ASSET_BYTES;
        }
    }
}
