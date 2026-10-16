package com.erg.ergspringboot.lcms;

import com.erg.ergspringboot.service.quiz.authoring.QuestionBankAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizDraftService;
import com.erg.ergspringboot.service.quiz.bundle.PortableQuizBundleService;
import com.erg.ergspringboot.service.quiz.media.MediaService;
import com.erg.ergspringboot.service.quiz.publish.QuizPublishService;

/** @deprecated Construction seam for legacy LCMS command tests/callers. */
@Deprecated
public final class LcmsCommandCompatibility {
    private LcmsCommandCompatibility() { }

    public static LcmsCommandPort fromServices(
            QuestionBankAuthoringService questions,
            QuizAuthoringService quizzes,
            QuizDraftService drafts,
            QuizPublishService publishing,
            MediaService media,
            PortableQuizBundleService bundles) {
        return new com.erg.ergspringboot.lcms.internal.command.LcmsCommandUseCase(
                questions, quizzes, drafts, publishing, media, bundles);
    }
}
