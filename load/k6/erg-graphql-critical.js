import http from 'k6/http';
import { check, sleep } from 'k6';

const isSmoke = __ENV.SMOKE === 'true';
const isWarmup = __ENV.WARMUP === 'true';
const targetVus = Number(__ENV.TARGET_VUS || 0);
const stepDuration = __ENV.STEP_DURATION || '5m';

const fullStages = [
  { duration: '3m', target: 100 },
  { duration: '5m', target: 500 },
  { duration: '5m', target: 1000 },
  { duration: '10m', target: 5000 },
  { duration: '30m', target: 5000 },
];

export const options = {
  scenarios: {
    graphqlCritical: isSmoke ? {
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
      stages: fullStages,
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

const query = `
  query LmsNamespace {
    lms {
      namespace
      status
    }
  }
`;

function pad(value, width) {
  return String(value).padStart(width, '0');
}

export function setup() {
  return {
    base: __ENV.BASE_URL || 'http://localhost:8080',
    token: __ENV.ACCESS_TOKEN || '',
    tenant: __ENV.TENANT_ID || '',
  };
}

export default function (data) {
  const studentNumber = ((__VU - 1) % 4500) + 1;
  const tenantNumber = Math.floor((studentNumber - 1) / 450) + 1;
  const token = data.token || `erg-load-seed-student-${pad(studentNumber, 5)}`;
  const tenant = data.tenant || `seed-tenant-${pad(tenantNumber, 2)}`;
  const response = http.post(
    data.base + '/api/v1/graphql',
    JSON.stringify({ operationName: 'LmsNamespace', query, variables: {} }),
    {
      headers: {
        Authorization: 'Bearer ' + token,
        'Content-Type': 'application/json',
        'X-Tenant-ID': tenant,
      },
      tags: { route: 'graphql_lms_namespace' },
    },
  );
  let body = {};
  try {
    body = response.json();
  } catch (_) {
    body = {};
  }
  check(response, {
    'GraphQL returns HTTP 200': (result) => result.status === 200,
    'GraphQL has no errors': () => !body.errors,
    'LMS namespace is available': () => body?.data?.lms?.namespace === 'lms',
    'LMS status is available': () => Boolean(body?.data?.lms?.status),
  });
  sleep(1);
}
