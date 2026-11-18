---
title: "Bài giáo viên tự tạo là quiz thật trên máy chủ, thuộc riêng giáo viên đó"
author: Vuong
date: 2026-11-17
branch: feat/role-consolidation
cost: { minutes: 43, tokens: 204298 }
id: l-2c5d948c
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/platform/security/TeacherClassScopeProvider.java
  - src/main/java/com/erg/ergspringboot/lms/internal/classroom/LmsTeacherClassScopeProvider.java
  - src/test/java/com/erg/ergspringboot/curriculum/CurriculumClassSubjectServiceTest.java
  - src/main/java/com/erg/ergspringboot/curriculum/persistence/CurriculumClassSubjectAllocationRepository.java
  - src/main/java/com/erg/ergspringboot/lms/internal/classlog/LessonLogCommandUseCase.java
  - src/main/resources/db/migration/postgres/V164__student_code_unique_per_school.sql
  - src/main/java/com/erg/ergspringboot/users/persistence/auth/AuthLoginAttemptRepository.java
  - src/main/java/com/erg/ergspringboot/curriculum/CurriculumClassSubjectService.java
  - src/main/java/com/erg/ergspringboot/curriculum/CurriculumTeachingPlanService.java
  - src/main/java/com/erg/ergspringboot/controller/curriculum/CurriculumController.java
---

## Decided
- Chủ dự án (17/11/2026): PĐT tạo bài cho cả hệ thống; giáo viên tự ghép bài từ câu hỏi của PĐT, bài theo tài khoản giáo viên và dùng được ở mọi trường họ dạy. Giáo viên khác không thấy. PĐT/admin thấy (có tên giáo viên) và xoá được. Xoá chỉ ẩn khỏi kho, bài đã giao vẫn chạy. Tài khoản giáo viên bị khoá/xoá thì bài của họ rời kho.
- Dùng lại đúng đường tạo → lưu nháp → xuất bản của PĐT, không dựng API riêng.

## Tried and ruled out
- Lưu bài trong bộ nhớ trình duyệt (cách cũ): không theo tài khoản, đổi máy là mất, người khác cùng máy thấy được.
- Thêm trường mới vào GraphQL: tránh vì phải đổi hợp đồng và danh mục route; dùng lại `scopeLabel` («Giáo viên») và `ownerLabel` (tên người tạo).
- Xoá bài bằng sự kiện khi khoá tài khoản: hạ tầng sự kiện liên module nặng; thay bằng lượt quét định kỳ 5 phút.

## Watch out
- «Giáo viên» ở đây = người có `lms.exercise.create` nhưng KHÔNG có `lms.quiz.publish`. Thêm quyền publish cho vai nào thì vai đó thành «PĐT» với tính năng này.
- FE lms không được import từ features/lcms (script ranh giới), nên giáo viên có API riêng gọi cùng endpoint, và phải truyền `portal: "lms"` vì đường dẫn bắt đầu bằng /lcms.
- Các truy vấn quizWorkspace/quizDraft/quizVersions vẫn chỉ lọc theo tenant, chưa lọc theo chủ bài.

In the code:
- BE: lcms/quiz/authoring/TeacherQuizOwnership.java (portal = "teacher"), LcmsCommandUseCase, LcmsReadModelUseCase, QuizRepository, LmsAssignmentCommandService.persistResources
- FE: src/features/lms/components/exercise-bank/teacher-exercise-api.ts, use-exercise-bank-controller.ts; src/features/question-bank/components/quiz-bank-panel.tsx
