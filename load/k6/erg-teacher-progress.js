import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// S5 (fable/03 §7): VUS teachers keep the homework-progress screen open and refresh it every POLL_S
// seconds, run at the same time as erg-exam-session.js. The query is the one the frontend sends
// (erg-system src/features/lms/api/lms-assignment-progress-api.ts, page 0 size 50).
// Seed fixture: teacher n is homeroom teacher and assigner of class n, but only some n share the
// class's tenant (teachers 45/tenant, classes 25/tenant); VUs cycle through those classes.
const isSmoke = __ENV.SMOKE === 'true';
const pollSeconds = Number(__ENV.POLL_S || 15);

const graphqlErrors = new Rate('graphql_errors');

export const options = {
  scenarios: {
    teacherProgress: isSmoke ? {
      executor: 'shared-iterations', vus: 1, iterations: Number(__ENV.ITERATIONS || 2), maxDuration: '1m',
    } : {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 100),
      duration: __ENV.DURATION || '10m',
      gracefulStop: '30s',
    },
  },
  thresholds: isSmoke ? { http_req_failed: ['rate<0.01'], graphql_errors: ['rate<0.01'] } : {
    'http_req_duration{name:progress}': ['p(95)<800'],
    http_req_failed: ['rate<0.01'],
    graphql_errors: ['rate<0.01'],
  },
};

const query = `
query LmsAssignmentProgressWorkspace($input: AssignmentProgressWorkspaceInput!) {
  lms {
    assignmentProgressWorkspace(input: $input) {
      tenantId
      assignment { id academicClassId quizId subjectId dueAt status assignedBy recipientMode
        resources { quizId quizVersionId quizVersionLabel orderIndex } createdAt updatedAt }
      summary { submittedCount inProgressCount needsReviewCount }
      attempts {
        items { id assignmentId quizId studentId status score maxScore percent passed startedAt submittedAt updatedAt }
        page size totalItems totalPages hasNext hasPrevious
      }
      recipients {
        items { studentId schoolId academicClassId studentCode fullName username authUserId email status missing late
          latestAttempt { id assignmentId quizId studentId status score maxScore percent passed startedAt submittedAt updatedAt }
          bestAttempt { id assignmentId quizId studentId status score maxScore percent passed startedAt submittedAt updatedAt }
          updatedAt }
        page size totalItems totalPages hasNext hasPrevious
      }
      missingLateStudents {
        items { studentId schoolId academicClassId studentCode fullName username authUserId email status dueAt missing late updatedAt }
        page size totalItems totalPages hasNext hasPrevious
      }
    }
  }
}`;

const base = __ENV.BASE_URL || 'http://localhost:8080';
const assignmentSlot = __ENV.ASSIGNMENT_SLOT || '03';
function pad(value, width) { return String(value).padStart(width, '0'); }
const classTenant = (n) => Math.floor((n - 1) / 25) + 1;
const teacherTenant = (n) => Math.floor((n - 1) / 45) + 1;
const ownedClasses = Array.from({ length: 250 }, (_, i) => i + 1).filter((n) => classTenant(n) === teacherTenant(n));

export default function () {
  const classNumber = ownedClasses[(__VU - 1) % ownedClasses.length];
  const token = __ENV.ACCESS_TOKEN || `erg-load-seed-teacher-${pad(classNumber, 5)}`;
  const tenant = __ENV.TENANT_ID || `seed-tenant-${pad(classTenant(classNumber), 2)}`;
  const assignmentId = __ENV.ASSIGNMENT_ID || `seed-assignment-${pad(classNumber, 4)}-${assignmentSlot}`;
  const response = http.post(`${base}/api/v1/graphql`, JSON.stringify({
    operationName: 'LmsAssignmentProgressWorkspace',
    query,
    variables: { input: { assignmentId, page: 0, size: 50 } },
  }), {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'X-Tenant-ID': tenant },
    tags: { name: 'progress' },
  });
  let body = {};
  try { body = response.json(); } catch (_) { body = {}; }
  const ok = response.status === 200 && !body.errors && Boolean(body?.data?.lms?.assignmentProgressWorkspace);
  graphqlErrors.add(!ok);
  check(response, { 'progress workspace loads': () => ok });
  sleep(isSmoke ? 1 : pollSeconds);
}
