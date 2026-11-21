package com.erg.ergspringboot.lms.internal.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StudentIdentityCleanTest {
    @Test
    void tabsDoubleSpacesAndNoBreakSpacesCollapseToOneSpace() {
        assertThat(StudentIdentity.clean("  Nguyen\tVan  A  ")).isEqualTo("Nguyen Van A");
        assertThat(StudentIdentity.normalize("NGUYEN \t VAN")).isEqualTo("nguyen van");
        assertThat(StudentIdentity.clean(null)).isEmpty();
    }
}
