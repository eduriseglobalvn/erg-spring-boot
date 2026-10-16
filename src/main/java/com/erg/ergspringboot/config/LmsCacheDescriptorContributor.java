package com.erg.ergspringboot.config;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentAssignmentListResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.StudentGroupWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkWorkspaceResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorContributor;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/** Cache metadata owned by LMS, LCMS, quiz and learner runtime use cases. */
@Component
public class LmsCacheDescriptorContributor implements CacheDescriptorContributor {
    @Override
    public List<CacheDescriptor<?>> descriptors(ObjectMapper mapper) {
        JavaType untyped = mapper.constructType(Object.class);
        JavaType quizPackage = mapper.getTypeFactory().constructParametricType(Optional.class, QuizPackageResponse.class);
        return List.of(
                descriptor("lmsClassWorkspace", mapper.constructType(ClassWorkspaceResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lmsHomeworkWorkspace", mapper.constructType(TeacherHomeworkWorkspaceResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lmsAssignmentProgress", mapper.constructType(AssignmentProgressWorkspaceResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lmsStudentAssignments", mapper.constructType(StudentAssignmentListResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lmsStudentGroups", mapper.constructType(StudentGroupWorkspaceResponse.class), Duration.ofSeconds(30), 32),
                descriptor("quizRuntimePackage", quizPackage, Duration.ofMinutes(10), 256),
                descriptor("quizQuestionAnalytics", mapper.constructType(QuestionAnalyticsResponse.class), Duration.ofSeconds(30), 64),
                descriptor("quizAssignmentReport", mapper.constructType(AssignmentReportResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lcmsQuestionBankWorkspace", mapper.constructType(QuestionBankWorkspaceResponse.class), Duration.ofSeconds(30), 128),
                descriptor("lcmsQuizWorkspace", mapper.constructType(QuizWorkspaceResponse.class), Duration.ofSeconds(30), 128),
                descriptor("studentDashboard", mapper.constructType(StudentDashboardResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lmsSchoolClasses", untyped, Duration.ofSeconds(60), 64)
        );
    }

    private CacheDescriptor<Object> descriptor(String name, JavaType type, Duration ttl, long l1MiB) {
        return new CacheDescriptor<>(name, type, ttl, ttl.multipliedBy(4), l1MiB * 1024 * 1024,
                1024 * 1024, true, false);
    }
}
