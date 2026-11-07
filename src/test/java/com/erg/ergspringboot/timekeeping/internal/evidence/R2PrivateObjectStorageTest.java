package com.erg.ergspringboot.timekeeping.internal.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class R2PrivateObjectStorageTest {

    private static final String KEY = "private/timekeeping/tenant-a/2026/11/abc.jpg";

    private final S3Client client = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final R2PrivateObjectStorage storage = new R2PrivateObjectStorage(client, presigner, "erg-private");

    @Test
    void presignGetSignsTheKeyInThePrivateBucketWithTheGivenTtl() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://acc.r2.example/erg-private/" + KEY + "?X-Amz-Signature=x"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        URI url = storage.presignGet(KEY, Duration.ofMinutes(5));

        assertThat(url.toString()).startsWith("https://acc.r2.example/erg-private/");
        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo("erg-private");
        assertThat(request.getValue().getObjectRequest().key()).isEqualTo(KEY);
        assertThat(request.getValue().getObjectRequest().responseCacheControl()).isEqualTo("private, no-store");
    }

    @Test
    void missingBucketSettingsFailAtConstruction() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> R2PrivateObjectStorage.create("", "ak", "sk", "auto", "erg-private"))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> R2PrivateObjectStorage.create("https://r2.example", "ak", "sk", "auto", " "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteAndPutTargetThePrivateBucket() {
        storage.delete(KEY);
        storage.put(KEY, new byte[] {1, 2}, "image/jpeg");

        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(delete.capture());
        assertThat(delete.getValue().bucket()).isEqualTo("erg-private");
        assertThat(delete.getValue().key()).isEqualTo(KEY);
        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().bucket()).isEqualTo("erg-private");
        assertThat(put.getValue().contentType()).isEqualTo("image/jpeg");
    }
}
