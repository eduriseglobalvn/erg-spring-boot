package com.erg.ergspringboot.lcms;

import com.erg.ergspringboot.service.lcms.readmodel.LcmsQuizWorkspaceReadService;
import com.erg.ergspringboot.service.lcms.readmodel.LcmsWorkspaceReadService;
import com.erg.ergspringboot.service.quiz.report.QuizReportService;

/** @deprecated Construction seam for legacy LCMS read tests/callers. */
@Deprecated
public final class LcmsReadCompatibility {
    private LcmsReadCompatibility() { }

    public static LcmsReadPort fromServices(
            LcmsQuizWorkspaceReadService quizWorkspaces,
            LcmsWorkspaceReadService workspaces,
            QuizReportService reports) {
        return new com.erg.ergspringboot.lcms.internal.readmodel.LcmsReadModelUseCase(
                quizWorkspaces, workspaces, reports);
    }
}
