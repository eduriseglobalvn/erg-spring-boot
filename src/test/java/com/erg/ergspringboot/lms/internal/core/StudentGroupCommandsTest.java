package com.erg.ergspringboot.lms.internal.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.StudentGroupRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.persistence.StudentGroup;
import com.erg.ergspringboot.lms.persistence.StudentGroupMemberRepository;
import com.erg.ergspringboot.lms.persistence.StudentGroupRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StudentGroupCommandsTest {
    private final StudentGroupRepository groups = mock(StudentGroupRepository.class);
    private final StudentGroupMemberRepository members = mock(StudentGroupMemberRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final StudentGroupCommands commands = new StudentGroupCommands(groups, members, students);

    private static List<String> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> "s" + i).toList();
    }

    private StudentGroupRequest request(List<String> studentIds) {
        return new StudentGroupRequest("Nhóm A", null, null, null, studentIds);
    }

    @Test
    void unknownMemberIdsAreRejectedWithTheirCountOnly() {
        when(students.countByTenantIdAndIdIn(org.mockito.ArgumentMatchers.eq("t"), any())).thenReturn(1L);

        assertThatThrownBy(() -> commands.createGroup("t", "u", request(ids(3))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("2 ")
                .hasMessageNotContaining("s1");
        verify(groups, never()).save(any());
    }

    @Test
    void createRejectsMoreThanFiveHundredMembersWithoutQueryingTheIds() {
        assertThatThrownBy(() -> commands.createGroup("t", "u", request(ids(501)))).isInstanceOf(BadRequestException.class);
        verify(students, never()).countByTenantIdAndIdIn(any(), any());
    }

    @Test
    void anOversizedGroupMayShrinkOrStayButNotGrow() {
        StudentGroup group = new StudentGroup();
        group.setId("g");
        group.setStudentCount(700);
        when(groups.findByIdAndTenantId("g", "t")).thenReturn(Optional.of(group));
        when(groups.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(students.countByTenantIdAndIdIn(org.mockito.ArgumentMatchers.eq("t"), any())).thenAnswer(invocation ->
                (long) ((java.util.Collection<?>) invocation.getArgument(1)).size());

        assertThatCode(() -> commands.updateGroup("t", "g", request(ids(650)))).doesNotThrowAnyException();
        assertThat(group.getStudentCount()).isEqualTo(650);
        assertThatThrownBy(() -> commands.updateGroup("t", "g", request(ids(651)))).isInstanceOf(BadRequestException.class);
    }
}
