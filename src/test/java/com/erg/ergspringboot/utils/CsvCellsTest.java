package com.erg.ergspringboot.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvCellsTest {
    @Test
    void csvNeutralisesFormula() {
        for (String bad : new String[] {"=1+1", "+1", "-1", "@SUM(A1)", "\tx", "\rx"}) {
            assertThat(CsvCells.safe(bad)).isEqualTo("'" + bad);
        }
        assertThat(CsvCells.safe("hello")).isEqualTo("hello");
        assertThat(CsvCells.safe("")).isEmpty();
        assertThat(CsvCells.safe(null)).isNull();
    }
}
