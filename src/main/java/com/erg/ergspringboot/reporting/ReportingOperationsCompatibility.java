package com.erg.ergspringboot.reporting;

import com.erg.ergspringboot.reporting.internal.ReportJobUseCase;
import com.erg.ergspringboot.repository.reporting.ReportJobRepository;

/** @deprecated Repository construction seam for the legacy report-job adapter. */
@Deprecated
public final class ReportingOperationsCompatibility {
    private ReportingOperationsCompatibility() { }

    public static ReportJobPort fromRepository(ReportJobRepository repository) {
        return new ReportJobUseCase(repository);
    }
}
