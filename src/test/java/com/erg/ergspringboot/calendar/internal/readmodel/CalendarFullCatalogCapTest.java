package com.erg.ergspringboot.calendar.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarRoomRepository;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarCatalogResponse;
import com.erg.ergspringboot.curriculum.persistence.CurriculumLevelRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubjectRepository;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.users.UserDirectoryPort;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class CalendarFullCatalogCapTest {

    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final CalendarFullCatalogQueryUseCase catalog = new CalendarFullCatalogQueryUseCase(
            mock(CalendarEventTeacherRepository.class), classrooms, mock(CurriculumSubjectRepository.class),
            mock(CurriculumLevelRepository.class), mock(EducationUnitRepository.class), mock(CalendarRoomRepository.class),
            mock(UserDirectoryPort.class));

    private List<Classroom> classes(int count) {
        List<Classroom> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Classroom c = new Classroom();
            c.setId("c" + i);
            c.setName("Class " + i);
            list.add(c);
        }
        return list;
    }

    private CalendarCatalogResponse load(int classCount) {
        when(classrooms.findByTenantIdAndSchoolIdInOrderByNameAsc(eq("t1"), anyList(), any(Pageable.class)))
                .thenReturn(classes(classCount));
        return catalog.catalog("t1", List.of("s1"), OffsetDateTime.now(), OffsetDateTime.now().plusDays(7));
    }

    @Test
    void overTheCapTheFirst500AreKeptAndTheFlagIsSet() {
        CalendarCatalogResponse response = load(501);

        assertThat(response.classes()).hasSize(500);
        assertThat(response.classes().getFirst().id()).isEqualTo("c0");
        assertThat(response.catalogTruncated()).isTrue();
    }

    @Test
    void atTheCapNothingIsCut() {
        CalendarCatalogResponse response = load(500);

        assertThat(response.classes()).hasSize(500);
        assertThat(response.catalogTruncated()).isFalse();
    }
}
