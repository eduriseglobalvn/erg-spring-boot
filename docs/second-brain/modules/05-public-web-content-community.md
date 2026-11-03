# 05 - Public web, CMS, nội dung phụ trợ và cộng đồng

Đường dẫn tính từ gốc repo `erg-spring`; code Java nằm dưới `src/main/java/com/erg/ergspringboot/` (viết tắt `…/`).
Các dòng "file:line" lấy từ code đã đọc; mục nào chưa kiểm chứng được sẽ ghi "nghi ngờ".

## 1. Phạm vi và trách nhiệm

Slice này gồm bề mặt web công khai và các tính năng phụ trợ: CMS (bài viết, trang, menu), catalog khóa học, SEO, crawler/RSS, trending, bot (Discord/Telegram), tuyển dụng, đánh giá (review), công bố thông tin, tài liệu (documents), storage policy và cộng đồng.

**Phát hiện then chốt:** gần như toàn bộ slice là "prototype" – controller/service gắn `@Profile("prototype")`, lưu dữ liệu **trong bộ nhớ tiến trình** (`ConcurrentHashMap`), không có repository/DB. Các `model/*` (community, bot, crawler, seo, trending, reviews, documents) chỉ là lớp dữ liệu "migrated from erg-go", **chưa có repository/service nào dùng** (xem mục 9).

Phần thật sự có logic và lưu DB trong slice chỉ có:
- `StudentEngagementService` (thảo luận lớp học, reaction, đánh dấu đã đọc thông báo – qua GraphQL).
- Webhook Discord/Telegram (`WebhookController` + `WebhookInboxService`, ghi bảng `webhook_inbox`).
- `StoragePolicyService` (kiểm tra kích thước/loại file) và `FileSystemLearningStandardsArtifactStorage`.

## 2. Class/entity chính và vai trò

| Class | Đường dẫn | Vai trò |
|---|---|---|
| `ModuleWorkflowService` | `…/service/support/ModuleWorkflowService.java` | Kho in-memory chung cho crawler/rss/seo/trending/bot/reviews/recruitment/public-disclosure/community (key `module:type`) |
| `ProcessLocalStatePolicy` | `…/platform/persistence/ProcessLocalStatePolicy.java` | Công tắc `erg.process-local.enabled`; tắt thì mọi store in-memory ném `NotImplementedException` |
| `CmsService` | `…/service/cms/CmsService.java` | Store in-memory cho posts/categories/pages |
| `CatalogService` | `…/service/catalog/CatalogService.java` | Store in-memory cho courses/categories/levels/units |
| `DocumentService` | `…/service/documents/DocumentService.java` | Store in-memory metadata tài liệu, dùng `StoragePolicyService` |
| `StoragePolicyService` | `…/service/storage/StoragePolicyService.java` | Giới hạn 25 MB và whitelist MIME |
| `FileSystemLearningStandardsArtifactStorage` | `…/learningstandards/internal/storage/FileSystemLearningStandardsArtifactStorage.java` | Adapter đĩa cục bộ cho artifact chuẩn học tập (head + SHA-256) |
| `StudentEngagementService` | `…/service/elearning/engagement/StudentEngagementService.java` | Thảo luận lớp, comment, reaction, đọc thông báo (JDBC thuần, `@Deprecated since 2026-08-08`, được `ElearningOperations` gọi) |
| `WebhookController` | `…/controller/bot/WebhookController.java` | Nhận webhook Discord/Telegram |
| `WebhookInboxService` / `WebhookReplayGuard` | `…/platform/webhook/` (ngoài slice, đọc để hiểu luồng) | Chống replay + ghi inbox |
| `CourseController`, `ElearningController` | `…/controller/catalog/` | REST catalog (prototype) |
| `PostController`, `PageMenuController` | `…/controller/cms/` | REST CMS (prototype) |
| `CrawlerController`, `RssBlacklistController` | `…/controller/crawler/` | REST crawler/RSS/blacklist (prototype, chỉ tạo "job" giả) |
| `SeoController` | `…/controller/seo/SeoController.java` | robots.txt, sitemap, API SEO (prototype) |
| `TrendingBotController` | `…/controller/trending/TrendingBotController.java` | Trending + bot conversation/link (prototype) |
| `EngagementController` | `…/controller/engagement/EngagementController.java` | Recruitment, reviews, public-disclosure (prototype) |
| `DocumentController` + `DocumentRequests` | `…/controller/documents/`, `…/common/request/documents/` | REST tài liệu (prototype) + validation request |
| `AuthorizationPermissionCatalogUseCase` | `…/authorization/internal/catalog/` | Thuộc authorization (trùng từ khóa "catalog", KHÔNG phải catalog khóa học); đọc danh mục quyền, cache `authorizationPermissions` |
| Model chưa dùng | `…/model/{community,bot,crawler,seo,trending,reviews,documents}/*.java` | Thực thể/POJO chuyển từ erg-go (xem mục 6) |

## 3. Nghiệp vụ và luồng xử lý

### 3.1 Thảo luận lớp học của học viên (nghiệp vụ thật, có DB)

Tất cả trong `…/service/elearning/engagement/StudentEngagementService.java`; vào qua GraphQL `StudentEngagementGraphQlController` (`…/controller/elearning/graphql/StudentEngagementGraphQlController.java:36-85`).

**Xác định học viên** (`requireStudent`, :189-193): tìm `Student` theo `tenantId + authUserId`, bắt buộc có `academicClassId` khác rỗng, nếu không ném `ResourceNotFoundException` ("Authenticated student enrollment was not found"). Mọi thao tác bị giới hạn trong **lớp học của học viên**.

**Feed** (`feed`, :49-73): đếm và lấy `student_discussion_threads` của đúng `tenant_id` + `academic_class_id`; sắp xếp `latest_activity_at desc, created_at desc`; `size` bị kẹp 1..50, `page >= 0`; `hasNext = (page+1)*size < total`. Tải comment theo `post_id in (...)`, gom reaction theo (POST|COMMENT) và reaction của chính viewer (`assemblePosts` :209-237). Tên tác giả mặc định "Học viên", initials mặc định "HV" (:380-384).

**Tạo thread** (`createDiscussion`, :75-100):
1. `requireStudent`.
2. Bắt buộc `idempotencyKey` (<=192), `title` (<=250), `content` (<=4000) (`requireText` :362-365).
3. Nếu đã có thread cùng (tenant, author, idempotencyKey) -> trả lại thread cũ (idempotent, :81-82).
4. Insert; nếu `DataIntegrityViolationException` (race) thì tra lại key và trả bản đã có (:94-98).
5. `title/content/idempotencyKey` được `trim()`; `assignmentId` rỗng -> null.

**Trả lời (comment)** (`reply`, :102-131):
1. Kiểm tra text (`idempotencyKey`, `postId` <=36, `content` <=4000), post phải thuộc lớp học viên (`requirePostInClass`).
2. Idempotent theo (tenant, author, key).
3. Nếu có `parentCommentId` thì comment cha phải thuộc cùng post (`requireCommentInPost`, :330-334).
4. Insert; sau đó cập nhật `latest_activity_at`/`updated_at` của thread (:128-129) -> thread nổi lên đầu feed.
5. Cây comment: chỉ hỗ trợ lồng 2 cấp ở phần hiển thị (`toComment` gọi con với `Map.of()` nên cháu bị bỏ, :244-262) – xem mục 9.

**Reaction** (`setReaction`, :133-163): `targetType` ∈ {POST, COMMENT}, `reaction` ∈ {like, love, care, haha, wow, sad, angry} (:36-37); mục tiêu phải thuộc lớp học viên. Hành vi **toggle**: gửi lại cùng reaction -> xóa; reaction khác -> upsert `on conflict (tenant_id, target_type, target_id, user_id)`. Trả về số đếm theo loại.

**Đánh dấu đã đọc thông báo** (`markAnnouncementRead`, :165-187): thông báo `lms_announcements` phải `status = 'PUBLISHED'` và (`target_type = 'ALL'` hoặc `academic_class_ids` chứa lớp, hoặc `student_ids` chứa học viên) – ngược lại `ResourceNotFoundException`. Upsert vào `student_announcement_reads` theo (tenant, announcement, user), cập nhật `read_at`.

### 3.2 Webhook bot Discord/Telegram (nghiệp vụ thật)

`WebhookController` (`…/controller/bot/WebhookController.java`):
- `POST /webhooks/discord` (:38-50): giới hạn body (rỗng hoặc > `maxBodyBytes`, mặc định 64 KB -> `BadRequestException`, :64-68); xác thực chữ ký Ed25519 trên `timestamp + rawBody`, timestamp lệch quá ±5 phút (mặc định) bị loại trước khi tính chữ ký (`DiscordEd25519SignatureVerifier.java:82-86`); `receiptKey` = `payload.id` hoặc chữ ký nếu id trống; `inbox.accept("discord", …)`.
- `POST /webhooks/telegram` (:52-62): so sánh secret token bằng `MessageDigest.isEqual` (thời gian hằng số), trống thì fail-closed (`TelegramSecretTokenVerifier.java:120-126`); `receiptKey` = `updateId`.
- `WebhookInboxService.accept` (`…/platform/webhook/WebhookInboxService.java:182-190`): trong một transaction `postgresTransactionManager`: `replayGuard.claim` (insert `webhook_receipts`; nếu bản ghi chưa hết hạn -> `WebhookReplayException`), rồi insert `webhook_inbox(status='pending')`, tăng metric `webhook.accepted{provider}`. TTL replay mặc định 24h.
- Trả `202 Accepted` "Webhook accepted".
- **Không thấy worker nào đọc `webhook_inbox`** trong `src/main/java` (grep chỉ thấy lệnh insert) -> payload chỉ được xếp hàng; xử lý lệnh bot chưa có (mục 9).
- Nguồn cấu hình: `erg.webhook.*` (`application.yml:211-216`), khóa/secret lấy từ biến môi trường `SECRET_*` (không ghi giá trị).

### 3.3 Các bề mặt prototype (in-memory)

Điểm chung: `statePolicy.assertEnabled(...)` ở đầu mọi hàm; mặc định `erg.process-local.enabled=false` (`application.yml:265, 549`) trừ profile local (`application-local.yml:22-23` bật `true`). Khi tắt -> `NotImplementedException("PROCESS_LOCAL_STATE_DISABLED")` (`ProcessLocalStatePolicy.java:147-152`). Controller đã gắn `@Profile("prototype")`, nên trong production các route này không được đăng ký (kiểm tra bởi `ProductionPrototypeExposureTest`).

- **CMS** (`CmsService`): `save` tạo bản ghi `status="draft"` (:49); `mark(...,"deleted")` là xóa mềm, `restore` đặt lại `draft` (`PostController.java:69-77`); `delete` (đường `/permanent`) xóa hẳn. Không có state machine publish thật; trạng thái chỉ là chuỗi do client gửi. `get`/`bySlug` khi không tìm thấy **trả object giả** `{id,type}`/`{slug,type}` thay vì 404 (:33, :40). `menuStructure` luôn trả `items=[]` (:79-82). `imageUpload` chỉ sinh id/url giả `/uploads/posts/<id>` (:84-87).
- **Catalog** (`CatalogService`): courses/categories/levels/units; `findBySlug` khớp `slug` hoặc `subdomain` (:36-42); `schema` trả khung rỗng; `reorder` bài học chỉ echo lại dữ liệu (`CourseController.java:63-66`).
- **Documents** (`DocumentService`): `upload` gọi `storagePolicyService.validate(filename, mimeType, size)`; `storageKey = documents/<id>/<filename>`; `status="active"`; `delete` trả `remoteObjectDeleted:true` dù không xóa gì ở storage (:79-83); `file()` chỉ trả `downloadUrl` tự trỏ về chính nó (:59-68).
- **Crawler / RSS / blacklist**: `ModuleWorkflowService.job` tạo "job" `status="queued"` kèm `retryPolicy{maxAttempts:3, backoff:exponential}` và `dlq="<module>:dlq"` (:94-103) nhưng **không có worker/crawler thật**. `GET .../stream` trả một khung SSE tĩnh `status:"ready"` (`CrawlerController.java:40-43`). `status()` luôn trả `available/healthy` (`ModuleWorkflowService.java:83-92`).
- **SEO**: `robots.txt` cố định (`SeoController.java:28-31`); `sitemap*.xml` trả một `<url>` duy nhất `https://erg.local/` (hoặc `/<sub>` nếu `sub` khớp `[a-z0-9-]{1,64}`, :33-39) – chưa sinh từ dữ liệu thật. Các endpoint GSC/keyword/redirect/404 chỉ lưu/echo qua `ModuleWorkflowService`.
- **Trending/Bot** (`TrendingBotController`): `refresh/discover` tạo job queued; `/bot/link/{code}` luôn trả `valid:true` (:70-73); `/bot/conversations/{id}/wizard` trả 3 bước cố định `input, confirm, run` (:60-63).
- **Engagement** (`EngagementController`): recruitment (jobs/apply/candidates), reviews (list/stats/admin/status/approve/reject/feature/helpful/reply), public-disclosure; module/type được suy ra từ **chuỗi đường dẫn** (`module()`/`type()` :130-147). Trạng thái review/ứng viên chỉ là `update` map theo body.

### 3.4 Storage

- `StoragePolicyService.validate` (:20-28): size > 25 MB -> `BadRequestException("file is too large")`; MIME không rỗng mà không thuộc {pdf, png, jpeg, webp, docx} -> "file type is not allowed". MIME rỗng/null **được chấp nhận** (xem mục 9).
- `FileSystemLearningStandardsArtifactStorage.head` (:26-42): chống path traversal (`normalize` + `startsWith(root)` và kiểm tra lại `toRealPath` để chặn symlink), chỉ file thường, kích thước 0..512 MB, trả (size, SHA-256 hex). Root mặc định `.erg-learning-standards` (`erg.learning-standards.storage-root`). Ghi chú code: production có thể thay bằng R2/S3 (:14).

## 4. Quy tắc nghiệp vụ, validation, phân quyền

**Bảng quyền ở `…/platform/security/SecurityConfig.java`:**

| Nhóm | Rule | Dòng |
|---|---|---|
| Public (permitAll) | `/robots.txt`, `/sitemap*.xml` | 104 |
| Public | `POST /webhooks/discord`, `/webhooks/telegram` (xác thực bằng chữ ký/secret trong controller) | 106 |
| Editorial CMS | `GET /api/v1/posts/hidden|trash|preview/**` cần `cms.posts.update`/`cms.posts.delete`/`*` (đặt trước rule public) | 111-116 |
| Reviews admin | `GET /api/v1/reviews/admin(/all)` chỉ cần `authenticated()` (không cần quyền cụ thể) | 117-119 |
| Public GET | pages, menus/structure, posts/**, courses/**, elearning/categories|levels, recruitment/jobs/**, reviews, reviews/stats, public-disclosure/**, trending/** | 120-127 |
| Public POST | `recruitment/apply`, `reviews`, `reviews/*/helpful` | 128-130 |
| Crawler/RSS | `/api/v1/crawler/**`, `/api/crawler/**`, `/api/v1/rss/**` cần `crawler.manage` | 191-192 |
| E-learning admin | `/api/v1/admin/elearning/**`, `/api/v1/elearning/**` cần `elearning.manage` | 218-219 |
| SEO | `/api/v1/seo/**` cần `seo.manage` | 220-221 |
| Posts | POST create `cms.posts.create`; PUT `cms.posts.update`; DELETE `cms.posts.delete`; categories `cms.categories.create/update/delete`; ảnh `media.upload`/`media.delete` | 222-237 |
| Courses | POST `courses.create`; PATCH/POST con `courses.update`; DELETE `courses.delete` | 238-245 |
| Documents | `/api/v1/documents/**` cần `documents.manage` | 246-247 |
| Public disclosure | POST/DELETE cần `public-disclosure.manage` | 248-251 |

Lưu ý: nếu route khớp rule public GET ở :120-127 thì `GET /api/v1/posts/**` công khai cả `GET /api/v1/posts/{id}`; `@CmsEditorialRead` trên `PostController.editorialList` (`PostController.java:36-40`) là lớp bảo vệ method-level thứ hai (`authorization/authorization/CmsEditorialRead.java`: cần `cms.posts.update|delete|*`).

**Validation:**
- Documents: `UploadDocumentRequest.name` `@NotBlank`, `<=500`; `content` `@NotBlank`; `UpdateDocumentRequest.name <=500` (`DocumentRequests.java:12-40`). `DocumentController.upload` ném `BadRequestException` nếu body null (:35-37).
- Thảo luận học viên: xem 3.1 (độ dài tối đa, idempotency, phạm vi lớp, whitelist reaction).
- Quyền GraphQL: `elearning.dashboard.read` cho feed, `elearning.discussion.write_self` cho tạo/trả lời/reaction, `elearning.announcement.read_self` cho đọc thông báo (đăng ký ở migration V83); `tenantId` được xác thực qua `accessSupport.verifiedTenantId(principal, input.tenantId())` (controller :40-41, :52 …) – tenant đến từ principal, không tin input.
- Tenant: `DocumentController` lấy tenant/user từ `SecurityUtils.principal()` (:73-80), nhưng lùi về `"system"` nếu principal null; các store in-memory khác **không có tenant** (xem mục 9).

## 5. API

### REST (đều `@Profile("prototype")`, trừ webhook)

| Method | Path | Controller | Mô tả |
|---|---|---|---|
| POST | `/webhooks/discord`, `/webhooks/telegram` | `WebhookController` | Nhận webhook, xếp hàng inbox (202). Có ở mọi profile |
| GET | `/api/v1/pages/{slug}`, `/api/v1/menus/structure` | `PageMenuController` | Trang theo slug / menu (rỗng) |
| GET | `/api/v1/posts`, `/{id}`, `/slug/{slug}`, `/categories`, `/categories/{id}` | `PostController` | Đọc công khai |
| GET | `/api/v1/posts/hidden`, `/trash`, `/preview/{id}` | `PostController` | Danh sách/preview editorial |
| POST/PUT/DELETE | `/api/v1/posts`, `/preview`, `/{id}`, `/{id}/restore`, `/{id}/permanent`, `/{id}/promote`, `/images/upload`, `/images`, `/images/id/{filename}`, `/categories[/{id}]` | `PostController` | Ghi bài viết/danh mục/ảnh |
| GET/POST/PATCH/DELETE | `/api/v1/courses`, `/subdomain/{s}`, `/{id}`, `/{id}/schema`, `/{id}/theme`, `/{id}/lessons/reorder` | `CourseController` | Catalog khóa học |
| GET/POST/PATCH/DELETE | `/api/v1/elearning/categories|levels|units…` và `/api/v1/admin/elearning/…` | `ElearningController` | Taxonomy e-learning |
| GET/POST/PATCH/DELETE | `/api/v1/crawler/**`, `/api/crawler/**` (crawl, smart-detect, configs, rss, blacklist, history, stats, stream…) | `CrawlerController` | Tạo job/cấu hình giả |
| GET/POST/PUT/DELETE | `/api/v1/rss/feeds…`, `/api/v1/blacklist` | `RssBlacklistController` | Quản lý feed + blacklist |
| GET/POST | `/robots.txt`, `/sitemap.xml`, `/sitemap_index.xml`, `/sitemap-{sub}.xml`, `/sitemap/data` | `SeoController` | SEO công khai |
| mixed | `/api/v1/seo/**` (keywords, redirects, 404, gsc, schema, analyze, suggest-meta…) | `SeoController` | SEO admin |
| GET/POST | `/api/v1/trending/**`, `/trending/topics/{topic}`, `/refresh`, `/discover` | `TrendingBotController` | Trending |
| GET/POST/DELETE | `/api/v1/bot/conversations[/{id}[/send|/wizard]]`, `/bot/link[/{code}]`, `/bot/accounts[/{id}]` | `TrendingBotController` | Bot |
| mixed | `/api/v1/recruitment/**`, `/api/v1/reviews/**`, `/api/v1/public-disclosure/**` | `EngagementController` | Tuyển dụng, đánh giá, công bố |
| POST/GET/PATCH/DELETE | `/api/v1/documents`, `/{id}`, `/{id}/file` | `DocumentController` | Tài liệu |

### GraphQL (có thật, `src/main/resources/graphql/modules/`)

| Operation | File schema | Resolver | Mô tả |
|---|---|---|---|
| `ElearningQuery.discussionFeed(input)` | `20-elearning.graphqls:5` | `StudentEngagementGraphQlController:36` | Feed thảo luận lớp (phân trang) |
| `Mutation.createStudentDiscussion` | `40-mutation-social.graphqls:4` | `…:49` | Tạo thread (idempotent) |
| `Mutation.replyStudentDiscussion` | `40-mutation-social.graphqls:5` | `…` | Trả lời/comment lồng |
| `Mutation.setStudentDiscussionReaction` | `40-mutation-social.graphqls:6` | `…:69` | Toggle reaction |
| `Mutation.markStudentAnnouncementRead` | `40-mutation-social.graphqls:7` | `…:79` | Ghi nhận đã đọc thông báo |

Không có GraphQL operation nào cho CMS/SEO/crawler/trending/bot/reviews/documents.

## 6. Dữ liệu

**Có migration (thật):** `V83__student_engagement_contract.sql`
- `lms_announcements` (index `(tenant_id,status,pinned,published_at desc)`, GIN trên `academic_class_ids`, `student_ids`).
- `student_discussion_threads` (index feed `(tenant_id, academic_class_id, latest_activity_at desc)`; unique `(tenant_id, author_id, idempotency_key)`).
- `student_discussion_comments` (FK `post_id` -> threads CASCADE; `parent_id` tự tham chiếu CASCADE; unique idempotency tương tự; index `(tenant_id, post_id, created_at)`).
- `student_discussion_reactions` (CHECK `target_type IN (POST,COMMENT)`, CHECK reaction trong 7 giá trị; unique `(tenant_id,target_type,target_id,user_id)`). Lưu ý: `target_id` không có FK.
- `student_announcement_reads` (FK `announcement_id` CASCADE; unique `(tenant_id, announcement_id, user_id)`).
- Bật **Row Level Security** cho cả 5 bảng với policy theo `app.tenant_id` (hoặc `app.maintenance_mode='true'`).
- Chèn 3 permission `elearning.dashboard.read`, `elearning.discussion.write_self`, `elearning.announcement.read_self` cùng `permission_bindings`.

`V72__webhook_ingress.sql`: `webhook_receipts` (PK `(provider, receipt_key)`, index hạn `expires_at`) và `webhook_inbox` (identity id, `status` CHECK pending|processing|completed|failed, unique `(provider, receipt_key)`, index pickup `(status, received_at)`, `payload jsonb`, `last_error`). Lưu ý V72 không bật RLS/tenant (webhook không gắn tenant).

**Model Java chưa có bảng/migration/repository tương ứng** (tìm `community_*` trong `src/main/resources/db` không có kết quả):
- `model/community`: `CommunityTopic`(`community_topics`), `CommunityPost`(`community_posts`, trạng thái `status`/`visibility`, `isPinned`, `isLocked`, các bộ đếm view/comment/reaction/share, `deletedAt`), `CommunityComment`(`community_comments`, `parentId/rootId/depth`), `CommunityReaction`, `CommunityFollow`, `CommunityMedia` – là `@Entity` JPA, kế thừa `BaseEntity` (trừ Follow/Media).
- `model/bot`: `BotConversation` (state, wizardStep, wizardData, permissionLevel, expiresAt), `BotLinkedAccount` (linkCode, verifiedAt, unlinkedAt), `WorkflowExecution/Step/StepLog`, `CommandRegistry/Entry`, `PlatformUpdate` – POJO (@Data), không phải entity.
- `model/crawler`: `RSSFeed`, `CrawlHistory`, `ContentBlacklist`, `ContentFingerprint` (simHash/SHA-256), `DomainReputation`, payload/progress DTO.
- `model/seo`: `SeoKeyword`, `SeoRedirect`, `Seo404Log`, `GSCData`, `SeoConfig`.
- `model/trending`: `TrendingTopic`, `NewsArticle`, `TrendingSnapshot`.
- `model/reviews/Review` (rating, status, adminReply, isFeatured, helpfulCount, ipAddress, userAgent), `model/documents/Document` (+`WatermarkConfig`, có `r2Url`, `driveId`).
Tất cả có chú thích "Migrated from erg-go …" -> là bản dịch từ hệ thống Go cũ.

## 7. Tích hợp ngoài và sự kiện

- **Discord / Telegram:** chỉ đường vào (webhook) đã được hiện thực; không có client gọi ra (gửi tin nhắn) trong slice. Secret/khóa công khai cấu hình bằng biến môi trường (`application.yml:215-216`).
- **Crawler / RSS / GSC / trending:** chỉ có model, endpoint prototype và job giả; không có HTTP fetch, parser RSS, scheduler hay tích hợp Google Search Console trong code đã đọc. Dữ liệu nguồn dự kiến (theo tên model/endpoint): URL feed RSS, sitemap, cấu hình crawl, GSC, nguồn tin trending – chưa chạy được.
- **Storage:** local FS cho learning-standards (R2/S3 chỉ là ghi chú); `Document` có trường `r2Url`/`driveId` nhưng `DocumentService` chỉ đặt `storageType` mặc định `"local"` (:40).
- **Cache:** chỉ `AuthorizationPermissionCatalogUseCase` dùng `@Cacheable("authorizationPermissions")` (khóa `'all'`, `'groups'`, không theo tenant – xem mục 9). SEO sitemap trả `cacheTtlSeconds:300` chỉ là số liệu giả (`SeoController.java:43`).
- **Metrics:** `webhook.accepted{provider}`, `webhook.rejected.<reason>{provider}` (hàm `rejected` có nhưng chưa thấy nơi gọi trong `WebhookController`).
- **Sự kiện/outbox:** không thấy trong slice này.

## 8. Test hiện có

- `src/test/java/com/erg/ergspringboot/service/elearning/engagement/StudentEngagementServiceTest.java` (Mockito, 3 test): thiếu `idempotencyKey` -> `BadRequestException` và không ghi DB; reaction vào thread ngoài lớp -> `ResourceNotFoundException`; đọc thông báo không nhắm tới học viên -> `ResourceNotFoundException` và không ghi.
- `…/platform/webhook/WebhookSecurityTest.java` (4 test): Discord chấp nhận chữ ký + timestamp mới; từ chối timestamp cũ; Telegram fail-closed; replay guard từ chối receipt đã có.
- `…/platform/security/WebhookSecurityBoundaryTest.java`: đảm bảo `SecurityConfig` không chứa `"/webhooks/**"` (chỉ kiểm tra bằng chuỗi nguồn).
- `…/architecture/ProductionPrototypeExposureTest.java`: controller/service prototype phải có `@Profile("prototype")`.
- `…/platform/persistence/ProcessLocalStatePolicyTest.java`: production từ chối store in-memory; `DocumentService` không dựng được nếu thiếu policy.
- Tồn tại thêm `K6RouteCatalogContractTest`, `ApiCoverageCatalogIntegrationTest` (có nhắc route; chưa đọc kỹ ngoài slice).

**Khoảng trống:** không có test cho `StoragePolicyService`, `DocumentService`, `CmsService`, `CatalogService`, `ModuleWorkflowService`, `SeoController`, `WebhookController` (end-to-end, body limit, 202), `WebhookInboxService`, `FileSystemLearningStandardsArtifactStorage` (path traversal/symlink), `EngagementController`; không có test tích hợp DB/RLS cho thảo luận (happy path feed/reply/reaction, trường hợp reply lồng, hasNext, race idempotency).

## 9. Rủi ro, nợ kỹ thuật, nghi ngờ bug

1. **Phần lớn slice là giả lập/prototype:** CMS, catalog, crawler, SEO, trending, bot, reviews, recruitment, documents lưu in-memory, mất khi restart, không tenant, không phân trang (`ModuleWorkflowService.java:18`, `CmsService.java:16-18`). Production tắt bằng `erg.process-local.enabled=false` và `@Profile("prototype")`; cần xác minh chắc chắn profile "prototype" không bật ở production (nghi ngờ – chưa kiểm tra cấu hình triển khai). Nghĩa là **website công khai/CMS hiện chưa có backend thật trong repo này**.
2. **Không có trường tenant/quyền sở hữu trong các store prototype:** `get`/`update` ghi đè theo `id` do client chỉ định; `update` trên id không tồn tại **tạo mới** bản ghi (ví dụ `CmsService.update` :56-63, `ModuleWorkflowService.update` :46-55) và `putAll(body)` cho phép client ghi đè cả `id`, `status`, `tenantId` (mass assignment) – `DocumentService.update` :70-76 cho phép đổi `storageKey`, `uploadedBy`, `tenantId` qua `metadata`.
3. **`DocumentRequests.UploadDocumentRequest.toMap()` đặt khóa `name`/`content`, nhưng `DocumentService.upload` đọc `filename`/`mimeType`/`size`** (`DocumentService.java:28-30`) -> `filename` luôn rơi về `"document.pdf"`, `mimeType` `"application/pdf"`, `size` 0 trừ khi client nhét vào `metadata`. Hậu quả: `StoragePolicyService` thực tế kiểm tra giá trị mặc định (nghi ngờ lỗi; có bằng chứng ở 2 file).
4. **`StoragePolicyService`** cho phép MIME rỗng/null (:24) và chỉ tin MIME khai báo (không kiểm tra magic bytes/đuôi file); `size` âm không bị chặn.
5. **`DocumentService.delete`** trả `remoteObjectDeleted:true` cố định (:82) dù không có thao tác xóa ở storage -> thông tin sai lệch. `GET /documents/{id}` trả `status:"missing"` HTTP 200 thay vì 404 (:56).
6. **Trả dữ liệu giả thay vì 404** ở `CmsService.get/bySlug`, `CatalogService.get/findBySlug`, `ModuleWorkflowService.get` (status `missing`).
7. **Webhook inbox không có consumer:** dữ liệu `webhook_inbox` chỉ tích lũy (không thấy worker/processor, cũng không thấy dọn `webhook_receipts` hết hạn). Chữ ký sai ném `WebhookVerificationException` mà `rejected()` metric chưa thấy được gọi (grep chỉ thấy định nghĩa). `payload.id()` rỗng thì dùng chữ ký làm receipt key (chấp nhận được).
8. **SecurityConfig:** `GET /api/v1/reviews/admin` chỉ `authenticated()` (:117-119) – mọi người dùng đăng nhập (kể cả học viên) đọc được review admin, thiếu kiểm tra quyền (nghi ngờ; cần xác minh `@AuthenticatedEndpoint` có ý đồ này). Review `PATCH` approve/reject/feature và `recruitment/admin/**` không có rule riêng trong phần đã đọc (đọc dòng 111-251 chỉ thấy các nhóm nêu ở mục 4) -> có thể rơi vào rule mặc định (nghi ngờ, chưa xác minh `anyRequest`). `POST /api/v1/reviews` và `/recruitment/apply` công khai không có rate limit/captcha trong slice (nghi ngờ spam).
9. **Cây comment thảo luận:** `toComment` truyền `Map.of()` cho con (`StudentEngagementService.java:261`) nên comment cấp 3 trở xuống không xuất hiện trong feed dù được lưu (`reply` cho phép cha là bất kỳ comment cùng post) – nghi ngờ mất dữ liệu hiển thị.
10. **Feed thảo luận N+1/tải toàn bộ comment** của các post trong trang, không giới hạn; `student_discussion_comments.parent_id` FK `ON DELETE CASCADE`.
11. **`json()` / `parseJsonList()`** (`:367-378`) tự nối/cắt chuỗi JSON thay vì dùng Jackson; `attachmentIds` chứa dấu phẩy sẽ vỡ khi đọc lại (`split(",")`). Không validate attachment id thuộc tenant/người dùng. `assignmentId` không kiểm tra tồn tại.
12. **Không có kiểm tra ghi quyền xem lớp khi `reaction` target COMMENT** ngoài join lớp (đã có, :341-345) – OK; nhưng `viewerReactions` lọc theo `target_id in (ids)` không kèm `target_type` (:312) có thể gán nhầm reaction nếu trùng id giữa POST và COMMENT (xác suất thấp, id sinh bằng `IdGenerator`).
13. **`StudentEngagementService` đánh dấu `@Deprecated`** nhưng vẫn là implementation duy nhất (qua `ElearningOperations`); không có phân tách repository.
14. **Cache `authorizationPermissions`** khóa `'all'` toàn cục (không theo tenant): chấp nhận được nếu danh mục quyền là toàn hệ thống; chưa kiểm chứng việc invalidate khi thay đổi quyền (ngoài slice).
15. **Tên slice dễ gây nhầm:** `authorization/internal/catalog` là danh mục quyền, `learningstandards/internal/storage` là artifact chuẩn học tập – không thuộc public web/CMS.
16. **Model "migrated from erg-go"** dùng tên trường lệch chuẩn Java (`sHA256`, `cTR`, `hTTPStatus`, `eTag`, `durationMS`) – dễ lỗi khi serialize/mapping.
17. **Entity community** (`CommunityPost` …) có `@Data` + quan hệ LAZY hai chiều; `@Data` trên entity JPA có thể gây vấn đề equals/hashCode/toString (đã loại một phần bằng `onlyExplicitlyIncluded`/`@ToString.Exclude`; Follow/Media/Comment không loại trừ hoàn toàn – nghi ngờ). Hiện chưa dùng nên chưa gây lỗi.

## 10. Câu hỏi còn mở

1. Website công khai (`erg` CMS) hiện lấy dữ liệu bài viết/trang/menu/review từ đâu, vì backend này chỉ có stub in-memory? Có kế hoạch hiện thực hóa các module này (bảng, migration, repository) không?
2. Profile `prototype` và `erg.process-local.enabled` có bao giờ bật ở staging/production không?
3. Ai xử lý `webhook_inbox` (`pending`)? Đã có tiến trình ngoài (erg-go/bot service) đọc bảng này chưa? Lệnh bot (`CommandRegistry`, workflow wizard) sẽ chạy ở đâu?
4. Bảng `community_*` có tồn tại ở DB nào (do erg-go tạo) hay sẽ tạo mới? Quan hệ giữa cộng đồng (`CommunityPost`) và thảo luận lớp học (`student_discussion_*`) là gì – hai hệ riêng hay sẽ hợp nhất?
5. Crawler/RSS/trending/GSC sẽ chạy trong Spring hay vẫn ở dịch vụ khác? Nguồn dữ liệu và lịch chạy dự kiến?
6. Quyền cho `reviews`/`recruitment` admin có nằm ở rule mặc định cuối `SecurityConfig` hay cần bổ sung (`@AuthenticatedEndpoint` chỉ yêu cầu đăng nhập)?
7. `UploadDocumentRequest` (`name/content`) và `DocumentService` (`filename/mimeType/size`) cố ý khác nhau (upload nhị phân sẽ làm sau) hay là lỗi ánh xạ?
8. Có định dùng R2/S3 cho `Document` (`r2Url`) và watermark (`WatermarkConfig`) không; quy trình xóa file ở storage?
