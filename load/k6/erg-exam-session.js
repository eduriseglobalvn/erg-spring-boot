import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// S3 (fable/03 §7): one VU = one student sitting an exam. Open the home list, start the attempt,
// save a draft every DRAFT_INTERVAL_S seconds for EXAM_MINUTES carrying ONLY the answers changed since
// the last successful save (the PERF-04 client behaviour; no `events` in the draft body), then submit.
// CHANGE_PROB = chance a student changed an answer within one interval: 0.3 at 2000 VUs / 5 s is the
// <= 120 draft writes/s target of the workload model. Thresholds follow its "Write" and "Cacheable read" rows.
// Seed fixture: load/fixtures/seed-postgres-scale.sql (student n is in class ceil(n/18), tenant ceil(n/450)).
const isSmoke = __ENV.SMOKE === 'true';
const vus = Number(__ENV.VUS || 2000);
const rampSeconds = Number(__ENV.RAMP_SECONDS || 60);
const examMinutes = Number(__ENV.EXAM_MINUTES || 10);
const draftIntervalSeconds = Number(__ENV.DRAFT_INTERVAL_S || 5);
const changeProbability = Number(__ENV.CHANGE_PROB || 0.3);
const questionCount = 50;

const submitted = new Counter('exam_submit_acknowledged');

export const options = {
  scenarios: {
    exam: {
      executor: 'per-vu-iterations',
      vus: isSmoke ? 1 : vus,
      iterations: 1,
      maxDuration: `${rampSeconds + examMinutes * 60 + 300}s`,
    },
  },
  // Smoke keeps only error thresholds, like erg-5k-steady.js: a cold JVM is not a latency sample.
  thresholds: isSmoke ? { http_req_failed: ['rate<0.01'] } : {
    'http_req_duration{name:home}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{name:package}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{name:start}': ['p(95)<800', 'p(99)<1500'],
    'http_req_duration{name:draft}': ['p(95)<800', 'p(99)<1500'],
    'http_req_duration{name:submit}': ['p(95)<800', 'p(99)<1500'],
    'http_req_failed{name:draft}': ['rate<0.01'],
    'http_req_failed{name:submit}': ['rate<0.001'],
    http_req_failed: ['rate<0.01'],
  },
};

const base = __ENV.BASE_URL || 'http://localhost:8080';
// Slots 01-02 hold the submission-burst fixture attempts; 03..40 start a fresh attempt (limit 3 per slot).
const assignmentSlot = __ENV.ASSIGNMENT_SLOT || '03';
const studentOffset = Number(__ENV.STUDENT_OFFSET || 0);
function pad(value, width) { return String(value).padStart(width, '0'); }

export default function () {
  const studentNumber = ((__VU - 1 + studentOffset) % 4500) + 1;
  const tenantNumber = Math.floor((studentNumber - 1) / 450) + 1;
  const classNumber = Math.floor((studentNumber - 1) / 18) + 1;
  const token = __ENV.ACCESS_TOKEN || `erg-load-seed-student-${pad(studentNumber, 5)}`;
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    'X-Tenant-ID': __ENV.TENANT_ID || `seed-tenant-${pad(tenantNumber, 2)}`,
  };
  const runId = `${__ENV.RUN_ID || 'k6'}-${__VU}`;

  if (!isSmoke) sleep(Math.random() * rampSeconds);

  const home = http.get(`${base}/api/v1/lms/students/me/assignments?page=0&size=20`, { headers, tags: { name: 'home' } });
  check(home, { 'home list loads': (r) => r.status === 200 });

  const assignmentId = __ENV.ASSIGNMENT_ID || `seed-assignment-${pad(classNumber, 4)}-${assignmentSlot}`;
  const quizId = __ENV.QUIZ_ID || `seed-quiz-${pad(tenantNumber, 2)}`;
  // Like the player: fetch the runtime package, then start with its id and content hash.
  const pkg = http.get(`${base}/api/v1/lms/quizzes/${quizId}/package?assignmentId=${assignmentId}`,
    { headers, tags: { name: 'package' } });
  const packageId = pkg.status === 200 ? pkg.json('data.id') : null;
  const packageHash = pkg.status === 200 ? pkg.json('data.contentHash') : null;
  if (!check(pkg, { 'package loads': () => Boolean(packageId && packageHash) })) return;
  const start = http.post(`${base}/api/v1/lms/attempts`, JSON.stringify({ assignmentId, quizId, packageId, packageHash }), { headers: { ...headers, 'X-Idempotency-Key': `${runId}-start` }, tags: { name: 'start' } });
  const attemptId = start.status === 200 ? start.json('data.id') : null;
  if (!check(start, { 'attempt starts': () => Boolean(attemptId) })) return;

  const answers = {};
  let pending = {};
  const ticks = isSmoke ? 2 : Math.floor((examMinutes * 60) / draftIntervalSeconds);
  for (let tick = 0; tick < ticks; tick += 1) {
    sleep(draftIntervalSeconds);
    if (isSmoke || Math.random() < changeProbability) {
      const questionId = `runtime-${tenantNumber}-${Math.floor(Math.random() * questionCount) + 1}`;
      answers[questionId] = { choiceId: Math.random() < 0.8 ? 'a' : 'b' };
      pending[questionId] = answers[questionId];
    }
    if (Object.keys(pending).length === 0) continue; // nothing changed: the client sends nothing.
    const operationId = `${runId}-draft-${tick}`;
    const draft = http.patch(`${base}/api/v1/lms/attempts/${attemptId}/draft`,
      JSON.stringify({ answers: pending, packageHash, operationId }),
      { headers: { ...headers, 'X-Idempotency-Key': operationId }, tags: { name: 'draft' } });
    // Unsaved changes stay pending and ride along with the next interval, as in the player.
    if (check(draft, { 'draft saved': (r) => r.status === 200 })) pending = {};
  }

  const submitKey = `${runId}-submit`;
  const submit = http.post(`${base}/api/v1/lms/attempts/${attemptId}/submit`,
    JSON.stringify({ answers, packageHash, idempotencyKey: submitKey }),
    { headers: { ...headers, 'X-Idempotency-Key': submitKey }, tags: { name: 'submit' } });
  // Reconcile this counter with submitted lms_attempts rows: no submission may be lost.
  if (check(submit, { 'submission acknowledged': (r) => r.status === 200 })) submitted.add(1);
}
