package com.erg.ergspringboot.timekeeping.internal.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class PunchPhotoStorageConfigTest {

    private static final String SECRET = "test-only-timekeeping-photo-secret-0123456789";

    @TempDir Path root;

    @Test
    void localDiskStorageIsRefusedUnderProdProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new PunchPhotoStorageConfig()
                .timekeepingLocalPhotoStorage(root.toString(), SECRET, false, Clock.systemUTC(), prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TK_PHOTOS_R2__ENABLED");
        assertThat(new PunchPhotoStorageConfig()
                .timekeepingLocalPhotoStorage(root.toString(), SECRET, false, Clock.systemUTC(), new MockEnvironment()))
                .isNotNull();
    }

    @Test
    void temporaryOptInAllowsLocalDiskUnderProdProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThat(new PunchPhotoStorageConfig()
                .timekeepingLocalPhotoStorage(root.toString(), SECRET, true, Clock.systemUTC(), prod)).isNotNull();
    }

    @Test
    void prodOptInRefusesTheRelativeDefaultRootThatLivesInTheContainerLayer() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new PunchPhotoStorageConfig()
                .timekeepingLocalPhotoStorage(".erg-private", SECRET, true, Clock.systemUTC(), prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ERG_TK_PHOTOS_LOCAL_ROOT");
    }

    @Test
    void prodRefusesBlankSigningSecret() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new PunchPhotoStorageConfig()
                .timekeepingLocalPhotoStorage(root.toString(), " ", true, Clock.systemUTC(), prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SECRET_TK_PHOTO_URL_SIGNING_KEY");
    }

    @Test
    void devKeepsRelativeRootAndRandomSecret() {
        assertThat(new PunchPhotoStorageConfig()
                .timekeepingLocalPhotoStorage(".erg-private", "", false, Clock.systemUTC(), new MockEnvironment()))
                .isNotNull();
    }

    @Test
    void privateBucketMustNotBeThePublicMediaBucket() {
        assertThatThrownBy(() -> PunchPhotoStorageConfig.rejectPublicBucket("erg-media", "ERG-media "))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> PunchPhotoStorageConfig.rejectPublicBucket("erg-private", "erg-media"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PunchPhotoStorageConfig.rejectPublicBucket("erg-private", ""))
                .doesNotThrowAnyException();
    }
}
