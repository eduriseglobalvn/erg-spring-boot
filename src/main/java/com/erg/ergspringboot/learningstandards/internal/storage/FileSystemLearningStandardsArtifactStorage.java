package com.erg.ergspringboot.learningstandards.internal.storage;

import com.erg.ergspringboot.learningstandards.LearningStandardsArtifactStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local/dev artifact adapter. Production can replace this bean with R2/S3. */
@Component
public final class FileSystemLearningStandardsArtifactStorage implements LearningStandardsArtifactStorage {
    private static final long MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L;
    private final Path root;

    public FileSystemLearningStandardsArtifactStorage(
            @Value("${erg.learning-standards.storage-root:.erg-learning-standards}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public Optional<ArtifactMetadata> head(String tenantId, String artifactKey) {
        if (tenantId == null || tenantId.isBlank() || artifactKey == null || artifactKey.isBlank()) {
            return Optional.empty();
        }
        Path object = root.resolve(tenantId).resolve(artifactKey).normalize();
        if (!object.startsWith(root) || !Files.isRegularFile(object)) return Optional.empty();
        try {
            Path realRoot = root.toRealPath();
            Path realObject = object.toRealPath();
            if (!realObject.startsWith(realRoot)) return Optional.empty();
            long size = Files.size(realObject);
            if (size < 0 || size > MAX_ARTIFACT_BYTES) return Optional.empty();
            return Optional.of(new ArtifactMetadata(size, sha256(realObject)));
        } catch (IOException exception) {
            throw new IllegalStateException("cannot inspect learning-standard artifact", exception);
        }
    }

    private String sha256(Path object) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(object)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
