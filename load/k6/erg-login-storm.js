import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

// S2 (fable/03 §7): the start-of-period login storm. LOGINS logins arrive on a rate that climbs
// linearly from 0 over RAMP, all from the one IP of this load generator (school NAT), so any 429 here
// is the per-IP limiter rejecting a real classroom. Thresholds follow the "Login" row of
// docs/performance/2026-07-workload-model.md. Seed users carry a fake hash: see load/README.md
// for setting a test-only password on the benchmark DB before running this.
const isSmoke = __ENV.SMOKE === 'true';
const logins = Number(__ENV.LOGINS || 2000);
const rampSeconds = Number(__ENV.RAMP_SECONDS || 60);
// Linear ramp 0 -> peak delivers peak * ramp / 2 iterations, so peak = 2 * logins / ramp.
const peakRate = Math.max(1, Math.ceil((2 * logins) / rampSeconds));

const rateLimited = new Counter('login_rate_limited');

export const options = {
  scenarios: {
    loginStorm: isSmoke ? {
      executor: 'shared-iterations', vus: 1, iterations: Number(__ENV.ITERATIONS || 3), maxDuration: '1m',
    } : {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || Math.min(logins, 500)),
      maxVUs: Number(__ENV.MAX_VUS || logins),
      stages: [{ duration: `${rampSeconds}s`, target: peakRate }],
      gracefulStop: '60s',
    },
  },
  thresholds: isSmoke ? { 'http_req_failed{name:login}': ['rate<0.01'], login_rate_limited: ['count<1'] } : {
    'http_req_duration{name:login}': ['p(95)<1500', 'p(99)<3000'],
    'http_req_failed{name:login}': ['rate<0.01'],
    login_rate_limited: ['count<1'],
  },
};

const base = __ENV.BASE_URL || 'http://localhost:8080';
const userPrefix = __ENV.USER_PREFIX || 'seed-student-';
const userCount = Number(__ENV.USER_COUNT || 4500);
const userOffset = Number(__ENV.USER_OFFSET || 0);
function pad(value, width) { return String(value).padStart(width, '0'); }

export function setup() {
  if (!__ENV.LOGIN_PASSWORD) throw new Error('LOGIN_PASSWORD (test-only) is required; see load/README.md');
  return { password: __ENV.LOGIN_PASSWORD };
}

export default function (data) {
  const studentNumber = ((exec.scenario.iterationInTest + userOffset) % userCount) + 1;
  const tenantNumber = Math.floor((studentNumber - 1) / 450) + 1;
  const deviceId = `k6-login-${studentNumber}`;
  const response = http.post(`${base}/api/v1/auth/login`, JSON.stringify({
    identifier: `${userPrefix}${pad(studentNumber, 5)}`,
    password: data.password,
    portal: 'lms',
    deviceId,
    deviceName: 'k6 login storm',
    deviceFingerprint: deviceId,
    rememberMe: false,
  }), {
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-ID': __ENV.TENANT_ID || `seed-tenant-${pad(tenantNumber, 2)}`,
    },
    tags: { name: 'login' },
  });
  if (response.status === 429) rateLimited.add(1);
  check(response, {
    'login succeeds': (r) => r.status === 200,
    'login returns an access token': (r) => Boolean(r.status === 200 && r.json('data.accessToken')),
  });
}
