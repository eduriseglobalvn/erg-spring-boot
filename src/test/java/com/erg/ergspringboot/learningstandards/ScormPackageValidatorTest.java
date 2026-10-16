package com.erg.ergspringboot.learningstandards;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScormPackageValidatorTest {
    private static final String SCORM_12 = "<manifest identifier=\"course-12\"><metadata>"
            + "<schemaVersion>1.2</schemaVersion></metadata></manifest>";
    private static final String SCORM_2004 = "<manifest identifier=\"course-04\"><metadata>"
            + "<schemaVersion>2004 4th Edition</schemaVersion></metadata></manifest>";

    @Test
    void acceptsSupportedScormManifestsAndSafeLaunchPath() {
        var twelve = ScormPackageValidator.validate("SCORM_12", SCORM_12, "index.html");
        var twoThousandFour = ScormPackageValidator.validate("SCORM_2004", SCORM_2004, "launch/index.html");

        assertThat(twelve.valid()).isTrue();
        assertThat(twelve.identifier()).isEqualTo("course-12");
        assertThat(twoThousandFour.valid()).isTrue();
        assertThat(twoThousandFour.schemaVersion()).startsWith("2004");
    }

    @Test
    void rejectsUnsafeXmlAndPathTraversal() {
        String xxe = "<!DOCTYPE manifest [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<manifest identifier=\"bad\"><schemaVersion>1.2</schemaVersion></manifest>";

        assertThat(ScormPackageValidator.validate("SCORM_12", xxe, "index.html").valid()).isFalse();
        assertThat(ScormPackageValidator.validate("SCORM_12", SCORM_12, "../index.html").valid()).isFalse();
        assertThat(ScormPackageValidator.safeArtifactKey("tenant/../secret.zip")).isFalse();
        assertThat(ScormPackageValidator.safeLaunchPath("://not-a-relative-path")).isFalse();
    }

    @Test
    void rejectsUnsupportedVersionAndBoundedOversizedManifest() {
        assertThat(ScormPackageValidator.validate("AICC", SCORM_12, "index.html").valid()).isFalse();
        assertThat(ScormPackageValidator.validate("SCORM_12", "x".repeat(ScormPackageValidator.MAX_MANIFEST_CHARS + 1),
                "index.html").valid()).isFalse();
    }
}
