import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: { burst: { executor: 'per-vu-iterations', vus: 100, iterations: 10, maxDuration: '60s' } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500'] },
};

const base = __ENV.BASE_URL || 'http://localhost:8080';
const provider = __ENV.WEBHOOK_PROVIDER || 'telegram';
const secret = __ENV.WEBHOOK_SECRET || 'load-test-secret';
export function setup() { return { base, provider, secret }; }
export function teardown() { /* restore provider fixtures in the benchmark harness */ }

export default function (data) {
  const body = JSON.stringify({ update_id: (__VU * 1000000) + __ITER, message: { text: 'load-test' } });
  const response = http.post(`${data.base}/webhooks/${data.provider}`, body, {
    headers: {
      'Content-Type': 'application/json',
      'X-Telegram-Bot-Api-Secret-Token': data.secret,
      'X-Webhook-Id': `k6-${__VU}-${__ITER}`,
    },
    tags: { route: 'webhook_ingress', provider },
  });
  check(response, { 'webhook accepted': (r) => r.status === 202 });
}
