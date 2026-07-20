import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: 5000,
      iterations: 1,
      maxDuration: '60s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    http_req_duration: ['p(95)<1000'],
  },
};

const base = __ENV.BASE_URL || 'http://localhost:8080';
function pad(value, width) { return String(value).padStart(width, '0'); }

export default function () {
  const studentNumber = ((__VU - 1) % 4500) + 1;
  const tenantNumber = Math.floor((studentNumber - 1) / 450) + 1;
  const token = __ENV.ACCESS_TOKEN || `erg-load-seed-student-${pad(studentNumber, 5)}`;
  const attemptId = __ENV.ATTEMPT_ID || `load-attempt-${__VU}-${__ITER}`;
  const response = http.post(`${base}/api/v1/lms/attempts/${attemptId}/submit`,
    JSON.stringify({ answers: {}, clientMutationId: `load-${__VU}-${__ITER}` }),
    { headers: {
      Authorization: `Bearer ${token}`, 'Content-Type': 'application/json',
      'X-Tenant-ID': __ENV.TENANT_ID || `seed-tenant-${pad(tenantNumber, 2)}`,
    } });
  check(response, { 'submission acknowledged': (r) => r.status >= 200 && r.status < 300 });
}
