package com.erg.ergspringboot.learningstandards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.learningstandards.internal.storage.R2LearningStandardsArtifactStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class R2LearningStandardsArtifactStorageTest {
    private final S3Client client = mock(S3Client.class);
    private final R2LearningStandardsArtifactStorage storage = new R2LearningStandardsArtifactStorage(client, "bucket");

    @Test
    void usesTheStoredChecksumWithoutDownloading() throws Exception {
        byte[] sha = MessageDigest.getInstance("SHA-256").digest("zip-content".getBytes(StandardCharsets.UTF_8));
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(11L).checksumSHA256(Base64.getEncoder().encodeToString(sha)).build());

        var metadata = storage.head("tenant-1", "artifacts/course.zip").orElseThrow();

        assertThat(metadata.byteSize()).isEqualTo(11);
        assertThat(metadata.sha256()).isEqualTo(HexFormat.of().formatHex(sha));
        verify(client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void hashesTheObjectWhenTheChecksumIsMissingOrComposite() throws Exception {
        byte[] bytes = "zip-content".getBytes(StandardCharsets.UTF_8);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength((long) bytes.length).checksumSHA256("AAAA-2").build());
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), new ByteArrayInputStream(bytes)));

        var metadata = storage.head("tenant-1", "artifacts/course.zip").orElseThrow();

        assertThat(metadata.byteSize()).isEqualTo(bytes.length);
        assertThat(metadata.sha256())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        verify(client).getObject(GetObjectRequest.builder()
                .bucket("bucket").key("learning-standards/tenant-1/artifacts/course.zip").build());
    }

    @Test
    void missingOrOversizedObjectIsEmpty() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build())
                .thenReturn(HeadObjectResponse.builder().contentLength(600L * 1024 * 1024).build());

        assertThat(storage.head("tenant-1", "artifacts/course.zip")).isEmpty();
        assertThat(storage.head("tenant-1", "artifacts/course.zip")).isEmpty();
    }

    @Test
    void rejectsKeysThatLeaveTheTenantPrefixWithoutCallingR2() {
        assertThat(storage.head("tenant-1", "../outside.zip")).isEmpty();
        assertThat(storage.head("tenant-1", "/artifacts/course.zip")).isEmpty();
        assertThat(storage.head("../other-tenant", "artifacts/course.zip")).isEmpty();
        assertThat(storage.head("tenant-1/x", "artifacts/course.zip")).isEmpty();
        assertThat(storage.head(" ", "artifacts/course.zip")).isEmpty();
        verifyNoInteractions(client);
    }
}
