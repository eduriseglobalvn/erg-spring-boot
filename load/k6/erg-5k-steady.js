import http from 'k6/http';
import { check, sleep } from 'k6';

const isSmoke = __ENV.SMOKE === 'true';
const isWarmup = __ENV.WARMUP === 'true';
const targetVus = Number(__ENV.TARGET_VUS || 0);
const stepDuration = __ENV.STEP_DURATION || '5m';

// Versioned steady-read SLO baseline (BE-015). The load ladder climbs the agreed representative
// steps 100 -> 500 -> 1,000 -> 5,000 VUs with a hold at each so p50/p95/p99 can be recorded per
// step, not just at peak. Thresholds mirror the "Cacheable read" row of docs/performance/
// 2026-07-workload-model.md and are enforced by K6SteadyLoadBaselineContractTest.
export const options = {
  scenarios: {
    steady: isSmoke ? {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [{ duration: '10s', target: 1 }],
      gracefulRampDown: '30s',
    } : targetVus > 0 ? {
      executor: 'constant-vus',
      vus: targetVus,
      duration: stepDuration,
      gracefulStop: '30s',
    } : {
      executor: 'ramping-vus',
      startVUs: 100,
      stages: [
        { duration: '3m', target: 100 },
        { duration: '5m', target: 500 },
        { duration: '5m', target: 1000 },
        { duration: '10m', target: 5000 },
        { duration: '30m', target: 5000 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: isSmoke || isWarmup ? {
    http_req_failed: ['rate<0.001'],
  } : {
    http_req_failed: ['rate<0.001'],
    http_req_duration: ['p(50)<150', 'p(95)<300', 'p(99)<800'],
  },
};

const base = __ENV.BASE_URL || 'http://localhost:8080';
export function setup() { return { base, token: __ENV.ACCESS_TOKEN || '' }; }
export function teardown() { /* metrics are collected by k6 and the benchmark harness */ }
function pad(value, width) { return String(value).padStart(width, '0'); }

export default function (data) {
  const studentNumber = ((__VU - 1) % 4500) + 1;
  const tenantNumber = Math.floor((studentNumber - 1) / 450) + 1;
  const token = data.token || `erg-load-seed-student-${pad(studentNumber, 5)}`;
  const response = http.get(`${base}/api/v1/lms/students/me/assignments`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Tenant-ID': __ENV.TENANT_ID || `seed-tenant-${pad(tenantNumber, 2)}`,
    },
    tags: { route: 'student_assignments' },
  });
  check(response, { 'assignment read succeeds': (r) => r.status >= 200 && r.status < 300 });
  sleep(1);
}
