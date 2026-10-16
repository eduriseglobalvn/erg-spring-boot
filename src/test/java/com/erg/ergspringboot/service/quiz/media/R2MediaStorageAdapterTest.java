package com.erg.ergspringboot.service.quiz.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class R2MediaStorageAdapterTest {

    @Test
    void presignBindsSizeMimeAndSha256ChecksumThenHeadVerifiesThem() throws Exception {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://r2.example.test/upload"));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        String sha = "a".repeat(64);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(2048L)
                .contentType("image/png")
                .checksumSHA256(Base64.getEncoder().encodeToString(java.util.HexFormat.of().parseHex(sha)))
                .build());
        R2MediaStorageAdapter adapter = new R2MediaStorageAdapter(client, presigner, "erg-media", "https://cdn.example.test");
        String key = adapter.storageKey("tenant-a", sha, "image/png");

        assertThat(adapter.presignUpload(key, "image/png", 2048L)).isEqualTo("https://r2.example.test/upload");
        var stored = adapter.headObject(key).orElseThrow();

        assertThat(stored).isEqualTo(new MediaStorageAdapter.StoredObject(2048L, sha, "image/png"));
        assertThat(adapter.publicUrl(key)).isEqualTo("https://cdn.example.test/" + key);
        ArgumentCaptor<PutObjectPresignRequest> presign = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(presigner).presignPutObject(presign.capture());
        assertThat(presign.getValue().putObjectRequest().bucket()).isEqualTo("erg-media");
        assertThat(presign.getValue().putObjectRequest().key()).isEqualTo(key);
        assertThat(presign.getValue().putObjectRequest().contentLength()).isEqualTo(2048L);
        assertThat(presign.getValue().putObjectRequest().contentType()).isEqualTo("image/png");
        assertThat(presign.getValue().putObjectRequest().checksumSHA256()).isNotBlank();
    }
}
