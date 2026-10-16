package com.erg.ergspringboot.learningstandards;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.learningstandards.internal.storage.FileSystemLearningStandardsArtifactStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemLearningStandardsArtifactStorageTest {
    @TempDir Path temp;

    @Test
    void headsTenantScopedArtifactAndRejectsTraversal() throws Exception {
        Path artifact = temp.resolve("tenant-1/artifacts/course.zip");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, "zip-content");
        var storage = new FileSystemLearningStandardsArtifactStorage(temp.toString());

        var metadata = storage.head("tenant-1", "artifacts/course.zip");

        assertThat(metadata).isPresent();
        assertThat(metadata.orElseThrow().byteSize()).isEqualTo("zip-content".length());
        assertThat(metadata.orElseThrow().sha256()).hasSize(64);
        assertThat(storage.head("tenant-1", "../outside.zip")).isEmpty();
        assertThat(storage.head("../other-tenant", "artifacts/course.zip")).isEmpty();
    }
}
