package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CiPortalGatesContractTest {

    @Test
    void ciRunsAndPublishesIndependentPortalSuitesAndReleaseGates() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/ci.yml"));

        assertThat(workflow)
                .contains("portal-suites:")
                .contains("portal: [lms, lcms, crm, elearning]")
                .contains("portal-test-reports-${{ matrix.portal }}")
                .contains("load/k6/erg-5k-steady.js")
                .contains("load/k6/erg-5k-submission-burst.js")
                .contains("load/k6/outbox-dispatch.js")
                .contains("load/k6/modulith-events.js")
                .contains("native:compile");
    }

    @Test
    void portalSuitesRunBothContractAndIntegrationTestsPerPortal() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/ci.yml"));

        assertThat(workflow)
                // Each portal leg runs surefire (contract) AND failsafe (integration) via `verify`,
                // scoped by a contract glob and an integration glob, so integration coverage is
                // reported per portal rather than only in the global verify job.
                .contains("-Dit.test=\"${{ matrix.itTests }}\"")
                .contains("verify")
                .contains("itTests:")
                .contains("*Lms*IntegrationTest")
                .contains("*StudentDashboard*IntegrationTest")
                // An empty integration glob (e.g. CRM) must not fail the leg.
                .contains("-Dsurefire.failIfNoSpecifiedTests=false")
                .contains("-Dfailsafe.failIfNoSpecifiedTests=false")
                // A Postgres service backs the portal integration tests.
                .contains("ERG_TEST_POSTGRES_URL")
                // Both report directories are published for independent per-portal failure analysis.
                .contains("target/surefire-reports")
                .contains("target/failsafe-reports");
    }

    // BE-018 merge gate: the mandatory quality jobs must exist, trigger on pull_request, and no job
    // may silently bypass a failure with continue-on-error. This locks the gate so a future edit can't
    // quietly drop compile/test/contract/dependency coverage from the merge check.
    @Test
    void mandatoryQualityGatesRunOnPullRequestAndAreNotBypassed() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/ci.yml"));

        assertThat(workflow)
                .contains("pull_request:")
                .contains("verify:")
                .contains("portal-suites:")
                .contains("openapi-contract:")
                .contains("dependency-and-secret-scan:")
                // No job may swallow its own failure — that would let a red gate merge green.
                .doesNotContain("continue-on-error");
    }

    @Test
    void contractJobBootsRuntimeBeforeCheckingPublishedSnapshots() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/ci.yml"));
        int runtimeGate = workflow.indexOf(
                "-Dtest=OpenApiRuntimeSnapshotIntegrationTest,GraphQlCoverageCatalogIntegrationTest");
        int snapshotGate = workflow.indexOf(
                "node scripts/export-openapi-contract.cjs --file contracts/openapi/erg-openapi.json --check");

        assertThat(runtimeGate).isGreaterThan(0);
        assertThat(snapshotGate).isGreaterThan(runtimeGate);
    }
}
