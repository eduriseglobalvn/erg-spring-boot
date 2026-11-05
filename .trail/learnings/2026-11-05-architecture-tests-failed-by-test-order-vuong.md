---
title: "Architecture tests failed by test order, not by chance"
author: Vuong
date: 2026-11-05
branch: claude/fix-audit-findings
cost: { minutes: 10, tokens: 25948 }
id: l-7ddef3fa
scope: repo
status: active
src: agent
touches:
  - pom.xml
  - ops/docker/deploy.sh
  - src/main/java/com/erg/ergspringboot/platform/security/ErgAuthenticationFilter.java
  - src/test/java/com/erg/ergspringboot/platform/security/SecurityHardeningTest.java
  - ops/docker/compose.monitor.yml
  - ops/docker/monitor/alertmanager/alertmanager.yml.tmpl
  - ops/docker/monitor/prometheus/alerts.yml
  - ops/docker/monitor/prometheus/prometheus.yml
---

## Decided
- The Modulith detection strategy (`explicitly-annotated`) is set for the whole surefire JVM in pom.xml, not in each test's @BeforeAll.

## Tried and ruled out
- Treating the ModulithVerificationTest / ArchitectureRatchetTest failures as flaky CI: they passed locally and in a clean Linux clone, then failed on three CI runs in a row.

## Watch out
- `ApplicationModules.of` caches its result per JVM. Whichever test builds it first fixes the strategy for every later test; with the default strategy `common`, `exception` and `platform` become modules and a cycle is reported.

In the code: pom.xml (surefire systemPropertyVariables), src/test/java/com/erg/ergspringboot/architecture/
