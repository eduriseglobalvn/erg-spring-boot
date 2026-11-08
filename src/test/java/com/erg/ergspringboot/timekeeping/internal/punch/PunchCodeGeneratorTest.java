package com.erg.ergspringboot.timekeeping.internal.punch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.timekeeping.persistence.TkCodeCounterRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PunchCodeGeneratorTest {

    @Test
    void codeIsCcDayMonthYearAndAFourDigitPerDayCounter() {
        TkCodeCounterRepository counters = mock(TkCodeCounterRepository.class);
        when(counters.increment(anyString(), eq("tenant-a"), eq("CC"), eq("121126"))).thenReturn(1L, 2L);
        when(counters.increment(anyString(), eq("tenant-a"), eq("CC"), eq("131126"))).thenReturn(1L);
        PunchCodeGenerator codes = new PunchCodeGenerator(counters);

        assertThat(codes.next("tenant-a", LocalDate.of(2026, 11, 12))).isEqualTo("CC-121126-0001");
        assertThat(codes.next("tenant-a", LocalDate.of(2026, 11, 12))).isEqualTo("CC-121126-0002");
        assertThat(codes.next("tenant-a", LocalDate.of(2026, 11, 13))).isEqualTo("CC-131126-0001");
    }
}
