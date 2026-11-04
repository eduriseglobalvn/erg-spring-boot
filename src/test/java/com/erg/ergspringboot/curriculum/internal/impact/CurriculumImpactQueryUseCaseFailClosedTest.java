package com.erg.ergspringboot.curriculum.internal.impact;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.erg.ergspringboot.curriculum.persistence.CurriculumLevelRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubjectRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumTopicRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurriculumImpactQueryUseCaseFailClosedTest {
    @Test
    void refusesToStartWithoutUsageCountersSoNodesNeverLookUnused() {
        assertThatThrownBy(() -> new CurriculumImpactQueryUseCase(mock(CurriculumSubjectRepository.class),
                mock(CurriculumLevelRepository.class), mock(CurriculumTopicRepository.class), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
