package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SchoolShortNameTest {

    @Test
    void shortNamesStayWholeWhenTheyFitAfterDroppingTheLevelPrefix() {
        assertThat(SchoolShortName.of("THCS Nguyễn Du")).isEqualTo("Nguyễn Du");
        assertThat(SchoolShortName.of("THCS Văn Lang")).isEqualTo("Văn Lang");
        assertThat(SchoolShortName.of("THCS Minh Đức")).isEqualTo("Minh Đức");
        assertThat(SchoolShortName.of("THCS Kết Đoàn")).isEqualTo("Kết Đoàn");
    }

    @Test
    void longNamesBecomeInitialsKeepingDiacritics() {
        assertThat(SchoolShortName.of("THCS Trương Công Định")).isEqualTo("TCĐ");
        assertThat(SchoolShortName.of("THPT Chuyên Lê Hồng Phong")).isEqualTo("CLHP");
    }

    @Test
    void travelLinesAlwaysUseInitials() {
        assertThat(SchoolShortName.initials("THCS Văn Lang")).isEqualTo("VL");
        assertThat(SchoolShortName.initials("THCS Trương Công Định")).isEqualTo("TCĐ");
        assertThat(SchoolShortName.initials("THCS Minh Đức")).isEqualTo("MĐ");
        assertThat(SchoolShortName.initials("THCS Kết Đoàn")).isEqualTo("KĐ");
        assertThat(SchoolShortName.initials("THCS Nguyễn Du")).isEqualTo("ND");
    }

    @Test
    void everyLevelPrefixIsStrippedAndThcsBeatsTh() {
        assertThat(SchoolShortName.of("TH Hòa Bình")).isEqualTo("Hòa Bình");
        assertThat(SchoolShortName.of("MN Hoa Mai")).isEqualTo("Hoa Mai");
        assertThat(SchoolShortName.of("TiH Sao Mai")).isEqualTo("Sao Mai");
        assertThat(SchoolShortName.of("THPT Lê Quý")).isEqualTo("Lê Quý");
        assertThat(SchoolShortName.of(null)).isEmpty();
    }
}
