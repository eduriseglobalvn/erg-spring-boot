package com.erg.ergspringboot.lcms.persistence.quiz.bank;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VietnameseFoldTest {

    @Test
    void aSearchTypedWithoutAccentsEqualsTheAccentedWord() {
        assertThat(VietnameseFold.fold("Biểu tượng Đĩa")).isEqualTo("bieu tuong dia");
        assertThat(VietnameseFold.fold("bieu tuong")).isEqualTo("bieu tuong");
    }

    @Test
    void theDatabaseTableFoldsEveryLetterTheSameWayAsTheJavaSide() {
        assertThat(VietnameseFold.FROM).hasSameSizeAs(VietnameseFold.TO);
        for (int i = 0; i < VietnameseFold.FROM.length(); i++) {
            assertThat(VietnameseFold.fold(String.valueOf(VietnameseFold.FROM.charAt(i)))).isEqualTo(String.valueOf(VietnameseFold.TO.charAt(i)));
        }
    }
}
