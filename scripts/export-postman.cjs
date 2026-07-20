const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const openApiPath = path.join(root, "contracts/openapi/erg-openapi.json");
const outDir = path.join(root, "postman");
const fullOut = path.join(outDir, "erg-spring-full.postman_collection.json");
const authOut = path.join(outDir, "erg-spring-auth.postman_collection.json");
const envOut = path.join(outDir, "erg-spring-local.postman_environment.json");

const openapi = JSON.parse(fs.readFileSync(openApiPath, "utf8").replace(/^\uFEFF/, ""));
const schemas = openapi.components?.schemas ?? {};

const SAMPLE_BY_NAME = {
  id: "demo-id",
  userId: "user_demo_001",
  roleId: "role_admin",
  overrideId: "override_demo_001",
  quizId: "quiz_demo_001",
  attemptId: "attempt_demo_001",
  questionId: "question_demo_001",
  assignmentId: "assignment_demo_001",
  subjectId: "subject_demo_001",
  resourceId: "resource_demo_001",
  postId: "post_demo_001",
  jobId: "job_demo_001",
  key: "site-title",
  slug: "demo-slug",
  subdomain: "demo",
  provider: "google",
  action: "test",
  filename: "image-demo.png",
  kind: "subject",
};

const QUERY_SAMPLE_BY_NAME = {
  page: "1",
  limit: "20",
  size: "20",
  search: "demo",
  status: "active",
  role: "teacher",
  email: "teacher@example.com",
  state: "postman-state",
  redirect_uri: "http://localhost:3000/oauth/callback",
  code: "oauth-code-demo",
  type: "school",
  centerId: "center_demo_001",
  classId: "class_demo_001",
  subjectId: "subject_demo_001",
  parentId: "subject_demo_001",
  schoolId: "{{schoolId}}",
  academicYear: "{{academicYear}}",
  from: "2026-05-01",
  to: "2026-05-21",
  format: "xlsx",
};

const COLLECTION_VARIABLES = [
  { key: "baseUrl", value: "http://localhost:8080" },
  { key: "accessToken", value: "" },
  { key: "refreshToken", value: "" },
  { key: "tenantId", value: "erg" },
  { key: "requestId", value: "{{$guid}}" },
  { key: "sessionId", value: "" },
  { key: "schoolId", value: "school-binh-an" },
  { key: "academicYear", value: "2025-2026" },
  { key: "taxonomyNodeId", value: "subject_demo_001" },
  { key: "subjectId", value: "subject_demo_001" },
  { key: "groupId", value: "group_demo_001" },
  { key: "lessonId", value: "lesson_demo_001" },
  { key: "resourceId", value: "resource_demo_001" },
  { key: "assetId", value: "asset_demo_001" },
];

const BODY_OVERRIDES = [
  [/\/auth\/register$/, { email: "teacher@example.com", password: "Password@123", fullName: "Teacher Demo" }],
  [/\/auth\/login$/, { email: "teacher@example.com", password: "Password@123", deviceId: "postman-device", deviceName: "Postman", deviceFingerprint: "postman-local", rememberMe: true }],
  [/\/auth\/refresh$/, { refreshToken: "{{refreshToken}}", deviceId: "postman-device" }],
  [/\/auth\/verify-pin$/, { email: "teacher@example.com", code: "123456", purpose: "register" }],
  [/\/auth\/resend-pin$/, { email: "teacher@example.com", purpose: "register" }],
  [/\/auth\/forgot-password$/, { email: "teacher@example.com" }],
  [/\/auth\/reset-password$/, { email: "teacher@example.com", code: "123456", new_password: "NewPassword@123" }],
  [/\/auth\/google\/login$/, { email: "teacher@example.com", fullName: "Teacher Demo", googleSub: "google-sub-demo", emailVerified: true, deviceId: "postman-device", deviceName: "Postman" }],
  [/\/auth\/oauth\/apple$/, { email: "teacher@example.com", providerId: "apple-sub-demo", fullName: "Teacher Demo", emailVerified: true, deviceId: "postman-device", deviceName: "Postman" }],
  [/\/roles$/, { name: "lms.teacher", description: "Teacher can manage assigned LMS classes", permissions: ["lms.teacher", "lms.assignment.read", "lms.report.read"] }],
  [/\/roles\/\{roleId\}$/, { name: "lms.teacher", description: "Updated teacher role", permissions: ["lms.teacher", "lms.assignment.*", "lms.report.read"] }],
  [/\/users\/roles\/bulk$/, { userIds: ["user_demo_001", "user_demo_002"], roleId: "role_teacher" }],
  [/\/users\/\{userId\}\/preview$/, { changes: { roles: ["lms.teacher"], permissions: ["lms.assignment.read"] } }],
  [/\/users$/, { email: "new.user@example.com", password: "Password@123", fullName: "New User", roles: ["teacher"], phone: "0900000000" }],
  [/\/users\/\{userId\}\/status$/, { status: "active" }],
  [/\/users\/bulk\/status$/, { user_ids: ["user_demo_001", "user_demo_002"], status: "inactive" }],
  [/\/users\/bulk$/, { user_ids: ["user_demo_001", "user_demo_002"] }],
  [/\/users\/\{userId\}\/roles$/, { roles: ["teacher", "lms.teacher"] }],
  [/\/users\/\{userId\}\/permissions$/, { permissions: ["lms.assignment.read"], grant_type: "allow", reason: "Temporary assignment access", expires_at: "2026-12-31T23:59:59Z" }],
  [/\/users\/me\/password$/, { old_password: "Password@123", new_password: "NewPassword@123" }],
  [/\/users\/onboarding$/, { fullName: "Teacher Demo", phone: "0900000000", bio: "LMS teacher", gender: "other", date_of_birth: "1990-01-01", address: "Ho Chi Minh City", city: "Ho Chi Minh", district: "District 1", job_title: "Teacher", region: "VN" }],
  [/\/profiles$/, { user_id: "user_demo_001", full_name: "Teacher Demo", bio: "Teacher profile", phone: "0900000000", date_of_birth: "1990-01-01", gender: "other", address: "Ho Chi Minh City", city: "Ho Chi Minh", district: "District 1", social_links: { facebook: "https://facebook.com/demo" }, avatar_url: "https://example.com/avatar.png", teaching_philosophy: "Student-centered learning", specialties: { primary: "Math" }, internal_note: "Created from Postman" }],
  [/\/profiles\/\{userId\}$/, { full_name: "Teacher Demo Updated", bio: "Updated profile", phone: "0900000001", city: "Ha Noi", social_links: { linkedin: "https://linkedin.com/in/demo" }, avatar_url: "https://example.com/avatar-updated.png" }],
  [/\/lms\/attempts$/, { assignmentId: "assignment_demo_001", quizId: "quiz_demo_001", packageId: "package_demo_001", packageHash: "sha256-demo" }],
  [/\/lms\/attempts\/\{attemptId\}\/draft$/, { answers: { question_demo_001: { answer: "A", answeredAt: "2026-05-21T09:00:00Z" } }, packageHash: "sha256-demo", quizVersion: "1", events: [{ type: "answer.changed", questionId: "question_demo_001", at: "2026-05-21T09:00:00Z" }], client: { platform: "web", offline: false } }],
  [/\/lms\/attempts\/\{attemptId\}\/answers\/\{questionId\}$/, { answer: "A", clientResult: { correct: true, timeSpentSeconds: 12 }, answeredAt: "2026-05-21T09:00:00Z" }],
  [/\/lms\/attempts\/\{attemptId\}\/answers$/, { answers: { question_demo_001: "A", question_demo_002: ["B", "C"] }, packageHash: "sha256-demo", quizVersion: "1", events: [], client: { platform: "web" } }],
  [/\/lms\/attempts\/\{attemptId\}\/sync$/, { packageHash: "sha256-demo", quizVersion: "1", attempt: { status: "in_progress", answers: { question_demo_001: "A" } }, events: [{ type: "sync", at: "2026-05-21T09:00:00Z" }], client: { platform: "web" } }],
  [/\/lms\/attempts\/\{attemptId\}\/submit$/, { answers: { question_demo_001: "A", question_demo_002: "B" }, submittedAt: "2026-05-21T09:30:00Z" }],
  [/\/lms\/assignments$/, { classId: "class_demo_001", quizIds: ["quiz_demo_001"], recipientMode: "selected_students", studentIds: ["student_demo_001"], dueAt: "2026-06-01T23:59:59Z", teacherNote: "Complete before deadline" }],
  [/\/lms\/scopes\/current$/, { scopeType: "center", centerId: "center_demo_001", schoolId: "school_demo_001" }],
  [/\/lms\/access-management\/users\/\{userId\}\/access$/, { centerIds: ["center_demo_001"], schoolIds: ["school_demo_001"], classIds: ["class_demo_001"], permissions: ["lms.teacher"], expiresAt: "2026-12-31T23:59:59Z" }],
  [/\/lms\/classes\/\{id\}$/, { name: "Class 10A1", centerId: "center_demo_001", schoolYear: "2026", status: "active" }],
  [/\/lms\/students\/\{id\}$/, { fullName: "Student Demo", classId: "class_demo_001", status: "active" }],
  [/\/lms\/quizzes\/\{id\}\/publish$/, { published: true, version: 1, note: "Publish from Postman" }],
  [/\/courses$/, { title: "Demo Course", slug: "demo-course", description: "Course created from Postman", status: "draft", categoryId: "category_demo_001" }],
  [/\/courses\/\{id\}$/, { title: "Updated Demo Course", description: "Updated course", status: "published" }],
  [/\/courses\/\{id\}\/theme$/, { primaryColor: "#2563eb", accentColor: "#16a34a", fontFamily: "Inter" }],
  [/\/courses\/\{id\}\/lessons\/reorder$/, { lessonIds: ["lesson_demo_001", "lesson_demo_002"] }],
  [/\/posts$/, { title: "Demo post", slug: "demo-post", excerpt: "Post excerpt", content: "Post content", status: "draft", categoryIds: ["category_demo_001"], tags: ["demo"], coverImage: "https://example.com/cover.jpg" }],
  [/\/posts\/preview$/, { title: "Preview post", content: "Preview content", categoryIds: ["category_demo_001"] }],
  [/\/posts\/\{id\}$/, { title: "Updated post", content: "Updated content", status: "published" }],
  [/\/posts\/\{id\}\/promote$/, { position: "featured", enabled: true }],
  [/\/posts\/images\/upload$/, { fileName: "cover.jpg", contentType: "image/jpeg", base64: "base64-image-content" }],
  [/\/posts\/categories$/, { name: "News", slug: "news", description: "News category", parentId: null, status: "active" }],
  [/\/posts\/categories\/\{id\}$/, { name: "News Updated", slug: "news-updated", description: "Updated category", status: "active" }],
  [/\/hoclieu\/resources$/, { title: "Demo resource", subjectId: "subject_demo_001", gradeId: "grade_demo_001", type: "document", visibility: "center", centerId: "center_demo_001", tags: ["math"] }],
  [/\/hoclieu\/resources\/upload$/, { title: "Uploaded resource", fileName: "lesson.pdf", contentType: "application/pdf", base64: "base64-file-content", subjectId: "subject_demo_001" }],
  [/\/hoclieu\/resources\/\{resourceId\}$/, { title: "Updated resource", visibility: "center", status: "active" }],
  [/\/documents$/, { fileName: "document.pdf", title: "Document demo", contentType: "application/pdf", base64: "base64-file-content", folderId: "folder_demo_001" }],
  [/\/documents\/\{id\}$/, { title: "Updated document", tags: ["demo"], status: "active" }],
  [/\/notifications\/preferences$/, { channels: { email: true, sms: false, push: true }, topics: { lms: true, cms: true, system: true } }],
  [/\/crawler\/(crawl|smart-detect|smart-selector\/analyze|auto-crawl|quick-add|url\/run|sitemap\/parse)$/, { url: "https://example.com", selector: "article", depth: 2, options: { useAi: true, language: "vi" } }],
  [/\/crawler\/configs$/, { name: "Demo crawler config", url: "https://example.com", schedule: "0 */6 * * *", selectors: { title: "h1", content: "article" }, active: true }],
  [/\/crawler\/configs\/test-batch$/, { urls: ["https://example.com/news"], selector: "article", limit: 5 }],
  [/\/crawler\/configs\/\{id\}$/, { name: "Updated crawler config", active: true }],
  [/\/crawler\/rss$/, { feedUrl: "https://example.com/rss.xml", categoryId: "category_demo_001", active: true }],
  [/\/crawler\/rss\/preview$/, { feedUrl: "https://example.com/rss.xml", limit: 5 }],
  [/\/crawler\/rss\/create-selective$/, { feedUrl: "https://example.com/rss.xml", selectedItemIds: ["item_demo_001"] }],
  [/\/crawler\/rss\/trigger$/, { feedId: "feed_demo_001", force: true }],
  [/\/crawler\/rss\/sync\/\{id\}$/, { force: true }],
  [/\/crawler\/rss\/\{id\}$/, { feedUrl: "https://example.com/rss-updated.xml", active: true }],
  [/\/crawler\/blacklist$/, { pattern: "https://example.com/private/*", reason: "Exclude private pages", active: true }],
  [/\/crawler\/blacklist\/\{id\}$/, { pattern: "https://example.com/archive/*", active: true }],
  [/\/rss\/feeds$/, { name: "Demo RSS", url: "https://example.com/rss.xml", categoryId: "category_demo_001", active: true }],
  [/\/rss\/feeds\/\{id\}$/, { name: "Updated RSS", active: true }],
  [/\/ai-content\/(generate|refine)$/, { prompt: "Create a short LMS lesson introduction", language: "vi", tone: "friendly", model: "default" }],
  [/\/ai-content\/keys$/, { provider: "openai", apiKey: "sk-demo", label: "Demo key", default: true }],
  [/\/seo\/.*$/, { title: "SEO title", description: "SEO description", keywords: ["erg", "lms"], canonicalUrl: "https://example.com/demo" }],
  [/\/operations\/configs$/, { key: "daily-report", value: { enabled: true, schedule: "0 8 * * *" }, description: "Daily report config" }],
  [/\/operations\/configs\/\{id\}$/, { value: { enabled: true }, description: "Updated operation config" }],
  [/\/insight\/session\/\{id\}\/finish$/, { finishedAt: "2026-05-21T09:30:00Z", durationSeconds: 1800, metadata: { source: "postman" } }],
  [/\/webhooks\/(telegram|discord)$/, { event: "message.created", payload: { text: "hello", userId: "external_user_demo" } }],
];

function resolveRef(schema) {
  if (!schema?.$ref) return schema;
  const name = schema.$ref.split("/").pop();
  return schemas[name] ?? schema;
}

function sampleForProperty(name, schema = {}, seen = new Set()) {
  const lower = name.toLowerCase();
  if (lower.includes("email")) return "teacher@example.com";
  if (lower.includes("password")) return "Password@123";
  if (lower === "code" || lower.includes("pin")) return "123456";
  if (lower.includes("token")) return lower.includes("refresh") ? "{{refreshToken}}" : "{{accessToken}}";
  if (lower.includes("permission")) return lower.endsWith("s") ? ["lms.assignment.read"] : "lms.assignment.read";
  if (lower.includes("role")) return lower.endsWith("s") || schema.type === "array" ? ["teacher"] : "teacher";
  if (lower.includes("status")) return "active";
  if (lower.includes("date") || lower.endsWith("at")) return schema.format === "date-time" ? "2026-05-21T09:00:00Z" : "2026-05-21";
  if (lower.includes("ids")) return ["demo-id-001"];
  if (lower.endsWith("id")) return SAMPLE_BY_NAME[name] ?? `${name}_demo_001`;
  if (lower.includes("name")) return "Demo name";
  if (lower.includes("title")) return "Demo title";
  if (lower.includes("description")) return "Demo description";
  if (lower.includes("url")) return "https://example.com/demo";
  if (lower.includes("phone")) return "0900000000";
  const type = Array.isArray(schema.type) ? schema.type.find((t) => t !== "null") : schema.type;
  if (type === "integer") return 1;
  if (type === "number") return 1.5;
  if (type === "boolean") return true;
  if (type === "array") return [sampleFromSchema(schema.items ?? { type: "string" }, seen, name.replace(/s$/, ""))];
  if (type === "object" || schema.properties || schema.additionalProperties || schema.$ref) return sampleFromSchema(schema, seen, name);
  return "demo";
}

function sampleFromSchema(inputSchema, seen = new Set(), propName = "") {
  const schema = resolveRef(inputSchema);
  if (!schema || schema === true) return {};
  if (schema.$ref) return {};
  if (schema.enum?.length) return schema.enum[0];
  if (schema.example !== undefined) return schema.example;
  if (schema.default !== undefined) return schema.default;
  if (schema.oneOf?.length) return sampleFromSchema(schema.oneOf[0], seen, propName);
  if (schema.anyOf?.length) return sampleFromSchema(schema.anyOf[0], seen, propName);
  if (schema.allOf?.length) return Object.assign({}, ...schema.allOf.map((s) => sampleFromSchema(s, seen, propName)));

  const type = Array.isArray(schema.type) ? schema.type.find((t) => t !== "null") : schema.type;
  if (type === "string") {
    if (schema.format === "email") return "teacher@example.com";
    if (schema.format === "date-time") return "2026-05-21T09:00:00Z";
    if (schema.format === "date") return "2026-05-21";
    if (schema.format === "uri") return "https://example.com/demo";
    if (propName) return sampleForProperty(propName, { type: "string" }, seen);
    return "string";
  }
  if (type === "integer" || type === "number") return type === "integer" ? 1 : 1.5;
  if (type === "boolean") return true;
  if (type === "array") return [sampleFromSchema(schema.items ?? { type: "string" }, seen, propName.replace(/s$/, ""))];
  if (type === "object" || schema.properties || schema.additionalProperties) {
    if (schema.properties) {
      const name = Object.keys(schemas).find((key) => schemas[key] === schema);
      if (name && seen.has(name)) return {};
      if (name) seen.add(name);
      const out = {};
      for (const [key, value] of Object.entries(schema.properties)) out[key] = sampleForProperty(key, value, seen);
      return out;
    }
    return domainFallback(propName);
  }
  return propName ? sampleForProperty(propName, {}, seen) : "string";
}

function domainFallback(propName) {
  if (propName === "answers") return { question_demo_001: "A" };
  if (propName === "clientResult") return { correct: true, timeSpentSeconds: 12 };
  if (propName === "client") return { platform: "web", userAgent: "Postman" };
  if (propName === "events") return [{ type: "event.demo", at: "2026-05-21T09:00:00Z" }];
  if (propName === "changes") return { permissions: ["lms.assignment.read"] };
  return { example: "value" };
}

function bodyOverride(openApiPath, method) {
  if (!["post", "put", "patch"].includes(method)) return undefined;
  for (const [pattern, sample] of BODY_OVERRIDES) {
    if (pattern.test(openApiPath)) return sample;
  }
  return undefined;
}

function getRequestBody(operation, openApiPath, method) {
  const override = bodyOverride(openApiPath, method);
  if (override) return override;
  const content = operation.requestBody?.content ?? {};
  const json = content["application/json"] ?? content["*/*"] ?? Object.values(content)[0];
  if (!json?.schema) return undefined;
  const sample = sampleFromSchema(json.schema);
  if (sample && typeof sample === "object" && !Array.isArray(sample) && Object.keys(sample).length === 0) {
    return { example: "value" };
  }
  return sample;
}

function collectParams(openApiPath, method, operation) {
  const declared = operation.parameters ?? [];
  const found = [...openApiPath.matchAll(/\{([^}]+)\}/g)].map((m) => m[1]);
  const pathParams = new Map();
  for (const name of found) pathParams.set(name, SAMPLE_BY_NAME[name] ?? `${name}_demo`);
  for (const p of declared.filter((p) => p.in === "path")) pathParams.set(p.name, SAMPLE_BY_NAME[p.name] ?? `${p.name}_demo`);

  const query = [];
  for (const p of declared.filter((p) => p.in === "query")) {
    query.push({
      key: p.name,
      value: QUERY_SAMPLE_BY_NAME[p.name] ?? String(sampleForProperty(p.name, p.schema)),
      description: p.required ? "required" : "optional",
    });
  }

  if (method === "get" && query.length === 0 && /\/(users|posts|courses|documents|resources|quizzes|assignments|history|reports|activities|sessions|centers)(\/?$|\?)/.test(openApiPath)) {
    query.push({ key: "page", value: "1", description: "optional" }, { key: "limit", value: "20", description: "optional" });
  }
  if (openApiPath.endsWith("/reports/export")) {
    query.push({ key: "format", value: "xlsx", description: "optional" });
  }

  return { pathParams, query };
}

function pathToPostman(openApiPath) {
  return openApiPath.replace(/\{([^}]+)\}/g, ":$1");
}

function headers(hasBody) {
  const list = [];
  if (hasBody) list.push({ key: "Content-Type", value: "application/json", type: "text" });
  list.push({ key: "X-Tenant-ID", value: "{{tenantId}}", type: "text" });
  list.push({ key: "X-Request-ID", value: "{{requestId}}", type: "text" });
  return list;
}

function hocLieuExtraItem(name, method, postmanPath, body, query = []) {
  const hasBody = body !== undefined;
  return {
    name,
    request: {
      method,
      header: headers(hasBody),
      url: {
        raw: query.length
          ? `{{baseUrl}}${postmanPath}?${query.map((q) => `${q.key}=${q.value}`).join("&")}`
          : `{{baseUrl}}${postmanPath}`,
        host: ["{{baseUrl}}"],
        path: postmanPath.replace(/^\//, "").split("/"),
        query,
      },
    },
    response: [],
    ...(hasBody ? {
      request: {
        method,
        header: headers(true),
        url: {
          raw: query.length
            ? `{{baseUrl}}${postmanPath}?${query.map((q) => `${q.key}=${q.value}`).join("&")}`
            : `{{baseUrl}}${postmanPath}`,
          host: ["{{baseUrl}}"],
          path: postmanPath.replace(/^\//, "").split("/"),
          query,
        },
        body: {
          mode: "raw",
          raw: JSON.stringify(body, null, 2),
          options: { raw: { language: "json" } },
        },
      },
    } : {}),
  };
}

function hocLieuManualFolders() {
  return [
    {
      name: "HocLieu Bootstrap",
      description: "Visible shortcut folder for the runtime and Studio bootstrap read-model APIs.",
      item: [
        hocLieuExtraItem("GET - Library Bootstrap", "GET", "/api/v1/hoclieu/library/bootstrap", undefined, [
          { key: "schoolId", value: "{{schoolId}}", description: "Managed school id" },
          { key: "academicYear", value: "{{academicYear}}", description: "Academic year" },
        ]),
        hocLieuExtraItem("GET - Studio Bootstrap", "GET", "/api/v1/admin/hoclieu/studio/bootstrap"),
      ],
    },
    {
      name: "HocLieu Runtime",
      description: "Runtime read and launch APIs used outside the Studio authoring flow.",
      item: [
        hocLieuExtraItem("GET - Resources", "GET", "/api/v1/hoclieu/resources", undefined, [
          { key: "subjectId", value: "{{subjectId}}", description: "Optional subject filter" },
          { key: "categoryId", value: "{{groupId}}", description: "Optional group filter" },
          { key: "lessonId", value: "{{lessonId}}", description: "Optional lesson filter" },
        ]),
        hocLieuExtraItem("GET - Resource Detail", "GET", "/api/v1/hoclieu/resources/{{resourceId}}"),
        hocLieuExtraItem("GET - Resource Items", "GET", "/api/v1/hoclieu/resources/{{resourceId}}/items"),
        hocLieuExtraItem("GET - Asset Launch", "GET", "/api/v1/hoclieu/assets/{{assetId}}/launch"),
        hocLieuExtraItem("GET - Asset Pages", "GET", "/api/v1/hoclieu/assets/{{assetId}}/pages"),
        hocLieuExtraItem("GET - Asset Stream", "GET", "/api/v1/hoclieu/assets/{{assetId}}/stream"),
        hocLieuExtraItem("GET - Asset Download", "GET", "/api/v1/hoclieu/assets/{{assetId}}/download"),
      ],
    },
    {
      name: "HocLieu Studio CRUD",
      description: "CRUD endpoints for the Studio screen: subject/group/lesson taxonomy, resources, attachment, and assets.",
      item: [
        hocLieuExtraItem("GET - Taxonomy Nodes", "GET", "/api/v1/admin/hoclieu/taxonomy", undefined, [
          { key: "kind", value: "subject", description: "subject | category | section" },
          { key: "parentId", value: "", description: "Optional parent node id" },
        ]),
        hocLieuExtraItem("GET - Taxonomy Node Detail", "GET", "/api/v1/admin/hoclieu/taxonomy/{{taxonomyNodeId}}"),
        hocLieuExtraItem("POST - Create Subject", "POST", "/api/v1/admin/hoclieu/taxonomy", {
          kind: "subject",
          label: "IC3 GS6",
          slug: "ic3-gs6",
          sortOrder: 1,
          status: "active",
          description: "Cau truc that tu API",
        }),
        hocLieuExtraItem("POST - Create Child Taxonomy", "POST", "/api/v1/admin/hoclieu/taxonomy/{{taxonomyNodeId}}/children", {
          label: "Level 1",
          sortOrder: 1,
          status: "active",
          description: "Nhom hoac bai hoc con cua node dang chon",
        }),
        hocLieuExtraItem("PATCH - Update Taxonomy", "PATCH", "/api/v1/admin/hoclieu/taxonomy/{{taxonomyNodeId}}", {
          label: "IC3 GS6 Updated",
          sortOrder: 2,
          status: "active",
          description: "Cap nhat taxonomy node",
        }),
        hocLieuExtraItem("DELETE - Delete Taxonomy", "DELETE", "/api/v1/admin/hoclieu/taxonomy/{{taxonomyNodeId}}"),
        hocLieuExtraItem("POST - Create Resource", "POST", "/api/v1/admin/hoclieu/resources", {
          title: "IC3 GS6 Level 1 - Excel nhap mon",
          subtitle: "Slide bai giang",
          description: "Tai lieu bai giang cho Level 1",
          thumbnailUrl: "https://example.com/thumb.png",
          subjectId: "{{subjectId}}",
          categoryId: "{{groupId}}",
          lessonId: "{{lessonId}}",
          type: "lecture",
          documentTypeId: "lecture",
          status: "published",
          visibility: "open",
          programSlug: "ic3-gs6",
        }),
        hocLieuExtraItem("PATCH - Update Resource", "PATCH", "/api/v1/admin/hoclieu/resources/{{resourceId}}", {
          title: "IC3 GS6 Level 1 - Excel nhap mon Updated",
          thumbnailUrl: "https://example.com/thumb-updated.png",
          status: "published",
          visibility: "open",
        }),
        hocLieuExtraItem("DELETE - Delete Resource", "DELETE", "/api/v1/admin/hoclieu/resources/{{resourceId}}"),
        hocLieuExtraItem("POST - Attach Resource To Node", "POST", "/api/v1/admin/hoclieu/taxonomy/{{taxonomyNodeId}}/resources", {
          resourceId: "{{resourceId}}",
          title: "Slide gan vao node dang chon",
          type: "lecture",
          fileType: "PPTX",
          thumbnailUrl: "https://example.com/thumb.png",
        }),
        hocLieuExtraItem("DELETE - Detach Resource From Node", "DELETE", "/api/v1/admin/hoclieu/taxonomy/{{taxonomyNodeId}}/resources/{{resourceId}}"),
        hocLieuExtraItem("POST - Create Asset", "POST", "/api/v1/admin/hoclieu/resources/{{resourceId}}/assets", {
          title: "Slide bai 01",
          selectedFileType: "PPTX",
          launchMode: "inline_viewer",
          originalFileName: "lesson-01.pptx",
          detectedMimeType: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          fileExtension: "pptx",
          fileSizeBytes: 1024000,
          storageProvider: "r2",
          storageUrl: "https://cdn.erg.edu.vn/lesson-01.pptx",
          canDownload: true,
          status: "ready",
        }),
        hocLieuExtraItem("PATCH - Update Asset", "PATCH", "/api/v1/admin/hoclieu/assets/{{assetId}}", {
          title: "Slide bai 01 Updated",
          selectedFileType: "PPTX",
          launchMode: "inline_viewer",
          canDownload: true,
          status: "ready",
        }),
        hocLieuExtraItem("DELETE - Delete Asset", "DELETE", "/api/v1/admin/hoclieu/assets/{{assetId}}"),
      ],
    },
  ];
}

function generatedHocLieuPathIsOverridden(openApiPath) {
  return openApiPath.startsWith("/api/v1/admin/hoclieu")
    || openApiPath.startsWith("/api/v1/hoclieu/resources")
    || openApiPath.startsWith("/api/v1/hoclieu/assets")
    || openApiPath.startsWith("/api/v1/hoclieu/taxonomy");
}

function requestName(method, operation, postmanPath) {
  const clean = postmanPath.replace(/^\/api\/v1\//, "").replace(/^\/api\//, "").replace(/^\/+/, "");
  return `${method.toUpperCase()} - ${clean || operation.operationId || "root"}`;
}

function makeItem(openApiPath, method, operation) {
  const postmanPath = pathToPostman(openApiPath);
  const { pathParams, query } = collectParams(openApiPath, method, operation);
  const body = getRequestBody(operation, openApiPath, method);
  const hasBody = body !== undefined;
  const rawUrl = `{{baseUrl}}${postmanPath}`;
  const item = {
    name: requestName(method, operation, postmanPath),
    request: {
      method: method.toUpperCase(),
      header: headers(hasBody),
      url: {
        raw: query.length ? `${rawUrl}?${query.map((q) => `${q.key}=${encodeURIComponent(q.value)}`).join("&")}` : rawUrl,
        host: ["{{baseUrl}}"],
        path: postmanPath.replace(/^\//, "").split("/"),
        query,
        variable: [...pathParams.entries()].map(([key, value]) => ({ key, value, description: "Sample path variable" })),
      },
      description: [
        operation.summary || "",
        operation.description || "",
        pathParams.size ? `Path params: ${[...pathParams.entries()].map(([k, v]) => `${k}=${v}`).join(", ")}` : "",
        query.length ? `Query params mẫu đã được điền sẵn trong tab Params.` : "",
      ].filter(Boolean).join("\n"),
    },
    response: [],
  };
  if (hasBody) {
    item.request.body = {
      mode: "raw",
      raw: JSON.stringify(body, null, 2),
      options: { raw: { language: "json" } },
    };
  }
  if (postmanPath === "/api/v1/auth/login") {
    item.event = [{
      listen: "test",
      script: {
        type: "text/javascript",
        exec: [
          "const json = pm.response.json();",
          "const data = json.data || {};",
          "if (data.accessToken) pm.environment.set('accessToken', data.accessToken);",
          "if (data.refreshToken) pm.environment.set('refreshToken', data.refreshToken);",
        ],
      },
    }];
  }
  return item;
}

function folderName(tag) {
  if (tag === "auth-controller") return "00 - Auth";
  return tag || "misc";
}

function buildCollection(name, filter) {
  const folders = new Map();
  for (const [openApiPath, pathItem] of Object.entries(openapi.paths)) {
    if (generatedHocLieuPathIsOverridden(openApiPath)) continue;
    for (const method of ["get", "post", "put", "patch", "delete"]) {
      const operation = pathItem[method];
      if (!operation || !filter(openApiPath, method, operation)) continue;
      const tag = operation.tags?.[0] ?? "misc";
      const folder = folderName(tag);
      if (!folders.has(folder)) folders.set(folder, []);
      folders.get(folder).push(makeItem(openApiPath, method, operation));
    }
  }

  const generatedItems = [...folders.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, item]) => ({ name, item }));
  const item = name.includes("Full") ? [...hocLieuManualFolders(), ...generatedItems] : generatedItems;

  return {
    info: {
      name,
      description: "Versioned ERG Spring Boot API surface. Requests include sample path variables, query params, headers, and JSON bodies generated from OpenAPI plus ERG domain samples.",
      schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    auth: { type: "bearer", bearer: [{ key: "token", value: "{{accessToken}}", type: "string" }] },
    variable: COLLECTION_VARIABLES,
    item,
  };
}

function buildEnvironment() {
  return {
    name: "ERG Spring Local",
    values: COLLECTION_VARIABLES.map((variable) => ({ ...variable, enabled: true })),
    _postman_variable_scope: "environment",
    _postman_exported_at: new Date().toISOString(),
    _postman_exported_using: "Codex",
  };
}

fs.mkdirSync(outDir, { recursive: true });
const full = buildCollection("ERG Spring API - Full", () => true);
const auth = buildCollection("ERG Spring API - Auth", (_path, _method, operation) => operation.tags?.[0] === "auth-controller");
fs.writeFileSync(fullOut, `${JSON.stringify(full, null, 2)}\n`);
fs.writeFileSync(authOut, `${JSON.stringify(auth, null, 2)}\n`);
fs.writeFileSync(envOut, `${JSON.stringify(buildEnvironment(), null, 2)}\n`);

const allRequests = full.item.reduce((sum, folder) => sum + folder.item.length, 0);
const authRequests = auth.item.reduce((sum, folder) => sum + folder.item.length, 0);
console.log(JSON.stringify({
  fullOut: path.relative(root, fullOut),
  authOut: path.relative(root, authOut),
  envOut: path.relative(root, envOut),
  allRequests,
  authRequests,
}, null, 2));
