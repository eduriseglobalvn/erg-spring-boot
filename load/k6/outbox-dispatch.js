import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: { dispatch: { executor: 'constant-vus', vus: 16, duration: '2m' } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<800'] },
};

const base = __ENV.BASE_URL || 'http://localhost:8080';
const token = __ENV.ACCESS_TOKEN || '';
export function setup() { return { base, token }; }
export function teardown() { /* verify backlog/duplicate counters in the benchmark harness */ }

export default function (data) {
  const response = http.post(`${data.base}/api/v1/admin/notifications/outbox/run`, null, {
    headers: {
      Authorization: data.token ? `Bearer ${data.token}` : undefined,
      'X-Tenant-ID': __ENV.TENANT_ID || 'seed-tenant-01',
    },
    tags: { route: 'notification_outbox_dispatch' },
  });
  check(response, { 'outbox dispatch accepted': (r) => r.status >= 200 && r.status < 300 });
}
