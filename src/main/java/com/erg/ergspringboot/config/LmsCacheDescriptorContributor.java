package com.erg.ergspringboot.config;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentAssignmentListResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyFramesResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyTierResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierCutoffsResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.StudentGroupWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkWorkspaceResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;
import com.erg.ergspringboot.lms.LmsStudentIdentityLookup.StudentIdentity;
import com.erg.ergspringboot.lms.QuizStartInfo;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorContributor;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/** Cache metadata owned by LMS, LCMS, quiz and learner runtime use cases. */
@Component
public class LmsCacheDescriptorContributor implements CacheDescriptorContributor {
    @Override
    public List<CacheDescriptor<?>> descriptors(ObjectMapper mapper) {
        // activePackage returns Optional, but Spring unwraps it before put(): the cached value is the bare response.
        JavaType quizPackage = mapper.constructType(QuizPackageResponse.class);
        return List.of(
                descriptor("lmsClassWorkspace", mapper.constructType(ClassWorkspaceResponse.class), Duration.ofSeconds(30), 64,
                        keyPart(1)),
                descriptor("lmsHomeworkWorkspace", mapper.constructType(TeacherHomeworkWorkspaceResponse.class), Duration.ofSeconds(30), 64, keyPart(1)),
                descriptor("lmsAssignmentProgress", mapper.constructType(AssignmentProgressWorkspaceResponse.class), Duration.ofSeconds(30), 64, keyPart(1)),
                descriptor("lmsStudentAssignments", mapper.constructType(StudentAssignmentListResponse.class), Duration.ofSeconds(30), 64),
                descriptor("lmsStudentGroups", mapper.constructType(StudentGroupWorkspaceResponse.class), Duration.ofSeconds(30), 32),
                descriptor("quizRuntimePackage", quizPackage, Duration.ofMinutes(10), 256),
                // What an attempt start needs from the quiz and its package; evicted wherever quizRuntimePackage is.
                descriptor("quizStartInfo", mapper.constructType(QuizStartInfo.class), Duration.ofMinutes(10), 16),
                // login -> student; scoped by the login so a student write evicts exactly that learner. "No student" is a value too.
                descriptor("lmsStudentIdentity", mapper.constructType(StudentIdentity.class), Duration.ofSeconds(60), 16, keyPart(1)),
                descriptor("quizQuestionAnalytics", mapper.constructType(QuestionAnalyticsResponse.class), Duration.ofSeconds(30), 64, keyPart(1)),
                descriptor("quizAssignmentReport", mapper.constructType(AssignmentReportResponse.class), Duration.ofSeconds(30), 64, keyPart(1)),
                descriptor("lcmsQuestionBankWorkspace", mapper.constructType(QuestionBankWorkspaceResponse.class), Duration.ofSeconds(30), 128),
                descriptor("lcmsQuizWorkspace", mapper.constructType(QuizWorkspaceResponse.class), Duration.ofSeconds(30), 128),
                descriptor("studentDashboard", mapper.constructType(StudentDashboardResponse.class), Duration.ofSeconds(30), 64,
                        // The dashboard shows one student: the requested one, else the viewer.
                        key -> Optional.ofNullable(keyPart(3).apply(key)).orElseGet(() -> keyPart(0).apply(key))),
                // Per-student reads keyed {tenantId, userId}: one student's hand-in/frame choice clears only that student.
                descriptor("studentTier", mapper.constructType(MyTierResponse.class), Duration.ofSeconds(60), 16, keyPart(1)),
                descriptor("studentFrames", mapper.constructType(MyFramesResponse.class), Duration.ofSeconds(60), 16, keyPart(1)),
                // Cutoff cells of a school/grade cohort; the weekly recompute clears the school.
                descriptor("tierCutoffs", mapper.constructType(TierCutoffsResponse.class), Duration.ofMinutes(10), 16, keyPart(1))
        );
    }

    private CacheDescriptor<Object> descriptor(String name, JavaType type, Duration ttl, long l1MiB) {
        return descriptor(name, type, ttl, l1MiB, null);
    }

    /** Scoped caches let one write (an attempt) invalidate its teacher/assignment/quiz/student instead of the tenant. */
    private CacheDescriptor<Object> descriptor(String name, JavaType type, Duration ttl, long l1MiB, Function<Object, String> scope) {
        return new CacheDescriptor<>(name, type, ttl, ttl.multipliedBy(4), l1MiB * 1024 * 1024,
                1024 * 1024, true, false, scope);
    }

    /** Element {@code index} of an {@code @Cacheable} list key such as {@code {#tenantId, #assignmentId, ...}}. */
    static Function<Object, String> keyPart(int index) {
        return key -> key instanceof List<?> parts && parts.size() > index && parts.get(index) != null
                ? parts.get(index).toString() : null;
    }
}
