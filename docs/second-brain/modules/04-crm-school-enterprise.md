# Module 04 - CRM, School, Organization, Enterprise Connectors, Recruitment, Public Disclosure, AI

Ghi chú: đường dẫn tính từ gốc repo `erg-spring`. `J/` = `src/main/java/com/erg/ergspringboot/`, `T/` = `src/test/java/com/erg/ergspringboot/`, `M/` = `src/main/resources/db/migration/postgres/`. Chỉ ghi điều đã đọc trong code; chỗ chưa chắc ghi "nghi ngờ". Slice gồm 112 file (xem mục 8 cho test).

## 1. Phạm vi và trách nhiệm

Slice này gom sáu nhóm nghiệp vụ quanh thực thể `education_units` (trường/trung tâm là "single school master" dùng chung cho CRM và LMS): (a) CRM bán hàng cho trường (lead, cơ hội/deal, follow-up, bàn giao); (b) School operations (hồ sơ vận hành trường, năm học, khối lớp, directory/workspace đọc cho LCMS); (c) Organization (danh sách education unit theo phạm vi truy cập); (d) Enterprise Connectors (đăng ký/đồng bộ/deprovision SIS-HRIS-SCIM qua adapter); (e) Recruitment và Public Disclosure; (f) AI content. Nhóm (a)-(d) là code thật có DB; nhóm (e), (f) chỉ là prototype in-memory (xem mục 7, 9).

## 2. Class/entity chính và vai trò

| Class | Đường dẫn | Vai trò |
|---|---|---|
| `CrmOperations` | `J/crm/CrmOperations.java` | Facade public của CRM; ủy quyền cho `CrmWorkspaceService` và `CrmSchoolService` (cả hai `@Deprecated`, gọi là "legacy adapter") |
| `CrmWorkspaceService` | `J/service/crm/CrmWorkspaceService.java` | Lead/opportunity/follow-up/handover workspace, idempotency, optimistic version |
| `CrmSchoolService` | `J/service/crm/CrmSchoolService.java` | Báo cáo trường đã ký theo SEO và hiệu suất SEO |
| `CrmWorkspaceController`, `CrmSchoolController` | `J/controller/crm/` | REST `/api/v1/crm/**` |
| `CrmSchoolProfile` (lead) | `J/model/crm/CrmSchoolProfile.java` | Hồ sơ lead gắn `school_id`; có `leadStatus`, `version` |
| `CrmSchoolDeal` (opportunity) | `J/model/crm/CrmSchoolDeal.java` | Deal: `stage`, `revenueLines`/`costLines` (jsonb), `idempotencyKey`, `signedRevenue` |
| `CrmSchoolActivity` (follow-up) | `J/model/crm/CrmSchoolActivity.java` | Hoạt động/follow-up: `type`, `dueAt`, `done` |
| `CrmHandover` | `J/model/crm/CrmHandover.java` | Bàn giao sang vận hành; `tenantId`, checklist jsonb, `status` |
| `CrmSchoolContact`, `CrmReferral` | `J/model/crm/` | Liên hệ trường; giới thiệu (referral). `CrmReferral` không có repository đọc/ghi trong luồng đã đọc ngoài `CrmReferralRepository` rỗng |
| `EducationUnit` | `J/model/organization/EducationUnit.java` | Trường/center (bảng `education_units`), `tenantId`, `slug`, `type`, `status`, `parentId`, `@Version` |
| `UserAccessScope`, `WorkShift` | `J/model/organization/` | Phạm vi truy cập user-đơn vị; ca làm việc (chỉ entity, không thấy service trong slice) |
| `OrganizationOperations` / `OrganizationEducationUnitQuery` | `J/organization/`, `J/organization/internal/` | Facade + query danh sách education unit có cache |
| `EducationUnitService` | `J/service/organization/EducationUnitService.java` | `@Deprecated`, bọc `OrganizationEducationUnitQuery` |
| `SchoolOperations` / `SchoolCommandService` | `J/school/SchoolOperations.java`, `J/service/school/SchoolCommandService.java` | Lệnh: tạo/sửa/lưu trữ/khôi phục trường, năm học, khối |
| `SchoolQueryOperations` / `SchoolOperationsReadService` | `J/school/`, `J/service/school/` | Đọc directory và workspace (cache) |
| `SchoolOperationalProfile`, `SchoolAcademicYear`, `SchoolGrade` | `J/model/school/` | Hồ sơ vận hành (PK = `school_id`), năm học, khối |
| `SchoolOperationsDemoSeedService/Initializer` | `J/service/school/` | Seed demo 5 trường (bật bằng property) |
| `LcmsSchoolOperationsGraphQlController` / `...MutationGraphQlController` | `J/controller/lcms/graphql/` | GraphQL đọc school; mutation class/student/subject/enrollment (ủy quyền `LmsOperationalOperations`, nằm ngoài slice) |
| `SchoolCommandController` | `J/controller/lcms/SchoolCommandController.java` | REST `/api/v1/lcms/schools` |
| `EnterpriseConnectorService` | `J/enterpriseconnectors/internal/EnterpriseConnectorService.java` | Orchestration connector: register, dry-run, sync, retry, deprovision |
| `EnterpriseConnectorPolicy` | `J/enterpriseconnectors/EnterpriseConnectorPolicy.java` | Validate `secret://` ref, chuẩn hóa field mapping + SHA-256 |
| `EnterpriseConnectorProviderPort`, `...ProviderRegistry`, `ReferenceEnterpriseConnectorAdapter` | `J/enterpriseconnectors/...` | Port adapter; registry theo `adapterKey`; adapter sandbox xác định (`REFERENCE`) |
| `EnterpriseConnector`, `...Run`, `...CommandReceipt` | `J/model/enterpriseconnectors/` | Connector, lần chạy, biên nhận idempotency |
| `Job`, `Candidate`, `CoverConfig`, `DetailBlock`, `DisclosureDocument` | `J/model/recruitment/`, `J/model/publicdisclosure/` | POJO Lombok thuần (không `@Entity`) "migrated from erg-go" |
| `RecruitmentJob`, `RecruitmentCandidate` | `J/model/recruitment/` | `@Entity` bảng `jobs`, `candidates`; package đăng ký là `dormant` |
| `AiContentController` | `J/controller/ai/AiContentController.java` | Stub `/api/v1/ai-content/**`, profile `prototype` |
| `ModuleWorkflowService` (ngoài slice, được dùng) | `J/service/support/ModuleWorkflowService.java` | Store in-memory `ConcurrentHashMap` phía sau recruitment/disclosure/ai |
| `EngagementController` (ngoài slice, được dùng) | `J/controller/engagement/EngagementController.java` | REST recruitment/public-disclosure/reviews, profile `prototype` |

## 3. Nghiệp vụ và luồng xử lý

### 3.1 CRM workspace (đọc)
`CrmWorkspaceService.workspace` (`J/service/crm/CrmWorkspaceService.java:84-112`):
1. Lấy tối đa 500 education unit của tenant (`PageRequest.of(0,500)`), lọc theo `access.canAccessEducationUnit` (dòng 85-88). Rỗng thì trả 4 list rỗng.
2. Với `schoolIds` đó nạp profiles (lead), primary contact (`isPrimary desc, createdAt asc`, dòng 342-347), deals, activities (sắp theo `dueAt`), handovers (lọc `tenantId`).
3. Map sang `WorkspaceResponse(leads, opportunities, followUps, handovers)`. Lead `ownerName` thực chất là user id (`leadOwnerUserId` nếu có, không thì `ownerUserId`, dòng 313) - tên field gây hiểu nhầm.

### 3.2 Lead status
`updateLeadStatus` (dòng 116-125): tải profile theo `leadId` -> `requireSchool` (đơn vị phải thuộc tenant và trong scope, dòng 276-283, ném `ForbiddenException`) -> `checkVersion(expectedVersion)` bắt buộc khớp `version` (dòng 406-410, `ConflictException`) -> status phải thuộc `LEAD_STATUSES` = `new_lead, consulted, waiting_decision, negotiating, won_pending_setup, active, paused, lost` (dòng 54-55, 423-429) -> cập nhật `lastTouchAt`. Không có state machine: chuyển trạng thái bất kỳ -> bất kỳ.

### 3.3 Opportunity (deal)
- Tạo (`createOpportunity`, dòng 128-149): lead -> `requireSchool` -> bắt buộc header `X-Idempotency-Key` (<=192 ký tự, dòng 412-421) -> tìm deal theo `(ownerUserId, idempotencyKey)`; nếu có và cùng `schoolId` thì replay, khác trường thì `ConflictException` -> tạo deal mới, owner là người gọi.
- `applyOpportunity` (dòng 285-297): `stage` thuộc `OPPORTUNITY_STAGES` = `discovery, proposal, pnl_review, contracting, handover, active`; trim code/năm học/kỳ hạn/tháng bắt đầu; lưu `revenueLines`, `costLines` dạng `List<Map>`; `expectedRevenue` = tổng `pricePerStudent * classCount * studentsPerClass * durationMonths * (1 - discountRate)` (dòng 299-305). Tiền được tính bằng `BigDecimal`.
- Cập nhật (`updateOpportunity`, dòng 152-164): kiểm version, giữ nguyên `code`, áp lại các trường còn lại. Không có state machine giữa các stage.
- Lưu ý: stage của deal ở workspace (`discovery..active`) khác với stage mà báo cáo hiệu suất đếm (`signed`, `lost`, xem 3.6). Không thấy đoạn code nào trong slice chuyển deal sang `signed`/`lost` hay set `signedAt`/`signedRevenue` (xem mục 9).

### 3.4 Follow-up
`createFollowUp` (dòng 167-198): lead -> `requireSchool` -> idempotency theo `(actorUserId, key)` -> nếu có `opportunityId` thì deal phải cùng `schoolId` với lead (`BadRequestException` nếu không) -> `type` thuộc `call, meeting, proposal, contract, handover` -> lưu `title` (cũng gán vào `content`), `dueAt`, `done=false`. `updateFollowUpCompletion` (dòng 202-212): kiểm version, set `done`. Lưu ý dòng 205-212: tải activity bằng `findById` rồi mới `requireSchool`.

### 3.5 Handover (bàn giao)
- Tạo (`createHandover`, dòng 216-245): lead -> `requireSchool` -> opportunity phải cùng school với lead -> idempotency theo `(tenantId, requestedBy, key)` -> `status` = `ready` nếu `isReady(checklist)` ngược lại `draft`.
- `isReady` (dòng 398-404): checklist không rỗng và `số item done >= size - 1`, tức là cho phép thiếu đúng 1 mục chưa xong (xem mục 9 - nghi ngờ lỗi/ý đồ).
- Duyệt (`approveHandover`, dòng 249-268): tìm theo `(id, tenantId)` -> `requireSchool` -> kiểm version -> `isReady` phải đúng, nếu không `BadRequestException("CRM handover checklist is not ready for approval")` -> `status="approved"`, lead `leadStatus="active"` + `lastTouchAt`, deal `stage="active"`. Không kiểm status hiện tại (có thể duyệt lại bản đã `approved`). Không thấy bước nào kích hoạt `EducationUnit.status`/`activatedAt` hoặc tạo school operational profile (xem mục 10).
- State handover: `draft` / `ready` -> `approved` (không có transition ngược/từ chối trong code).

### 3.6 Báo cáo SEO (legacy reporting)
`CrmSchoolService` (`J/service/crm/CrmSchoolService.java`):
- `signedSchoolsBySeo` (dòng 55-65): deal `stage ignore case = 'signed'` của `ownerUserId` sắp theo `signedAt desc`; gộp education unit, profile (người giới thiệu), primary contact. Cờ `signed` ở controller bị bỏ qua: cả hai nhánh gọi cùng hàm (`J/controller/crm/CrmSchoolController.java:31-33`).
- `performance` (dòng 68-102): dùng `aggregatePerformance` (JPQL, `J/repository/crm/CrmSchoolDealRepository.java:22-37`) trên `createdAt between from and to`; `inProgress = max(0, total - signed - lost)`; `conversionRate = signed/total` (scale 2, HALF_UP); `averageDealValue = revenue/signed`. Nhánh fallback tính trong bộ nhớ khi aggregate null (khó xảy ra vì aggregate JPQL luôn trả 1 dòng; nghi ngờ dead code). Cột `signedDealCount` của aggregate không được dùng.

### 3.7 School: tạo/sửa/lưu trữ
`SchoolCommandService` (`J/service/school/SchoolCommandService.java`). Mọi lệnh ghi `create/update/archive/restore` có `@CacheEvict(allEntries)` cho 4 cache (`organizationEducationUnits`, `organizationEducationUnitEntities`, `lcmsSchoolDirectory`, `lcmsSchoolOperations`) (dòng 59,99,134,151).
- `create` (dòng 60-95): `schoolType` thuộc `PRIMARY, SECONDARY, HIGH_SCHOOL, UNIVERSITY, TRAINING_CENTER` (dòng 33-34); sinh id, slug duy nhất theo tenant (bỏ dấu, `-`; nếu trùng thì thêm 6 ký tự cuối id, dòng 337-342); tạo `EducationUnit` (`source="LCMS"`, status mặc định `ONBOARDING`), năm học `CURRENT` (`startYear`, `endYear=startYear+1`), `SchoolOperationalProfile`, rồi sinh khối mặc định.
- Khối mặc định (dòng 252-267): PRIMARY 1-5, SECONDARY 6-9, HIGH_SCHOOL 10-12, còn lại không có; label `"Khối N"`, source `DEFAULT`.
- `update` (dòng 100-130): `assertScope` -> kiểm `version` của profile (`ConflictException`) -> không cho đổi `schoolType` khi còn lớp `active` (dòng 108-110) -> cập nhật trường cung cấp, `status` thuộc `DRAFT, ONBOARDING, ACTIVE, PAUSED, ARCHIVED` -> đồng bộ lại khối mặc định theo loại mới.
- `archive` (dòng 135-148): chặn nếu còn lớp `active`; set `ARCHIVED` cho cả unit và profile. `restore` (dòng 152-162): set `PAUSED` (không kiểm đang `ARCHIVED`).
- State trường: `DRAFT/ONBOARDING/ACTIVE/PAUSED/ARCHIVED`; chuyển bất kỳ qua `update.status`, ràng buộc DB CHECK `ck_school_profile_status` (`M/V50__school_operational_core.sql`).

### 3.8 Năm học và khối
- `createAcademicYear` (dòng 165-181): kiểm `endsOn >= startsOn`; unique `(tenant, school, startYear)` (`ConflictException`); nếu `activate=true` thì đóng năm `CURRENT` hiện tại, tạo năm mới `CURRENT`, gán vào profile; luôn sinh khối mặc định cho năm mới.
- `activateAcademicYear` (dòng 184-193): kiểm version, đóng năm hiện tại, set năm này `CURRENT` (không kiểm trạng thái hiện tại nên có thể kích hoạt lại năm `CLOSED`).
- `closeAcademicYear` (dòng 197-210): set `CLOSED`; nếu là năm hiện tại của profile thì `currentAcademicYearId=null` (trường rơi vào trạng thái "không có năm hiện tại", các thao tác khối sẽ ném `ConflictException("School has no current academic year.")`, dòng 297-300).
- `syncDefaultGrades`, `createGrade` (mã khối upper-case, unique trong năm hiện tại), `updateGrade` (label/sortOrder/status `ACTIVE|INACTIVE|ARCHIVED`).
- Trạng thái năm học: `PLANNED`, `CURRENT`, `CLOSED`, (DB còn cho `ARCHIVED` nhưng code không dùng). DB đảm bảo tối đa 1 năm `CURRENT`/trường bằng unique partial index `uk_school_year_current` (V50).

### 3.9 School đọc (GraphQL)
- `schoolDirectory` (`J/controller/lcms/graphql/LcmsSchoolOperationsGraphQlController.java:27-38`): cần quyền `lms.school.read|lms.school.update|lms.manage|*`; tenant lấy từ `accessSupport.verifiedTenantId(principal, input.tenantId())`; `allScope` = principal wildcard, scope chứa `*`, hoặc `curriculum.manage:*`; gọi `SchoolOperationsReadService.directory` (native SQL `findDirectory`, phân trang `size` tối đa 100, mặc định 20, cache 30s theo key đầy đủ, `J/service/school/SchoolOperationsReadService.java:60-71`). Đếm lớp/học sinh loại `archived`.
- `schoolOperations` (dòng 41-61): bắt buộc `schoolId`, kiểm quyền + `assertEducationUnitScope`; chỉ nạp các section được chọn trong selection set (`academicYears, grades, classes, students, subjects`); giới hạn cứng 1000 học sinh / 500 lớp / 500 môn, vượt thì `BadRequestException` (dòng 38-40, 197-203); `permissions` trả về cờ canCreate/Update/Archive/ManageClasses theo quyền wildcard-aware.
- Gán môn cho học sinh là giả lập: `assignedSubjectIds` chọn 2 môn đầu + môn thứ 3/4 theo `hashCode` id học sinh (dòng 189-195); `teacherCount` luôn bằng 1 (dòng 186). Đây là dữ liệu tính toán giả, không từ bảng đăng ký thực (xem mục 9).

### 3.10 Organization
`OrganizationEducationUnitQuery.list` (`J/organization/internal/OrganizationEducationUnitQuery.java:23-35`): cache `organizationEducationUnits` key `{tenantId, scopeFingerprint}`; nếu scope chứa `*` thì 500 unit đầu của tenant, nếu rỗng thì list rỗng, còn lại `IN (managedIds)` tối đa 500.

### 3.11 Enterprise connectors
`EnterpriseConnectorService` (`J/enterpriseconnectors/internal/EnterpriseConnectorService.java`), tất cả dùng transaction manager `postgresTransactionManager`:
1. `register` (dòng 95-137): `ensureWrite` (quyền `enterprise.connectors.manage`), tenant từ `AuthenticatedTenant.require(access.tenantId())`; validate adapter/provider/displayName; `secretRef` phải là `secret://...` (<=255, không xuống dòng/`=`/khoảng trắng, `EnterpriseConnectorPolicy.java:20-29`); field mapping 1-32 entry, key/value <=96, sắp xếp rồi băm SHA-256 (`mappingHash`, dòng 31-49); adapter phải tồn tại (`registry.require`) và `supports(providerKind)`; `X-Idempotency-Key` bắt buộc (dòng 442-444); hash payload -> `begin()` trả replay nếu cùng key+hash, `ConflictException` nếu cùng key khác payload (dòng 316-322); `providerKey` unique theo tenant (`ConflictException`); lưu connector `ACTIVE`, biên nhận, phát `EnterpriseConnectorRegistered`, ghi audit. Response luôn che `secretRef` thành `"secret://***"` (dòng 476).
2. `dryRun` (dòng 158-183): quyền sync hoặc manage; `expectedVersion` phải bằng `recordVersion` của connector; gọi `provider.preview`; lưu run `mode=DRY_RUN`, `status=PREVIEW`; không phát event.
3. `sync` (dòng 187-208): tương tự, gọi `provider.sync`, `apply` kết quả, phát `EnterpriseConnectorSyncCompleted`, audit.
4. `retry` (dòng 212-244): chỉ run `FAILED` hoặc `RETRYABLE_FAILED`; `retryCount < MAX_RETRIES (3)`; version run phải khớp; gọi lại `provider.sync(descriptor, null)`; set `mode=RETRY`, `retryCount+1`.
5. `deprovision` (dòng 248-280): `externalIds` 1-100 (dedup), `reason` bắt buộc; gọi `provider.deprovision`; run `mode=DEPROVISION`: `createdCount`=deprovisioned, `failedCount`=skipped; phát `EnterpriseConnectorDeprovisioned`, audit.
- Trạng thái connector: `ACTIVE` (và `DISABLED` chỉ có trong CHECK DB, code không có lệnh nào chuyển sang `DISABLED`). Trạng thái run: `PREVIEW, SUCCEEDED, FAILED, RETRYABLE_FAILED, DEPROVISIONED` (CHECK DB).
- Adapter `REFERENCE` (`ReferenceEnterpriseConnectorAdapter.java`) tạo 3-7 id giả xác định từ hash, không gọi mạng; hỗ trợ mọi `ProviderKind`.

### 3.12 Recruitment, Public Disclosure, AI
Không có service nghiệp vụ thật. `EngagementController` và `AiContentController` (cả hai `@Profile("prototype")`) đẩy dữ liệu vào `ModuleWorkflowService`, một `ConcurrentHashMap` trong bộ nhớ process, bị chặn nếu `erg.process-local.enabled=false` (`J/platform/persistence/ProcessLocalStatePolicy.java`). Không có state machine ứng tuyển, không sinh `trackingCode`, không lưu CV. AI: `generate/refine` chỉ tạo job "queued" ảo (`ModuleWorkflowService.job`, dòng 94-104), không gọi nhà cung cấp AI nào; các endpoint key trả dữ liệu cố định (`secretPreview "****"`).

## 4. Quy tắc nghiệp vụ, validation, phân quyền

| Quy tắc | Nguồn |
|---|---|
| Mọi thao tác CRM ghi gắn với trường phải: trường thuộc tenant của principal (`findByIdAndTenantId`) và nằm trong education-unit scope | `J/service/crm/CrmWorkspaceService.java:276-283` |
| Sửa lead/deal/follow-up/handover bắt buộc `expectedVersion` khớp (chống ghi đè) | `CrmWorkspaceService.java:406-410` |
| Tạo opportunity/follow-up/handover bắt buộc `X-Idempotency-Key` (<=192) | `CrmWorkspaceService.java:412-421`; unique index `uq_crm_*_idempotency` (`M/V82__crm_workspace_contract.sql`) |
| RevenueLine/CostLine: số lượng >= 0, `discountRate` 0..1, giá >= 0, `code` <=100 | `J/common/dto/crm/CrmWorkspaceDtos.java:62-95` |
| Phân quyền HTTP CRM: `GET /crm/seo/*/performance` và `/crm/analytics/**` cần `crm.analytics.read`; các GET khác cần `crm.school.read`; mọi method khác (POST/PATCH) cần `crm.deal.manage` | `J/platform/security/SecurityConfig.java:164-169` |
| Role seed: `seo.staff` (school.create/read, deal/contact/activity manage), `seo.lead`/`seo.manager` (+ analytics.read), `executive.director` (analytics.read + lms.read), `center.manager`/`school.manager` (lms.manage; center còn `organization.school.activate`) | `M/V6__school_crm_permission_seed.sql` |
| School REST: tạo `lms.school.create`; sửa/năm học/sync khối `lms.school.update` hoặc `lms.class.manage`; archive/restore `lms.school.archive`; tạo/sửa khối `lms.class.manage` (mỗi quyền kèm `*`) | `J/lcms/authorization/LcmsSchool*.java`, `J/controller/lcms/SchoolCommandController.java:35-109` |
| Lệnh school (trừ create) gọi `assertScope` (`canAccessEducationUnit`) và lọc tenant ở repo | `SchoolCommandService.java:317-319` |
| Không đổi loại trường / không archive khi còn lớp `active` | `SchoolCommandService.java:108-110, 139-141` |
| Update/activate/close dùng `version` (optimistic) của profile/năm học | `SchoolCommandService.java:104, 187, 200` |
| Request school: tên <=255, `academicYearStart` 2000-2200, email hợp lệ, phone <=50 | `J/common/request/school/SchoolRequests.java` |
| GraphQL mutation school: quyền typed + `verifiedTenantId` + `assertEducationUnitScope`; `version` là chuỗi số không âm | `J/controller/lcms/graphql/LcmsSchoolOperationsMutationGraphQlController.java:158-203` |
| Connector: `@PreAuthorize` theo `enterprise.connectors.read|sync|manage` (+`*`) và kiểm lại trong service; quyền bị deny (`deniedPermissions` chứa `*` hay đúng quyền) thì từ chối | `J/enterpriseconnectors/authorization/*.java`; `EnterpriseConnectorService.java:360-384` |
| Connector: tenant luôn lấy từ security context, không từ body; secret chỉ là tham chiếu `secret://` | `EnterpriseConnectorService.java:390-395`, `EnterpriseConnectorPolicy.java:20-29` |
| Connector: DB bật và FORCE Row Level Security theo `app.tenant_id` cho 3 bảng | `M/V97__enterprise_connectors_contract.sql` |
| Recruitment: `GET /recruitment/jobs/**`, `POST /recruitment/apply` công khai; `GET /public-disclosure/**` công khai; `POST`/`DELETE /public-disclosure/**` cần `public-disclosure.manage` | `SecurityConfig.java:121-130, 248-251` |
| AI: `/ai-content/keys/**` cần `api-keys.manage`; còn lại `ai-content.use` | `SecurityConfig.java:171-174` |
| Education units REST: không có matcher riêng, rơi vào `/api/v1/**` authenticated; dữ liệu lọc theo scope trong service | `SecurityConfig.java:252`, `OrganizationEducationUnitQuery.java:26-33` |

## 5. API

### REST
| Method | Path | Controller | Mô tả |
|---|---|---|---|
| GET | `/api/v1/crm/workspace` | `CrmWorkspaceController` | Workspace CRM (leads, opportunities, followUps, handovers) |
| PATCH | `/api/v1/crm/leads/{leadId}/status` | idem | Đổi trạng thái lead (body `status`, `expectedVersion`) |
| POST | `/api/v1/crm/opportunities` | idem | Tạo opportunity, header `X-Idempotency-Key` |
| PATCH | `/api/v1/crm/opportunities/{opportunityId}` | idem | Sửa opportunity |
| POST | `/api/v1/crm/follow-ups` | idem | Tạo follow-up (idempotent) |
| PATCH | `/api/v1/crm/follow-ups/{followUpId}/completion` | idem | Đánh dấu hoàn thành |
| POST | `/api/v1/crm/handovers` | idem | Tạo handover (idempotent) |
| POST | `/api/v1/crm/handovers/{handoverId}/approve` | idem | Duyệt handover |
| GET | `/api/v1/crm/seo/{seoId}/schools?signed=` | `CrmSchoolController` | Danh sách trường đã ký của SEO |
| GET | `/api/v1/crm/seo/{seoId}/performance?from=&to=` | idem | Chỉ số hiệu suất SEO |
| GET | `/api/v1/education-units`, `/api/v1/centers` | `EducationUnitController` | Danh sách đơn vị trong scope (JSON `parent_id`, `logo_url`) |
| POST | `/api/v1/lcms/schools` | `SchoolCommandController` | Tạo trường (201) |
| PUT/PATCH | `/api/v1/lcms/schools/{schoolId}` | idem | Sửa (hai method cùng gọi `update`, hành vi như nhau) |
| DELETE | `/api/v1/lcms/schools/{schoolId}` | idem | Lưu trữ (archive) |
| POST | `/api/v1/lcms/schools/{schoolId}/restore` | idem | Khôi phục -> `PAUSED` |
| POST | `/api/v1/lcms/schools/{schoolId}/academic-years` | idem | Tạo năm học |
| POST | `.../academic-years/{yearId}/activate` , `/close` | idem | Kích hoạt/đóng năm học |
| POST | `.../grades/sync-defaults` | idem | Đồng bộ khối mặc định |
| POST / PATCH | `.../grades`, `.../grades/{gradeId}` | idem | Tạo/sửa khối |
| POST | `/api/v1/enterprise-connectors` | `EnterpriseConnectorController` | Đăng ký connector |
| GET | `/api/v1/enterprise-connectors`, `/{connectorId}` | idem | Liệt kê (top 100)/chi tiết |
| POST | `/{connectorId}/dry-run`, `/sync`, `/deprovision`, `/runs/{runId}/retry` | idem | Các lệnh vận hành (idempotent) |
| GET/POST/PUT/PATCH/DELETE | `/api/v1/recruitment/...`, `/api/v1/public-disclosure/...`, `/api/v1/reviews/...` | `EngagementController` (profile `prototype`) | Stub in-memory |
| POST/GET/DELETE | `/api/v1/ai-content/generate|refine|status/{jobId}|history|templates|provider-health|keys...` | `AiContentController` (profile `prototype`) | Stub |

Tất cả trả `ApiResponse` với `requestId` từ `RequestCorrelation`.

### GraphQL
| Operation | Resolver | Ghi chú |
|---|---|---|
| Query `lcms.schoolDirectory(input)` | `LcmsSchoolOperationsGraphQlController.schoolDirectory` | Khai báo `M/../graphql/modules/00-shell.graphqls:40` (type `LcmsQuery`) |
| Query `lcms.schoolOperations(input)` | `...schoolOperations` | Idem dòng 41; types ở `graphql/modules/10-school-quiz.graphqls` |
| Mutation `createSchoolClass/updateSchoolClass/archiveSchoolClass` | `LcmsSchoolOperationsMutationGraphQlController` | `graphql/modules/45-school-operations-mutations.graphqls` |
| Mutation `createSchoolStudent/updateSchoolStudent/archiveSchoolStudent/importSchoolStudents` | idem | import có `idempotencyKey` |
| Mutation `createSchoolSubject/updateSchoolSubject/archiveSchoolSubject` | idem | |
| Mutation `enrollSchoolStudent/bulkEnrollSchoolStudents` | idem | idempotent theo `idempotencyKey` |

CRM chỉ có namespace `crm: PlatformNamespace` trong `Query` (`00-shell.graphqls:4`); không có resolver CRM thật trong slice. Lưu ý: các thao tác ghi school cấp trường (create/update school, năm học, khối) chỉ có REST, không có GraphQL.

## 6. Dữ liệu

| Bảng | Entity | Điểm chính |
|---|---|---|
| `education_units` | `EducationUnit` | `tenant_id` NOT NULL (V50), `version`, unique `uk_education_units_tenant_slug (tenant_id, lower(slug))`, index tenant/type/status/name. Tạo ở `M/V1__postgres_core_schema.sql:73` |
| `crm_school_profiles` | `CrmSchoolProfile` | Tạo V5; V82 thêm `lead_status, probability, expected_*, risks jsonb, version`; FK -> `education_units ON DELETE CASCADE` NOT VALID (V15), validate ở V16 |
| `crm_school_contacts`, `crm_referrals` | `CrmSchoolContact`, `CrmReferral` | V5 |
| `crm_school_deals` | `CrmSchoolDeal` | V5 + V82: `revenue_lines`, `cost_lines` jsonb, `idempotency_key`; unique partial `uq_crm_deals_owner_idempotency (owner_user_id, idempotency_key)` |
| `crm_school_activities` | `CrmSchoolActivity` | V82: `title, due_at, done`; unique partial `uq_crm_activities_actor_idempotency` |
| `crm_handovers` | `CrmHandover` | V82: có `tenant_id`, FK school/lead/opportunity, unique partial `(tenant_id, requested_by, idempotency_key)`, RLS bật (chỉ ENABLE, không FORCE) |
| `school_operational_profiles` | `SchoolOperationalProfile` | PK `school_id`, CHECK loại/trạng thái, unique `(tenant_id, school_id)` (V50) |
| `school_academic_years` | `SchoolAcademicYear` | CHECK `end_year = start_year + 1`, unique `(tenant, school, start_year)`, unique partial 1 `CURRENT` (V50) |
| `school_grades` | `SchoolGrade` | unique `(tenant, school, academic_year, grade_code)` (V50) |
| `enterprise_connectors`, `_runs`, `_command_receipts` | cùng tên | V97: RLS ENABLE+FORCE, unique `(tenant_id, provider_key)`, unique `(tenant_id, operation, idempotency_key)`, CHECK `retry_count <= 3` |
| `user_access_scopes`, `work_shifts` | `UserAccessScope`, `WorkShift` | V1 (dòng 210, 220), FK ở V12 |
| `jobs`, `candidates` | `RecruitmentJob`, `RecruitmentCandidate` | Không tìm thấy `CREATE TABLE jobs/candidates` khi grep migration (nghi ngờ chưa có bảng); `JpaRegistrationCatalog` đánh dấu `dormant("recruitment", 2)` (`J/config/persistence/JpaRegistrationCatalog.java:37`) |
| (không có) | `DisclosureDocument` | POJO, không map bảng |

Quan hệ: CRM bám `school_id` -> `education_units` (không nhân đôi bảng trường; test `CrmSchoolMigrationTest`). `crm_school_deals`, `crm_school_profiles`, `crm_school_activities`, `crm_school_contacts` KHÔNG có cột `tenant_id` (chỉ `crm_handovers` có), nên cô lập tenant dựa vào `school_id` -> `education_units.tenant_id` ở tầng service. Quyền mới (V50): `lms.school.*`, `lms.class.manage`. Registry JPA: `crm`, `enterpriseconnectors`, `organization`, `school` được bật (`JpaRegistrationCatalog.java:~25-45`).

## 7. Tích hợp ngoài và sự kiện

- Sự kiện Spring in-process (`ApplicationEventPublisher`): `EnterpriseConnectorRegistered`, `EnterpriseConnectorSyncCompleted`, `EnterpriseConnectorDeprovisioned` (metadata `ModuleEventMetadata`, `schemaVersion=1`, `correlationId`=request id hoặc event id). Không thấy trong slice listener hay outbox nào dùng chúng; chỉ `publishEvent` (nghi ngờ chưa có consumer; chưa grep toàn repo để xác nhận).
- Audit: `AuditOperations.record` cho mọi lệnh connector (`EnterpriseConnectorService.java:349-358`), details bị giới hạn 12 mục và stringify.
- Provider ngoài: chỉ có `ReferenceEnterpriseConnectorAdapter` (`adapterKey=REFERENCE`). Registry ném lỗi khi trùng `adapterKey`. Không có adapter SIS/HRIS/SCIM thật.
- Cache (Caffeine/L1 + L2 theo `PlatformCacheDescriptorContributor`, `J/config/PlatformCacheDescriptorContributor.java:29-37`): `organizationEducationUnits` (60s), `organizationEducationUnitEntities` (60s), `lcmsSchoolDirectory` (30s), `lcmsSchoolOperations` (30s). Evict `allEntries` từ `SchoolCommandService` (create/update/archive/restore) và `LmsOperationalCommandService`.
- `EducationUnitRepository.findByTenantIdOrderByNameAsc(String)` có `@Cacheable("organizationEducationUnitEntities", key=tenantId)` ngay trên repository (`J/repository/organization/EducationUnitRepository.java:21-22`).
- Seed demo: `SchoolOperationsDemoSeedInitializer` chạy khi `erg.lifecycle.school-operations-seed-on-startup=true`, tenant cố định `"erg"` (`...Initializer.java:10,26`).
- AI provider: không có tích hợp (stub). Không outbox cho CRM/School.

## 8. Test hiện có

| Test | Kiểm chứng |
|---|---|
| `T/service/crm/CrmWorkspaceServiceTest` | workspace lọc theo tenant+scope; từ chối đổi lead ngoài tenant; replay idempotency tạo opportunity |
| `T/service/crm/CrmSchoolServiceTest` | danh sách trường đã ký + người giới thiệu; tính conversion/revenue (nhánh fallback in-memory) |
| `T/controller/crm/CrmSchoolControllerContractTest`, `CrmWorkspaceControllerTest` | hợp đồng JSON; dùng tenant/actor đã xác thực |
| `T/crm/CrmBoundaryTest`, `T/organization/OrganizationBoundaryContractTest`, `T/school/*BoundaryTest`, `T/enterpriseconnectors/EnterpriseConnectorBoundaryTest` | ràng buộc kiến trúc (controller chỉ phụ thuộc facade, không import persistence) |
| `T/config/CrmSchoolMigrationTest`, `SchoolOperationalCoreMigrationTest`, `EnterpriseConnectorMigrationContractTest` | nội dung SQL migration (chuỗi), quyền seed, RLS |
| `T/model/crm/CrmSchoolModelMappingTest` | mapping cột entity |
| `T/service/school/SchoolCommandServiceTest` | tạo trường tiểu học sinh 5 khối mặc định; đại học không có khối |
| `T/service/school/SchoolOperationsReadServiceTest` | chặn vượt 1.000 học sinh; `gradeCount` khi không chọn grades |
| `T/service/school/SchoolOperationsDemoSeed*Test` | seed 5 trường; bật/tắt bằng property |
| `T/lcms/LcmsSchoolOperationsMutationGraphQlControllerTest` | alias quyền subject; thiếu principal -> `AuthenticationRequiredException`; version âm bị từ chối |
| `T/config/SchoolOperationsGraphQlSchemaTest`, `SchoolCacheRegistrationTest`, `SchoolJpaRegistrationTest` | schema có query; cache đăng ký; entity đăng ký |
| `T/enterpriseconnectors/EnterpriseConnectorServiceTest`, `...PolicyTest`, `ReferenceEnterpriseConnectorAdapterTest` | register tenant-scoped/redact/replay; dry-run-sync-deprovision; thiếu tenant fail-closed; reuse key khác payload -> conflict; secret ref; mapping hash ổn định; adapter xác định |
| `T/platform/security/EnterpriseAuthBoundaryContractTest`, `GraphQlEnterpriseSecurityAuditTest`, `AccessScope*Test` | profile enterprise tắt login local; audit đảm bảo GraphQL |

Khoảng trống rõ ràng (suy ra từ danh sách test, chưa chạy): không test cho `approveHandover` (duyệt, `isReady` cho phép thiếu 1 mục), `createHandover`, `createFollowUp`, `updateOpportunity`/`applyOpportunity` (tính `expectedRevenue`); không test `retry` của connector (đường tăng `retryCount`, giới hạn 3), không test version conflict ở dry-run/sync; không test `SchoolCommandService.update/archive/activate/close`, chuyển năm học, hay cache eviction; không test phân quyền HTTP CRM (`/crm/seo/*`) thật; không test cho `EngagementController`/`AiContentController`/`ModuleWorkflowService`; không có integration test với Postgres cho RLS connector/handover (chỉ kiểm chuỗi SQL).

## 9. Rủi ro, nợ kỹ thuật, nghi ngờ bug

1. **Báo cáo SEO không giới hạn theo chủ sở hữu/tenant (nghi ngờ lỗ hổng IDOR/rò rỉ tenant).** `GET /api/v1/crm/seo/{seoId}/schools` chỉ cần `crm.school.read` (`SecurityConfig.java:166-167`); `CrmSchoolController` không nhận `UserAccessContext` (`J/controller/crm/CrmSchoolController.java:25-45`) và `CrmSchoolService` truy vấn theo `ownerUserId` tự do, không lọc tenant/scope (`CrmSchoolService.java:55-65`, `CrmSchoolDealRepository.java:18-20`). Người có `crm.school.read` có thể đọc deal/liên hệ của SEO bất kỳ. Cần xác minh thêm ở tầng khác (chưa thấy bộ lọc).
2. **Retry connector luôn đặt `retryCount` = 1 (nghi ngờ bug).** `apply(run, result)` ghi đè `retryCount` bằng `result.retryCount()` (dòng 287), rồi `retry` đặt `run.getRetryCount()+1` (dòng 237) lấy giá trị vừa bị ghi đè. Với adapter trả `retryCount=0` (như REFERENCE), mỗi lần retry kết quả đều là 1, nên giới hạn `MAX_RETRIES=3` (dòng 223) không bao giờ chạm. Run sau retry thay `mode` thành `RETRY`, mất `mode` gốc.
3. **Gọi adapter ngoài trong transaction DB** (`dryRun/sync/retry/deprovision` đều `@Transactional` và gọi `provider.*` trong đó, dòng 171, 199, 234, 262). Adapter thật chậm/lỗi sẽ giữ transaction và kết nối; sự kiện `publishEvent` đồng bộ trong transaction. Hiện chỉ có adapter giả nên chưa lộ.
4. **Connector không có vòng đời đầy đủ:** không có lệnh disable/cập nhật connector, `deprovision` không đổi trạng thái connector, `DISABLED` tồn tại ở DB nhưng không dùng; `status` run do adapter quyết định và được `toUpperCase` nhưng không validate với tập CHECK DB (adapter trả giá trị lạ sẽ lỗi DB).
5. **`isReady` cho phép thiếu 1 mục checklist** (`CrmWorkspaceService.java:403`, `done >= size-1`): handover có thể ở `ready`/được duyệt khi còn 1 mục chưa xong; không rõ đây có phải ý đồ nghiệp vụ không. Checklist 1 mục luôn đủ điều kiện kể cả `done=false`.
6. **Không có state machine CRM.** Lead/opportunity/handover chuyển trạng thái tự do; `approveHandover` không kiểm trạng thái hiện tại (duyệt lặp, duyệt bản `draft` nếu checklist đủ) và còn đặt lead `active`, deal `active` mà không kiểm deal thuộc tenant/scope (chỉ qua `handover.schoolId`).
7. **Lệch giữa workspace và báo cáo:** báo cáo SEO đếm deal có `stage` = `signed`/`lost`, nhưng `OPPORTUNITY_STAGES` ghi (`discovery..active`) không chứa `signed`/`lost`; trong slice không thấy code đặt `signedAt/signedRevenue/lostAt`. Dữ liệu `signed` chỉ có thể đến từ seed/nguồn khác (chưa xác minh) - nghi ngờ báo cáo hiệu suất luôn rỗng cho deal tạo bằng workspace.
8. **Dữ liệu CRM thiếu cột `tenant_id` và RLS** (trừ `crm_handovers`); cô lập tenant chỉ ở service. `crm_handovers` chỉ `ENABLE ROW LEVEL SECURITY` không `FORCE` (khác V97) nên chủ bảng vẫn bỏ qua RLS.
9. **`CrmSchoolController`: tham số `signed` vô tác dụng** (`signed ? x : x`, dòng 31-33).
10. **Cache school có thể cũ:** `createAcademicYear/activate/close/syncDefaultGrades/createGrade/updateGrade` không có `@CacheEvict` (`SchoolCommandService.java:164-250`) trong khi `lcmsSchoolOperations` (30s) trả `academicYears/grades/currentAcademicYearId`. Chỉ create/update/archive/restore (và lệnh LMS) mới evict. Ngoài ra khóa cache `directory` chứa danh sách `schoolIds` (key lớn), TTL 30s.
11. **Thiếu kiểm tra phạm vi/tenant cho một số lệnh:** `createGrade`/`updateGrade` không gọi `unit(...)` và `updateGrade` không đối chiếu năm học (`SchoolCommandService.java:225-250`; vẫn lọc tenant+school qua `profile()`/filter); mutation `updateSchoolSubject/archiveSchoolSubject/enrollSchoolStudent/bulkEnroll...` không gọi `assertEducationUnitScope` ở controller (`LcmsSchoolOperationsMutationGraphQlController.java:120-156`), phụ thuộc kiểm tra trong `LmsOperationalOperations` (ngoài slice, chưa đọc). `createSchool` không `assertScope` (đúng ý đồ vì unit mới) nhưng người tạo có thể không thấy trường vừa tạo nếu scope hạn chế.
12. **`activateAcademicYear` có thể kích hoạt lại năm `CLOSED`**; `restore` không kiểm đang `ARCHIVED`; `update.status` cho phép tự đặt `ARCHIVED` mà bỏ qua kiểm tra lớp đang hoạt động của `archive`.
13. **Dữ liệu giả trong workspace:** `assignedSubjectIds` dựa hash id học sinh; `teacherCount=1` cố định (`SchoolOperationsReadService.java:186-195`). Không dùng cho nghiệp vụ thật.
14. **`LcmsSchoolUpdate` cho phép `lms.class.manage`** sửa hồ sơ trường, năm học, sync khối (`J/lcms/authorization/LcmsSchoolUpdate.java`): quyền quản lý lớp có thể thay đổi cấu trúc trường.
15. **Recruitment/Disclosure/AI là prototype in-memory:** dữ liệu mất khi restart, không theo tenant (store key chỉ `module:type`, `ModuleWorkflowService.java:124-126`), `get` trả object "missing" khi không có, `update` tạo mới nếu không có. `PUT/PATCH/DELETE /recruitment/**` và `GET /recruitment/admin/candidates` chỉ `authenticated` (không có quyền riêng, `EngagementController.java:68-97`; ứng viên chứa email/phone/CV nên nhạy cảm). `POST /recruitment/apply` công khai không giới hạn kích thước/validate (`Map` tùy ý). Công khai `GET /recruitment/tracking/{code}` trả theo id/code tuỳ ý. Các endpoint này chỉ tồn tại ở profile `prototype`.
16. **Entity tuyển dụng trùng lặp:** `Job`/`Candidate` (POJO) và `RecruitmentJob`/`RecruitmentCandidate` (JPA, package dormant) cùng mô tả một khái niệm; `Job`/`Candidate`/`DisclosureDocument` không được dùng ở đâu trong luồng đã đọc.
17. **Truy vấn có thể lỗi trên Postgres (nghi ngờ):** `UserAccessScopeRepository.findAccessManagementScopeOptions` dùng `select distinct ... order by lower(e.name)` (`J/repository/organization/UserAccessScopeRepository.java:15-27`); Postgres yêu cầu biểu thức ORDER BY nằm trong danh sách SELECT khi dùng DISTINCT. Chưa xác minh thực tế (có thể Hibernate/DTO constructor xử lý khác).
18. Hai lớp `CrmWorkspaceService`/`CrmSchoolService`/`EducationUnitService` đều `@Deprecated(since 2026-08-08)` nhưng vẫn là cài đặt chính (facade chỉ ủy quyền); `EducationUnitService` tự `new OrganizationEducationUnitQuery` nên bỏ qua proxy `@Cacheable` (`J/service/organization/EducationUnitService.java:15-17`).
19. Demo seed cố định tenant `"erg"` và đặt `LocalDate`-năm 2026 cứng (`...DemoSeedService.java:29`).

## 10. Câu hỏi còn mở

- Ai/đoạn nào đặt deal sang `signed`/`lost`, set `signedAt`, `signedRevenue`, `lostAt`, `lostReason`? (Không thấy trong slice; có phải do service khác hay còn thiếu?)
- Khi `approveHandover`, trường được "kích hoạt" ra sao (`organization.school.activate`, `EducationUnit.activatedAt`, tạo `SchoolOperationalProfile`)? Quyền `organization.school.activate` được seed (V6) nhưng không thấy dùng trong slice.
- `isReady` "thiếu tối đa 1 mục" là quy tắc kinh doanh cố ý hay lỗi `>=size-1`?
- Quyền `crm.contact.manage`, `crm.activity.manage`, `crm.school.create` không được áp ở đâu; contact và referral hiện chỉ đọc. Có endpoint ghi sắp tới?
- Có ràng buộc ownership thực sự cho `/crm/seo/{seoId}/...` (SEO chỉ xem của mình, manager xem team) ở tầng nào?
- Có consumer cho 3 sự kiện connector (outbox/listener) trong module khác không?
- Khi nào bật adapter SIS/HRIS/SCIM thật, và `secret://` được resolve ở đâu (secret manager)?
- Bảng `jobs`/`candidates` có trong migration nào không (grep không thấy)? Kế hoạch đưa recruitment/public-disclosure/AI từ prototype lên persistence là gì (có liên quan `ERG-066`)?
- `EducationUnit` có `@Version` nhưng `SchoolCommandService` kiểm version trên profile chứ không trên unit: có chủ ý không?
