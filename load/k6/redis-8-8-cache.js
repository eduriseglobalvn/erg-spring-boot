import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const mode = __ENV.CACHE_MODE || 'warm';
const isAcceptance = mode === '5k';
const vus = Number(__ENV.VUS || (isAcceptance ? 5000 : 50));
const duration = __ENV.DURATION || (isAcceptance ? '30m' : '2m');
const base = (__ENV.BASE_URL || 'http://127.0.0.1:1606').replace(/\/$/, '');
const token = __ENV.ACCESS_TOKEN || 'erg-load-seed-admin-00001';
const tenant = __ENV.TENANT_ID || 'seed-tenant-01';

const acceptedErrors = new Rate('erg_accepted_errors');
const rateLimited = new Counter('erg_rate_limited');
const workflowDuration = new Trend('erg_workflow_duration', true);

const headers = {
  Authorization: `Bearer ${token}`,
  'Content-Type': 'application/json',
  'X-Tenant-ID': tenant,
};

const scenarios = mode === 'submission-burst'
  ? {
      submission_burst: {
        executor: 'per-vu-iterations', vus: 5000, iterations: 1,
        maxDuration: '60s', exec: 'submissionBurst', gracefulStop: '30s',
      },
    }
  : isAcceptance ? {
      warmup: { executor: 'ramping-vus', startVUs: 100, stages: [{ duration: '10m', target: 1000 }], gracefulStop: '30s' },
      steady: {
        executor: 'constant-vus', vus: 5000, duration,
        startTime: '10m', gracefulStop: '1m',
      },
    }
  : { [mode.replace(/[^a-z0-9_]/gi, '_')]: { executor: 'constant-vus', vus, duration, gracefulStop: '30s' } };

export const options = {
  scenarios,
  thresholds: {
    http_req_failed: isAcceptance ? ['rate<0.001'] : ['rate<0.05'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    erg_accepted_errors: isAcceptance ? ['rate<0.001'] : ['rate<0.05'],
  },
  tags: { suite: 'redis-8-8-cache', cache_mode: mode },
};

function request(method, path, body, operation) {
  const response = http.request(method, `${base}${path}`, body ? JSON.stringify(body) : null, {
    headers, tags: { operation },
  });
  const accepted = response.status >= 200 && response.status < 400;
  acceptedErrors.add(!accepted);
  if (response.status === 429) rateLimited.add(1);
  check(response, { [`${operation} accepted`]: () => accepted });
  return response;
}

function graphQl(query, variables, operationName) {
  return request('POST', '/api/v1/graphql', { query, variables, operationName }, `graphql_${operationName}`);
}

const workflows = [
  () => request('GET', __ENV.SESSION_PATH || '/api/v1/auth/session', null, 'session_bootstrap'),
  () => request('GET', __ENV.ASSIGNMENTS_PATH || '/api/v1/lms/students/me/assignments', null, 'assignment_list'),
  () => request('GET', __ENV.CALENDAR_PATH || '/api/v1/calendar/workspace?from=2026-07-01&to=2026-07-07', null, 'calendar'),
  () => graphQl(
    'query StudentDashboard($input: StudentDashboardInput) { elearning { studentDashboard(input: $input) { tenantId summary { activeAssignmentCount } } } }',
    { input: { tenantId: tenant, studentUserId: __ENV.STUDENT_USER_ID || null } }, 'StudentDashboard'),
  () => graphQl(
    'query QuestionBank($input: QuestionBankWorkspaceInput) { lcms { questionBankWorkspace(input: $input) { tenantId questions { totalItems } } } }',
    { input: { tenantId: tenant, includeQuestions: true, page: 0, size: 20 } }, 'QuestionBank'),
  () => graphQl(
    'query TeacherHomework($input: TeacherHomeworkWorkspaceInput) { lms { teacherHomeworkWorkspace(input: $input) { tenantId } } }',
    { input: { tenantId: tenant, page: 0, size: 20 } }, 'TeacherHomework'),
  () => graphQl(
    'query CacheCombined { lms { __typename } lcms { __typename } elearning { __typename } }',
    {}, 'CacheCombined'),
];

export default function () {
  const started = Date.now();
  group(`redis-${mode}`, () => workflows[Math.floor(Math.random() * workflows.length)]());
  workflowDuration.add(Date.now() - started);
  sleep(isAcceptance ? 5 + Math.random() * 10 : 1 + Math.random() * 2);
}

export function submissionBurst() {
  const attemptId = __ENV.ATTEMPT_ID || `load-attempt-${__VU}-${__ITER}`;
  request('POST', __ENV.SUBMIT_PATH || `/api/v1/lms/attempts/${attemptId}/submit`, {
    packageHash: __ENV.PACKAGE_HASH || 'load-fixture', answers: {}, clientEvents: [],
  }, 'submission_burst');
}
