package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeacherShortNameTest {

    @Test
    void threeOrMoreWordsKeepTheMiddleInitialAndTheGivenName() {
        assertThat(TeacherShortName.of("Nguyễn Quốc Vương")).isEqualTo("Q.Vương");
        assertThat(TeacherShortName.of("Trần Thanh Tuấn")).isEqualTo("T.Tuấn");
        assertThat(TeacherShortName.of("  Lê  Thị  Hà ")).isEqualTo("T.Hà");
    }

    @Test
    void shortNamesAreKeptAsTheyAre() {
        assertThat(TeacherShortName.of("Lan Anh")).isEqualTo("Lan Anh");
        assertThat(TeacherShortName.of("Vương")).isEqualTo("Vương");
        assertThat(TeacherShortName.of(null)).isEmpty();
    }

    @Test
    void initialsAreTheLastTwoWords() {
        assertThat(TeacherShortName.initials("Nguyễn Quốc Vương")).isEqualTo("QV");
        assertThat(TeacherShortName.initials("Lan Anh")).isEqualTo("LA");
        assertThat(TeacherShortName.initials("  vương ")).isEqualTo("V");
        assertThat(TeacherShortName.initials(null)).isEmpty();
    }
}
