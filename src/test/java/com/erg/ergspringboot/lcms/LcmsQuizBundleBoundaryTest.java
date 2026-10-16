package com.erg.ergspringboot.lcms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.lcms.LcmsQuizBundleController;
import com.erg.ergspringboot.lcms.LcmsQuizBundleRevokeRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class LcmsQuizBundleBoundaryTest {

    @Test
    void bundleControllerDependsOnlyOnLcmsFacade() {
        assertThat(LcmsQuizBundleController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LcmsOperations.class));
    }

    @Test
    void lcmsFacadePublishesBothExistingBundleQueries() throws Exception {
        assertThat(LcmsOperations.class.getMethod("exportActiveBundle", String.class, String.class))
                .isNotNull();
        assertThat(LcmsOperations.class.getMethod("exportVersionBundle", String.class, String.class))
                .isNotNull();

        assertThat(LcmsQuizBundleController.class.getMethod(
                "exportActive", String.class, HttpServletRequest.class)).isNotNull();
        assertThat(LcmsQuizBundleController.class.getMethod(
                "exportVersion", String.class, HttpServletRequest.class)).isNotNull();
        assertThat(LcmsOperations.class.getMethod(
                "exportPackageManifest", String.class, String.class)).isNotNull();
        assertThat(LcmsOperations.class.getMethod(
                "packageUpdates", String.class, String.class, String.class, Integer.class, String.class)).isNotNull();
        assertThat(LcmsQuizBundleController.class.getMethod(
                "manifest", String.class, HttpServletRequest.class)).isNotNull();
        assertThat(LcmsQuizBundleController.class.getMethod(
                "download", String.class, HttpServletRequest.class)).isNotNull();
        assertThat(LcmsQuizBundleController.class.getMethod(
                "updates", String.class, String.class, Integer.class, String.class, HttpServletRequest.class)).isNotNull();
        assertThat(LcmsQuizBundleController.class.getMethod(
                "revoke", String.class, LcmsQuizBundleRevokeRequest.class, HttpServletRequest.class)).isNotNull();
    }
}
