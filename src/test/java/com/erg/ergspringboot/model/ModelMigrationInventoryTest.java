package com.erg.ergspringboot.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ModelMigrationInventoryTest {

    private record ExpectedModel(String className, List<String> fields, boolean baseEntity) {
    }

    @Test
    void allErgGoModelsArePresentWithFields() {
        List<ExpectedModel> expectedModels = List.of(
                new ExpectedModel("com.erg.ergspringboot.model.authorization.Permission", List.of("id", "name", "groupName", "label", "description", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.authorization.PermissionGroup", List.of("id", "name", "label", "order", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.authorization.Role", List.of("id", "name", "description", "permissions", "isDefault", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.authorization.UserPermissionOverride", List.of("id", "userId", "user", "permission", "permissionRef", "grantType", "reason", "expiresAt", "createdBy", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.analytics.Event", List.of("id", "tenantId", "sessionId", "userId", "eventName", "eventType", "properties", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.analytics.FirebaseSyncEvent", List.of("id", "tenantId", "eventName", "platform", "userId", "deviceId", "sessionId", "params", "country", "city", "appInstanceId", "receivedAt", "processedAt", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.analytics.Visit", List.of("id", "tenantId", "userId", "sessionId", "pageUrl", "referrer", "ipHash", "userAgent", "deviceType", "browser", "oS", "country", "city", "region", "timezone", "entityType", "entityId", "durationSeconds", "pageViews", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.audit.AuditLog", List.of("id", "userId", "userEmail", "action", "resourceType", "resourceId", "changes", "metadata", "ipAddress", "userAgent", "tenantId", "timestamp"), false),
                new ExpectedModel("com.erg.ergspringboot.model.bot.BotConversation", List.of("id", "userId", "platform", "platformConvId", "state", "wizardStep", "wizardData", "context", "lastMessageId", "lastCommand", "permissionLevel", "metadata", "createdAt", "updatedAt", "expiresAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.bot.BotLinkedAccount", List.of("id", "platform", "platformUserId", "internalUserId", "displayName", "username", "avatarUrl", "linkCode", "verifiedAt", "linkedAt", "unlinkedAt", "metadata"), false),
                new ExpectedModel("com.erg.ergspringboot.model.bot.CommandEntry", List.of("name", "aliases", "description", "usage", "category", "permission", "hidden", "cooldown"), false),
                new ExpectedModel("com.erg.ergspringboot.model.bot.CommandRegistry", List.of("entries", "aliases", "byCategory"), false),
                new ExpectedModel("com.erg.ergspringboot.model.bot.PlatformUpdate", List.of("platform", "userId", "username", "displayName", "conversationId", "messageId", "command", "args", "rawText", "isCommand", "isCallback", "callbackData", "replyToId", "timestamp", "metadata"), false),
                new ExpectedModel("com.erg.ergspringboot.model.bot.WorkflowExecution", List.of("id", "workflowId", "convId", "userId", "status", "currentStep", "steps", "stepHistory", "data", "retryCount", "maxRetries", "error", "startedAt", "updatedAt", "completedAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.bot.WorkflowStep", List.of("name", "label", "type", "command", "handler", "prompt", "onSuccess", "onFail", "onTimeout", "onComplete", "timeoutSec", "metadata"), false),
                new ExpectedModel("com.erg.ergspringboot.model.bot.WorkflowStepLog", List.of("step", "status", "input", "output", "error", "duration", "executedAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.courses.Course", List.of("id", "tenantId", "title", "slug", "subdomain", "description", "thumbnailUrl", "instructorId", "status", "enrollmentCount", "ratingAvg", "schemaType", "schemaDataJson", "themeConfig", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.courses.CourseEnrollment", List.of("id", "courseId", "userId", "enrolledAt", "completedAt", "progressPercent"), false),
                new ExpectedModel("com.erg.ergspringboot.model.courses.CourseInstructor", List.of("id", "name", "bio", "avatarUrl", "title"), false),
                new ExpectedModel("com.erg.ergspringboot.model.courses.CourseLesson", List.of("id", "courseId", "order", "title", "content", "durationMinutes", "videoUrl"), false),
                new ExpectedModel("com.erg.ergspringboot.model.courses.CourseSyllabus", List.of("id", "courseId", "order", "title", "content"), false),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.ContentBlacklist", List.of("id", "type", "pattern", "reason", "blockedCount", "enabled", "createdBy", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.ContentFingerprint", List.of("id", "url", "simHash", "sHA256", "bucket", "crawlId", "crawledAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.CrawlHistory", List.of("id", "url", "feedId", "jobId", "status", "hTTPStatus", "responseSize", "durationMS", "errorCode", "errorMsg", "qualityScore", "step", "title", "description", "contentHash", "tags", "language", "crawledAt", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.CrawlJobPayload", List.of("url", "feedId", "jobId", "priority", "depth", "configId", "userId"), false),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.CrawlProgressEvent", List.of("jobId", "step", "total", "message", "done", "error"), false),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.DomainReputation", List.of("id", "domain", "blockCount", "successCount", "failCount", "avgDuration", "score", "lastSeenAt", "updatedAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.JobStatusResponse", List.of("jobId", "url", "status", "step", "message", "error"), false),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.RefreshFeedPayload", List.of("feedId", "force"), false),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.ReindexPayload", List.of("algorithm", "since", "batchSize"), false),
                new ExpectedModel("com.erg.ergspringboot.model.crawler.RSSFeed", List.of("id", "name", "url", "type", "category", "language", "frequency", "enabled", "lastFetchAt", "lastItemAt", "itemCount", "errorCount", "lastError", "eTag", "lastModified", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.documents.Document", List.of("id", "tenantId", "filename", "originalName", "mimeType", "size", "storageType", "r2Url", "driveId", "watermarkConfig", "status", "uploadedBy", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.documents.WatermarkConfig", List.of("text", "position", "opacity", "color", "fontSize", "perPage", "offsetX", "offsetY", "rotation"), false),
                new ExpectedModel("com.erg.ergspringboot.model.elearning.ElearningCategory", List.of("id", "tenantId", "name", "slug", "description", "parentId", "order", "isActive", "levels", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.elearning.ElearningLevel", List.of("id", "tenantId", "categoryId", "name", "slug", "description", "order", "units", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.elearning.ElearningLevelRef", List.of("levelId", "name"), false),
                new ExpectedModel("com.erg.ergspringboot.model.elearning.ElearningUnit", List.of("id", "tenantId", "levelId", "name", "slug", "description", "content", "order", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.elearning.ElearningUnitRef", List.of("unitId", "name"), false),
                new ExpectedModel("com.erg.ergspringboot.model.hoclieu.AssetRecord", List.of("id", "resourceId", "title", "selectedFileType", "launchMode", "originalFileName", "detectedMimeType", "fileExtension", "fileSizeBytes", "storageProvider", "storageUrl", "canDownload", "status", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.hoclieu.ResourceItemRecord", List.of("id", "tenantId", "resourceId", "assetId", "unitTitle", "lessonTitle", "sortOrder", "pageCount", "durationSec", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.hoclieu.ResourceRecord", List.of("id", "tenantId", "programSlug", "subjectId", "gradeId", "categoryId", "sectionId", "bookSeriesId", "topicId", "levelId", "documentTypeId", "title", "slug", "subtitle", "description", "thumbnailUrl", "tags", "metadata", "visibility", "status", "publishedAt", "lectureDesign", "createdBy", "updatedBy", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.hoclieu.TaxonomyNode", List.of("id", "tenantId", "kind", "label", "slug", "parentId", "subjectId", "gradeId", "categoryId", "bookSeriesId", "levelIds", "depth", "sortOrder", "status", "description", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Announcement", List.of("id", "tenantId", "targetType", "academicClassIds", "studentIds", "title", "content", "pinned", "authorId", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Assignment", List.of("id", "tenantId", "academicClassId", "classroom", "quizId", "subjectId", "studentIds", "dueAt", "teacherNote", "status", "assignedBy", "recipientMode", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Attempt", List.of("id", "tenantId", "assignmentId", "quizId", "studentId", "packageId", "packageHash", "quizVersion", "status", "answers", "events", "client", "score", "maxScore", "percent", "passed", "startedAt", "submittedAt", "updatedAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.AttemptAnswer", List.of("questionId", "answer", "clientResult", "answeredAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Classroom", List.of("id", "tenantId", "schoolId", "name", "grade", "academicYear", "status", "homeroomTeacherId", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.ContentScope", List.of("type", "schoolId"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.CurrentScope", List.of("id", "tenantId", "userId", "level", "educationUnitId", "academicClassId", "updatedAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.DiscussionAttachment", List.of("id", "tenantId", "url", "mime", "size", "ownerId", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.DiscussionReply", List.of("id", "tenantId", "threadId", "academicClassId", "content", "attachmentIds", "authorId", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.DiscussionThread", List.of("id", "tenantId", "academicClassId", "title", "content", "assignmentId", "attachmentIds", "authorId", "replyCount", "latestActivityAt", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.ImportCredential", List.of("rowId", "rowNumber", "studentId", "username", "password"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.ImportJob", List.of("id", "tenantId", "userId", "previewId", "spreadsheetId", "sheetName", "sheetRange", "status", "progress", "created", "skipped", "duplicates", "credentials", "errors", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.ImportPreview", List.of("id", "tenantId", "userId", "spreadsheetId", "sheetName", "range", "rows", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.InternalDocument", List.of("id", "tenantId", "type", "title", "subjectId", "fileId", "content", "authorId", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Level", List.of("id", "tenantId", "subjectId", "name", "code", "order", "status", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.ParsedStudentRow", List.of("rowId", "rowNumber", "studentCode", "fullName", "className", "email", "gender", "birthday", "phone", "address", "parentName", "parentPhone", "parentEmail", "parentRelationship", "enrollmentDate", "note", "status", "messages"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Question", List.of("id", "tenantId", "scope", "subjectId", "levelId", "topicId", "type", "stem", "choices", "answer", "metadata", "status", "archivedAt", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.QuestionChoice", List.of("id", "label", "correct"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Quiz", List.of("id", "tenantId", "scope", "title", "kind", "subjectId", "levelId", "topicIds", "questionIds", "slides", "settings", "result", "theme", "themeId", "status", "version", "packageHash", "publishedAt", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Student", List.of("id", "tenantId", "schoolId", "academicClassId", "studentCode", "fullName", "username", "authUserId", "email", "gender", "birthday", "phone", "address", "parentName", "parentPhone", "parentEmail", "parentRelationship", "enrollmentDate", "note", "status", "metrics", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.StudentMetrics", List.of("averageScore", "completedAssignments", "lastActivityAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Subject", List.of("id", "tenantId", "scope", "name", "code", "status", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.lms.Topic", List.of("id", "tenantId", "levelId", "name", "code", "order", "status", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.notifications.DeliveryLog", List.of("id", "notificationId", "provider", "attempt", "status", "hTTPStatus", "responseBody", "errorMsg", "durationMS", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.notifications.Digest", List.of("id", "userId", "channel", "recipient", "frequency", "subject", "items", "count", "status", "sentAt", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.notifications.Notification", List.of("id", "userId", "userIdText", "type", "channel", "recipient", "title", "subject", "message", "body", "hTMLBody", "template", "data", "metadata", "status", "priority", "retryCount", "maxRetries", "provider", "errorMsg", "read", "digested", "digestId", "readAt", "legacyReadAt", "actionUrl", "groupKey", "source", "scheduledAt", "sentAt", "deliveredAt", "expiresAt", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.notifications.NotificationPreference", List.of("id", "userId", "email", "discord", "telegram", "whatsApp", "slack", "webhooks", "digestFreq", "digestTime", "language", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.notifications.WebhookPref", List.of("name", "url", "enabled"), false),
                new ExpectedModel("com.erg.ergspringboot.model.auth.AuthLoginAttempt", List.of("id", "tenantId", "userId", "attemptedEmail", "attemptedEmailHash", "ipAddress", "countryCode", "countryName", "continentCode", "userAgent", "deviceId", "deviceName", "result", "reason", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.auth.AuthPin", List.of("id", "email", "code", "purpose", "expiresAt", "usedAt", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.auth.AuthSession", List.of("id", "userId", "user", "sessionId", "deviceId", "deviceName", "ipAddress", "userAgent", "refreshTokenHash", "tenantId", "lastActiveAt", "expiresAt", "revokedAt", "revokedReason", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.organization.EducationUnit", List.of("id", "name", "slug", "type", "parentId", "parent", "logoUrl", "description", "status", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.users.Certificate", List.of("id", "userId", "user", "name", "issuedBy", "issueDate", "expiryDate", "imageUrl", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.community.CommunityComment", List.of("id", "tenantId", "postId", "post", "parentId", "rootId", "authorId", "author", "content", "depth", "replyCount", "reactionCount", "createdAt", "updatedAt", "deletedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.community.CommunityFollow", List.of("id", "tenantId", "targetType", "targetId", "userId", "user", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.community.CommunityMedia", List.of("id", "postId", "post", "mediaType", "url", "storageKey", "thumbnailUrl", "originalName", "mimeType", "sizeBytes", "width", "height", "durationSec", "sortOrder", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.community.CommunityPost", List.of("id", "tenantId", "topicId", "topic", "authorId", "author", "title", "content", "postType", "status", "visibility", "tagsJson", "isPinned", "isLocked", "viewCount", "commentCount", "reactionCount", "shareCount", "lastActivityAt", "createdAt", "updatedAt", "deletedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.community.CommunityReaction", List.of("id", "tenantId", "targetType", "targetId", "userId", "user", "reaction", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.community.CommunityTopic", List.of("id", "tenantId", "slug", "name", "description", "groupName", "icon", "color", "sortOrder", "isFeatured", "threadCount", "postCount", "followerCount", "lastPostId", "lastPostAt", "createdBy", "createdAt", "updatedAt", "deletedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.courses.CourseProgress", List.of("id", "userId", "courseId", "lessonId", "isCompleted", "progressPercent", "completedAt", "lastAccessedAt", "score", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.security.FirewallRule", List.of("id", "entry", "ruleType", "reason", "source", "active", "expiresAt", "revokedAt", "revokedBy", "createdBy", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.content.Page", List.of("id", "tenantId", "domain", "slug", "title", "content", "metaTitle", "metaDescription", "faqJson", "status", "publishedAt", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.content.Post", List.of("id", "title", "slug", "excerpt", "content", "metaJson", "thumbnailUrl", "status", "isPublished", "publishedAt", "createdById", "publishedById", "authorId", "viewCount", "commentCount", "categoryId", "category", "isCreatedByAi", "aiprompt", "aiJobId", "metaTitle", "metaDescription", "focusKeyword", "keywords", "canonicalUrl", "schemaType", "seoScore", "readabilityScore", "keywordDensity", "schemaMarkupJson", "schemaDataJson", "robotsIndex", "robotsFollow", "robotsAdvanced", "ogTitle", "ogDescription", "ogImage", "twitterCard", "breadcrumbTitle", "faqItemsJson", "howToStepsJson", "introVideoJson", "tagsJson", "createdAt", "updatedAt", "deletedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.content.PostCategory", List.of("id", "name", "slug", "description", "icon", "metaTitle", "metaDescription", "keywords", "isHidden", "hiddenType", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.users.Profile", List.of("id", "userId", "user", "fullName", "bio", "phone", "dateOfBirth", "gender", "address", "city", "district", "socialLinksJson", "avatarUrl", "isProfileCompleted", "teachingPhilosophy", "specialtiesJson", "rating", "internalNote", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.recruitment.RecruitmentCandidate", List.of("id", "jobId", "jobTitle", "tenantId", "fullName", "email", "phone", "cvUrl", "coverLetter", "note", "publicNote", "applyType", "status", "trackingCode", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.recruitment.RecruitmentJob", List.of("id", "tenantId", "slug", "title", "status", "isHot", "isNew", "isUrgent", "salary", "salaryMin", "salaryMax", "salaryCurrency", "quantity", "viewCount", "workType", "workSchedule", "postDate", "deadline", "deadlineDate", "location", "streetAddr", "city", "country", "employmentType", "summary", "descriptionJson", "requirementsJson", "benefitsJson", "isActive", "createdBy", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.users.SocialAccount", List.of("id", "userId", "user", "provider", "providerId", "email", "accessToken", "refreshToken", "expiresAt", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.configuration.SystemConfig", List.of("id", "key", "valueJson", "description", "updatedBy", "updatedAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.organization.UserAccessScope", List.of("id", "userId", "user", "educationUnitId", "educationUnit", "modules", "role", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.organization.WorkShift", List.of("id", "userId", "user", "schoolId", "room", "teachingSubject", "startTime", "endTime", "type", "status", "note", "remuneration", "confirmedBy", "actualStartTime", "actualEndTime", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.publicdisclosure.CoverConfig", List.of("eyebrow", "issuedBy", "title", "subtitle", "footer"), false),
                new ExpectedModel("com.erg.ergspringboot.model.publicdisclosure.DetailBlock", List.of("heading", "body"), false),
                new ExpectedModel("com.erg.ergspringboot.model.publicdisclosure.DisclosureDocument", List.of("id", "tenantId", "sectionSlug", "slug", "title", "menuLabel", "shortDescription", "description", "publishedAt", "effectiveDate", "referenceCode", "issuingAuthority", "reviewCycle", "accessScope", "heroKicker", "highlights", "detailBlocks", "cover", "documentId", "thumbnailUrl", "schoolYear", "watermarkConfig", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.recruitment.Candidate", List.of("id", "jobId", "jobTitle", "tenantId", "fullName", "email", "phone", "cvUrl", "coverLetter", "note", "publicNote", "applyType", "status", "trackingCode", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.recruitment.Job", List.of("id", "tenantId", "slug", "title", "status", "isHot", "isNew", "isUrgent", "salary", "salaryMin", "salaryMax", "salaryCurrency", "quantity", "viewCount", "workType", "workSchedule", "postDate", "deadline", "deadlineDate", "location", "streetAddr", "city", "country", "empType", "summary", "description", "requirements", "benefits", "isActive", "createdAt", "updatedAt", "createdBy"), true),
                new ExpectedModel("com.erg.ergspringboot.model.reviews.Review", List.of("id", "targetId", "targetType", "userId", "userName", "userEmail", "userAvatar", "rating", "comment", "isVerifiedPurchase", "status", "adminReply", "replyBy", "replyAt", "adminNote", "reviewedBy", "reviewedAt", "isFeatured", "helpfulCount", "ipAddress", "userAgent", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.seo.GSCData", List.of("id", "postId", "url", "clicks", "impressions", "cTR", "position", "date"), false),
                new ExpectedModel("com.erg.ergspringboot.model.seo.Seo404Log", List.of("id", "url", "referrer", "userAgent", "hitCount", "lastSeen", "firstSeen"), false),
                new ExpectedModel("com.erg.ergspringboot.model.seo.SeoConfig", List.of("key", "value", "updatedBy", "updatedAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.seo.SeoKeyword", List.of("id", "keyword", "targetUrl", "linkLimit", "isActive", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.seo.SeoRedirect", List.of("id", "fromPattern", "toUrl", "type", "isRegex", "isActive", "hitCount", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.sessions.Session", List.of("id", "tenantId", "userId", "sessionId", "ipAddress", "userAgent", "createdAt", "lastActive", "expiresAt", "isActive", "revokedAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.trending.NewsArticle", List.of("id", "topic", "headline", "source", "url", "publishedAt", "relevanceScore", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.trending.TrendingSnapshot", List.of("id", "topics", "topicCount", "generatedAt", "createdAt"), false),
                new ExpectedModel("com.erg.ergspringboot.model.trending.TrendingTopic", List.of("id", "topic", "slug", "score", "volume", "source", "keywords", "urls", "timeline", "lastRefreshedAt", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.users.SessionUser", List.of("id", "tenantId", "email", "fullName", "avatarUrl", "provider", "accountType", "lastLoginProvider", "status", "roles", "isProfileCompleted"), false),
                new ExpectedModel("com.erg.ergspringboot.model.users.User", List.of("id", "email", "passwordHash", "fullName", "avatarUrl", "status", "provider", "providerId", "accountType", "googleSub", "googleEmail", "googleEmailVerified", "lastLoginProvider", "roles", "tenantId", "phone", "bio", "gender", "dateOfBirth", "address", "city", "district", "jobTitle", "region", "socialLinks", "extendedProfile", "isProfileCompleted", "lastLoginAt", "loginCount", "createdAt", "updatedAt"), true),
                new ExpectedModel("com.erg.ergspringboot.model.users.UserActivity", List.of("id", "userId", "tenantId", "action", "targetUserId", "ipAddress", "userAgent", "description", "metadata", "createdAt"), false)
        );

        List<String> missing = new ArrayList<>();

        for (ExpectedModel expectedModel : expectedModels) {
            try {
                Class<?> modelClass = Class.forName(expectedModel.className());
                List<String> actualFields = fieldsIncludingInherited(modelClass);

                for (String field : expectedModel.fields()) {
                    if (!actualFields.contains(field)) {
                        missing.add(expectedModel.className() + "#" + field);
                    }
                }

                if (expectedModel.baseEntity()) {
                    assertThat(BaseEntity.class).isAssignableFrom(modelClass);
                    assertThat(declaredFieldNames(modelClass))
                            .doesNotContain("id", "createdAt", "updatedAt");
                }
            } catch (ClassNotFoundException exception) {
                missing.add(expectedModel.className());
            }
        }

        assertThat(missing).isEmpty();
    }

    @Test
    void userAccountModelLivesInUsersPackage() throws ClassNotFoundException {
        assertThat(Class.forName("com.erg.ergspringboot.model.users.User")).isNotNull();
        assertThat(Class.forName("com.erg.ergspringboot.model.users.SessionUser")).isNotNull();
        assertThat(classExists("com.erg.ergspringboot.model.auth.User")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.sessions.User")).isFalse();
    }

    @Test
    void authorizationModelUsesDomainNameAndDirectJoinTables() throws ClassNotFoundException {
        assertThat(Class.forName("com.erg.ergspringboot.model.authorization.Role")).isNotNull();
        assertThat(Class.forName("com.erg.ergspringboot.model.authorization.Permission")).isNotNull();
        assertThat(Class.forName("com.erg.ergspringboot.model.authorization.PermissionGroup")).isNotNull();
        assertThat(Class.forName("com.erg.ergspringboot.model.authorization.UserPermissionOverride")).isNotNull();

        assertThat(classExists("com.erg.ergspringboot.model.accesscontrol.Role")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.accesscontrol.Permission")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.core.AuthUser")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.core.ACRole")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.core.ACPermission")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.core.UserRole")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.core.RolePermission")).isFalse();
    }

    @Test
    void consolidatedModelPackagesDoNotKeepLegacyDuplicates() {
        assertThat(classExists("com.erg.ergspringboot.model.auth.LoginAttempt")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.auth.PinCode")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.auth.UserSession")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.lms.EducationUnit")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.operations.SystemConfig")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.pages.Page")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.posts.Post")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.profiles.Profile")).isFalse();

        assertThat(classExists("com.erg.ergspringboot.model.auth.AuthLoginAttempt")).isTrue();
        assertThat(classExists("com.erg.ergspringboot.model.auth.AuthPin")).isTrue();
        assertThat(classExists("com.erg.ergspringboot.model.auth.AuthSession")).isTrue();
        assertThat(classExists("com.erg.ergspringboot.model.organization.EducationUnit")).isTrue();
        assertThat(classExists("com.erg.ergspringboot.model.configuration.SystemConfig")).isTrue();
        assertThat(classExists("com.erg.ergspringboot.model.content.Page")).isTrue();
        assertThat(classExists("com.erg.ergspringboot.model.content.Post")).isTrue();
        assertThat(classExists("com.erg.ergspringboot.model.users.Profile")).isTrue();
    }

    private static List<String> fieldsIncludingInherited(Class<?> modelClass) {
        List<String> fields = new ArrayList<>();
        Class<?> current = modelClass;
        while (current != null && current != Object.class) {
            fields.addAll(declaredFieldNames(current));
            current = current.getSuperclass();
        }
        return fields;
    }

    private static List<String> declaredFieldNames(Class<?> modelClass) {
        return Arrays.stream(modelClass.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .toList();
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}

