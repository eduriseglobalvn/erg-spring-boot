import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    steady: {
      executor: 'ramping-vus',
      startVUs: 100,
      stages: [
        { duration: '10m', target: 5000 },
        { duration: '15m', target: 5000 },
        { duration: '30m', target: 5000 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    http_req_duration: ['p(95)<300', 'p(99)<800'],
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
