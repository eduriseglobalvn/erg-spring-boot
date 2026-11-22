import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

// CCU students online, each sending one request every THINK_S seconds => CCU / THINK_S requests per second.
// Open model (arrival rate): a slow server does not slow the arrivals down, it shows up as latency or drops.
const ccu = Number(__ENV.CCU || 5000);
const thinkS = Number(__ENV.THINK_S || 10);
const rate = Math.max(1, Math.round(ccu / thinkS));
// shortcut: the fixture has 4,500 students, so above 4,500 CCU tokens repeat; seed more students to test distinct sessions beyond that.
const users = Math.min(ccu, 4500);

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration: __ENV.DURATION || '3m',
      preAllocatedVUs: Math.min(1000, Math.max(50, rate)),
      maxVUs: 4000,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    http_req_duration: ['p(95)<300', 'p(99)<800'],
    dropped_iterations: ['count<1'],
  },
};

const base = __ENV.BASE_URL || 'http://localhost:8080';
function pad(value, width) { return String(value).padStart(width, '0'); }

export default function () {
  // Random pick: real students do not take turns, and fixed turns make every cache entry expire in the same pass.
  const studentNumber = __ENV.ROUND_ROBIN === 'true' ? (exec.scenario.iterationInTest % users) + 1 : Math.floor(Math.random() * users) + 1;
  const tenantNumber = Math.floor((studentNumber - 1) / 450) + 1;
  const response = http.get(`${base}/api/v1/lms/students/me/assignments`, {
    headers: {
      Authorization: `Bearer erg-load-seed-student-${pad(studentNumber, 5)}`,
      'X-Tenant-ID': `seed-tenant-${pad(tenantNumber, 2)}`,
    },
    tags: { route: 'student_assignments' },
  });
  check(response, { 'assignment read succeeds': (r) => r.status >= 200 && r.status < 300 });
}
