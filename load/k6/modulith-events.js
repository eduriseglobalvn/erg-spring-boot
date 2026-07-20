import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: { events: { executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s', duration: '2m', preAllocatedVUs: 32, maxVUs: 128 } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<800'] },
};

const base = __ENV.BASE_URL || 'http://localhost:8080';
const token = __ENV.ACCESS_TOKEN || '';
export function setup() { return { base, token }; }
export function teardown() { /* compare publication lag and rows with the benchmark harness */ }

export default function (data) {
  const response = http.post(`${data.base}/api/v1/calendar/batches/${__ENV.BATCH_ID || 'load-batch'}/publish`, null, {
    headers: { Authorization: data.token ? `Bearer ${data.token}` : undefined, 'X-Tenant-ID': __ENV.TENANT_ID || 'seed-tenant-01' },
    tags: { route: 'modulith_business_event' },
  });
  check(response, { 'business command accepted': (r) => r.status >= 200 && r.status < 300 || r.status === 409 });
}
