package com.erg.ergspringboot.elearning;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.QuestionThreadResponse;
import com.erg.ergspringboot.elearning.internal.engagement.StudentQuestionThreadService;
import com.erg.ergspringboot.elearning.internal.engagement.StudentQuestionThreadService.Actor;
import com.erg.ergspringboot.elearning.internal.engagement.StudentQuestionThreadService.AskCommand;
import java.util.List;
import org.springframework.stereotype.Service;

/** Module entry point of the Hộp thư question threads (student asks, teacher answers). */
@Service
public class StudentQuestionThreadOperations {

    private final StudentQuestionThreadService threads;

    public StudentQuestionThreadOperations(StudentQuestionThreadService threads) {
        this.threads = threads;
    }

    public QuestionThreadResponse ask(String tenantId, String userId, String idempotencyKey, String assignmentId, int questionIndex,
                                      String questionPrompt, String studentAnswerText, String message, List<String> attachmentIds) {
        return threads.ask(new Actor(tenantId, userId, false), new AskCommand(idempotencyKey, assignmentId, questionIndex,
                questionPrompt, studentAnswerText, message, attachmentIds));
    }

    public QuestionThreadResponse send(String tenantId, String userId, boolean tenantWide, String threadId, String message,
                                       List<String> attachmentIds) {
        return threads.send(new Actor(tenantId, userId, tenantWide), threadId, message, attachmentIds);
    }

    public List<QuestionThreadResponse> list(String tenantId, String userId, boolean tenantWide, boolean teacherSide, String status) {
        return threads.list(new Actor(tenantId, userId, tenantWide), teacherSide, status);
    }

    public boolean markRead(String tenantId, String userId, String threadId) {
        return threads.markRead(new Actor(tenantId, userId, false), threadId);
    }

    public QuestionThreadResponse detail(String tenantId, String userId, boolean tenantWide, String threadId) {
        return threads.detail(new Actor(tenantId, userId, tenantWide), threadId);
    }
}
