package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KubernetesPlatformContractTest {

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }

    @Test
    void helmChartLocksRollingHealthSecurityAndMultiNodeAvailability() throws Exception {
        String deployment = read("helm/erg-spring/templates/deployment.yaml");
        String pdb = read("helm/erg-spring/templates/pdb.yaml");
        String values = read("helm/erg-spring/values.yaml");
        String dockerfile = read("Dockerfile");

        assertThat(deployment)
                .contains("maxUnavailable: 0")
                .contains("maxSurge:")
                .contains("startupProbe:")
                .contains("readinessProbe:")
                .contains("livenessProbe:")
                .contains("path: /ready")
                .contains("path: /healthz")
                .contains("topologySpreadConstraints:")
                .doesNotContain("whenUnsatisfiable: DoNotSchedule")
                .contains("runAsNonRoot: true")
                .contains("runAsUser: 999")
                .contains("runAsGroup: 999")
                .contains("readOnlyRootFilesystem: true")
                .contains("allowPrivilegeEscalation: false")
                .contains("automountServiceAccountToken: false")
                .contains("terminationGracePeriodSeconds:");
        assertThat(values)
                .contains("zoneKey: topology.kubernetes.io/zone")
                .contains("hostnameKey: kubernetes.io/hostname");
        assertThat(dockerfile)
                .contains("groupadd --system --gid 999 erg")
                .contains("useradd --system --uid 999 --gid 999");
        assertThat(pdb)
                .contains("apiVersion: policy/v1")
                .contains("maxUnavailable:")
                .contains("unhealthyPodEvictionPolicy: AlwaysAllow");
    }

    @Test
    void chartOffersExactlyOneAutoscalerAndBoundsDatabaseConnections() throws Exception {
        String values = read("helm/erg-spring/values.yaml");
        String schema = read("helm/erg-spring/values.schema.json");
        String hpa = read("helm/erg-spring/templates/hpa.yaml");
        String keda = read("helm/erg-spring/templates/keda-scaledobject.yaml");
        String helpers = read("helm/erg-spring/templates/_helpers.tpl");

        assertThat(values)
                .contains("mode: hpa")
                .contains("minReplicas: 2")
                .contains("maxReplicas: 15")
                .contains("perPodMaxConnections: 8")
                .contains("postgresMaxConnections: 160")
                .contains("reservedConnections: 40");
        assertThat(schema).contains("\"enum\": [\"disabled\", \"hpa\", \"keda\"]");
        assertThat(hpa)
                .contains("autoscaling/v2")
                .contains("eq .Values.autoscaling.mode \"hpa\"")
                .contains("behavior:")
                .contains("stabilizationWindowSeconds:");
        assertThat(keda)
                .contains("keda.sh/v1alpha1")
                .contains("eq .Values.autoscaling.mode \"keda\"")
                .contains("type: prometheus")
                .contains("hikaricp_connections_pending");
        assertThat(helpers)
                .contains("mul")
                .contains("reservedConnections")
                .contains("fail");
    }

    @Test
    void chartPublishesMonitoringNetworkPolicyAndOperationalReceipts() throws Exception {
        String serviceMonitor = read("helm/erg-spring/templates/servicemonitor.yaml");
        String networkPolicy = read("helm/erg-spring/templates/networkpolicy.yaml");
        String runbook = read("docs/runbooks/kubernetes-autoscaling-self-healing.md");
        String harness = read("load/scripts/kubernetes-platform-test.ps1");
        String kind = read("ops/kubernetes/kind/cluster.yaml");

        assertThat(serviceMonitor)
                .contains("monitoring.coreos.com/v1")
                .contains("path: /actuator/prometheus");
        assertThat(networkPolicy)
                .contains("kind: NetworkPolicy")
                .contains("policyTypes:")
                .contains("Ingress")
                .contains("Egress");
        assertThat(kind)
                .contains("role: control-plane")
                .contains("role: worker");
        assertThat(harness)
                .contains("upgrade --install")
                .contains("rollout\", \"status")
                .contains("delete\", \"pod")
                .contains("\"drain\"")
                .contains("rollout-probe")
                .contains("erg-erg-spring:1606/ready")
                .doesNotContain("port-forward")
                .contains("finally")
                .contains("kind delete cluster");
        assertThat(runbook)
                .contains("Cluster Autoscaler")
                .contains("HPA")
                .contains("KEDA")
                .contains("connection")
                .contains("rollback")
                .contains("failover");
    }
}
