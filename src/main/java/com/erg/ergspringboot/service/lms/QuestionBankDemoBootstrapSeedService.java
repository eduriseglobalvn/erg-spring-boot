package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Question;
import com.erg.ergspringboot.model.lms.QuestionChoice;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.lms.Topic;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.lms.TopicRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.service.quiz.authoring.QuizQuestionLinkService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionBankDemoBootstrapSeedService {

    private static final String TENANT_ID = "erg";
    private static final String ACTIVE = "ACTIVE";
    private static final String SEED_USER_ID = "seed.question.bank";

    private final SubjectRepository subjectRepository;
    private final LevelRepository levelRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final QuizDraftRepository quizDraftRepository;
    private final QuizQuestionLinkService quizQuestionLinkService;

    public QuestionBankDemoBootstrapSeedService(
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            TopicRepository topicRepository,
            QuestionRepository questionRepository,
            QuizRepository quizRepository,
            QuizDraftRepository quizDraftRepository,
            QuizQuestionLinkService quizQuestionLinkService
    ) {
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.quizRepository = quizRepository;
        this.quizDraftRepository = quizDraftRepository;
        this.quizQuestionLinkService = quizQuestionLinkService;
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace", "quizRuntimePackage"}, allEntries = true)
    public void seedQuestionBankDemo() {
        seedSubjects();
        seedQuestions();
        seedQuizBankItems();
    }

    private void seedSubjects() {
        upsertSubject("ic3-gs6", "IC3 GS6", "ic3-gs6");
        upsertSubject("python", "Python", "python");
        upsertSubject("mos", "MOS", "mos");
        deactivateLegacyDemoSubjects();

        upsertLevel("ic3-l1", "ic3-gs6", "Level 1", "ic3-l1", 1);
        upsertLevel("ic3-l2", "ic3-gs6", "Level 2", "ic3-l2", 2);
        upsertLevel("ic3-l3", "ic3-gs6", "Level 3", "ic3-l3", 3);
        upsertLevel("python-foundation", "python", "Foundation", "python-foundation", 1);
        upsertLevel("python-intermediate", "python", "Intermediate", "python-intermediate", 2);
        upsertLevel("mos-word", "mos", "Word Associate", "mos-word", 1);
        upsertLevel("mos-excel", "mos", "Excel Associate", "mos-excel", 2);
        upsertLevel("mos-powerpoint", "mos", "PowerPoint Associate", "mos-powerpoint", 3);

        seedIc3Topics("ic3-l1", List.of(
                "Thiet bi va he dieu hanh", "Quan ly tep", "Go phim va nhap lieu", "Internet an toan",
                "Email co ban", "Word can ban", "On tap level 1"
        ));
        seedIc3Topics("ic3-l2", List.of(
                "Word nang cao", "Excel nhap mon", "Cong thuc Excel", "PowerPoint",
                "Lam viec nhom online", "Tim kiem thong tin", "On tap level 2"
        ));
        seedIc3Topics("ic3-l3", List.of(
                "Bao mat tai khoan", "Du lieu va bieu do", "Tu duy thuat toan", "Ban quyen so",
                "Thuyet trinh du an", "Kiem tra mo phong IC3", "On tap level 3"
        ));
        seedTopics("python-foundation", List.of("Bien va kieu du lieu", "Dieu kien", "Vong lap", "Ham co ban", "On tap Python foundation"));
        seedTopics("python-intermediate", List.of("List va dictionary", "Xu ly chuoi", "Doc ghi file", "Debug va test", "Mini project"));
        seedTopics("mos-word", List.of("Dinh dang van ban", "Bang va hinh anh", "Tham chieu va mail merge", "On tap Word"));
        seedTopics("mos-excel", List.of("Cong thuc co ban", "Bang du lieu", "Bieu do", "On tap Excel"));
        seedTopics("mos-powerpoint", List.of("Bo cuc slide", "Transition va animation", "Trinh chieu", "On tap PowerPoint"));
    }

    private void seedIc3Topics(String levelId, List<String> labels) {
        seedTopics(levelId, labels);
    }

    private void seedTopics(String levelId, List<String> labels) {
        for (int index = 0; index < labels.size(); index += 1) {
            upsertTopic(levelId + "-topic-" + (index + 1), levelId, labels.get(index), levelId + "-topic-" + (index + 1), index + 1);
        }
    }

    private void deactivateLegacyDemoSubjects() {
        List.of("mathematics", "english", "science").forEach(subjectId -> subjectRepository.findById(subjectId).ifPresent(subject -> {
            subject.setStatus("INACTIVE");
            subjectRepository.save(subject);
        }));
    }

    private void seedQuestions() {
        upsertQuestion("qb-ic3-001", "ic3-gs6", "ic3-l1", "ic3-l1-topic-1", "multiple-choice", "core", "ready",
                "Thiet bi nao dung de nhap du lieu chu vao may tinh?", "Ban phim", 62, 18, 3);
        upsertQuestion("qb-ic3-002", "ic3-gs6", "ic3-l1", "ic3-l1-topic-2", "multiple-choice", "core", "ready",
                "Khi muon doi ten mot tep, thao tac nao la phu hop nhat?", "Rename", 69, 23, 4);
        upsertQuestion("qb-ic3-003", "ic3-gs6", "ic3-l1", "ic3-l1-topic-4", "multiple-choice", "stretch", "ready",
                "Dau hieu nao cho thay mot website dang dung ket noi an toan?", "Bieu tuong o khoa va HTTPS", 76, 28, 5);
        upsertQuestion("qb-ic3-004", "ic3-gs6", "ic3-l1", "ic3-l1-topic-6", "multiple-choice", "core", "reviewing",
                "Trong Word, tinh nang nao giup can deu hai le doan van?", "Justify", 83, 33, 6);
        upsertQuestion("qb-ic3-005", "ic3-gs6", "ic3-l2", "ic3-l2-topic-1", "multiple-choice", "stretch", "ready",
                "Trong Word, muc luc tu dong thuong dua vao thanh phan nao?", "Heading styles", 90, 38, 7);
        upsertQuestion("qb-ic3-006", "ic3-gs6", "ic3-l2", "ic3-l2-topic-2", "multiple-choice", "core", "ready",
                "Trong Excel, o A1 chua 10 va A2 chua 15. Cong thuc nao tinh tong hai o?", "=SUM(A1:A2)", 64, 43, 8);
        upsertQuestion("qb-ic3-007", "ic3-gs6", "ic3-l2", "ic3-l2-topic-3", "multiple-choice", "core", "ready",
                "Ham nao dung de tim gia tri lon nhat trong mot vung du lieu Excel?", "MAX", 71, 48, 9);
        upsertQuestion("qb-ic3-008", "ic3-gs6", "ic3-l2", "ic3-l2-topic-5", "multiple-response", "stretch", "pilot",
                "Khi lam viec nhom online, hanh vi nao giup giam nham lan phien ban tai lieu?",
                "Dat ten phien ban ro rang; binh luan thay vi ghi de", 78, 53, 3);
        upsertQuestion("qb-ic3-009", "ic3-gs6", "ic3-l3", "ic3-l3-topic-1", "multiple-choice", "core", "ready",
                "Yeu to nao lam mat khau manh hon?", "Dai, co chu hoa, chu thuong, so va ky tu dac biet", 85, 58, 4);
        upsertQuestion("qb-ic3-010", "ic3-gs6", "ic3-l3", "ic3-l3-topic-2", "multiple-choice", "core", "ready",
                "Bieu do cot phu hop nhat khi can lam gi?", "So sanh cac nhom du lieu", 92, 63, 5);
        upsertQuestion("qb-ic3-011", "ic3-gs6", "ic3-l3", "ic3-l3-topic-3", "sequence", "challenge", "ready",
                "Sap xep cac buoc: xac dinh bai toan, viet thuat toan, chay thu, sua loi.",
                "Xac dinh bai toan -> viet thuat toan -> chay thu -> sua loi", 66, 68, 6);
        upsertQuestion("qb-ic3-012", "ic3-gs6", "ic3-l3", "ic3-l3-topic-6", "multiple-choice", "challenge", "reviewing",
                "Mot bai kiem tra mo phong IC3 nen uu tien dieu gi?",
                "Do dung nang luc theo tung muc tieu ky nang", 73, 73, 7);
        upsertQuestion("qb-python-001", "python", "python-foundation", "python-foundation-topic-1", "multiple-choice", "core", "ready",
                "Kieu du lieu nao phu hop de luu gia tri dung/sai trong Python?", "bool", 82, 22, 5);
        upsertQuestion("qb-python-002", "python", "python-foundation", "python-foundation-topic-2", "multiple-choice", "core", "ready",
                "Cau lenh nao dung de kiem tra dieu kien trong Python?", "if", 76, 28, 5);
        upsertQuestion("qb-python-003", "python", "python-intermediate", "python-intermediate-topic-1", "multiple-choice", "stretch", "ready",
                "Nhung cau truc nao co the luu nhieu gia tri trong Python?", "list; dictionary", 88, 23, 5);
        upsertQuestion("qb-mos-001", "mos", "mos-word", "mos-word-topic-1", "multiple-choice", "core", "ready",
                "Trong Microsoft Word, cong cu nao dung de ap dung nhanh bo dinh dang tieu de?", "Styles", 91, 26, 5);
        upsertQuestion("qb-mos-002", "mos", "mos-excel", "mos-excel-topic-1", "multiple-choice", "core", "ready",
                "Trong Excel, cong thuc nao tinh tong vung A1 den A5?", "=SUM(A1:A5)", 84, 31, 6);
        upsertQuestion("qb-mos-003", "mos", "mos-powerpoint", "mos-powerpoint-topic-1", "multiple-choice", "stretch", "ready",
                "Trong PowerPoint, Slide Master giup lam gi?", "Quan ly bo cuc va dinh dang chung", 79, 19, 4);
        seedEditorSampleQuestions();
    }

    private void seedEditorSampleQuestions() {
        String topicId = "ic3-l1-topic-7";
        upsertQuestion("qb-sample-q01-single-choice", "ic3-gs6", "ic3-l1", topicId, "single_choice", "core", "ready",
                "Bạn không thể gửi tập tin nào sau đây qua một nhà cung cấp email tiêu chuẩn?",
                "Tập tin văn bản thuần dài 400 trang.", 75, 0, 1);
        upsertQuestion("qb-sample-q02-multiple-response", "ic3-gs6", "ic3-l1", topicId, "multiple_response", "core", "ready",
                "Chọn các hành động giúp bài nộp trực tuyến rõ ràng và dễ chấm hơn.",
                "Đặt tên file theo cú pháp giáo viên yêu cầu; Nén ảnh hoặc video nếu dung lượng quá lớn; Kiểm tra lại file trước khi nộp.", 75, 0, 1);
        upsertQuestion("qb-sample-q03-true-false", "ic3-gs6", "ic3-l1", topicId, "true_false", "core", "ready",
                "Bật xác thực hai lớp giúp tài khoản học tập an toàn hơn.", "Đúng", 75, 0, 1);
        upsertQuestion("qb-sample-q04-short-answer", "ic3-gs6", "ic3-l1", topicId, "short_answer", "core", "ready",
                "Tên định dạng file thường dùng để nộp bài đọc được trên nhiều máy là gì?", "PDF", 75, 0, 1);
        upsertQuestion("qb-sample-q05-numeric", "ic3-gs6", "ic3-l1", topicId, "numeric", "core", "ready",
                "Một tài liệu được phép nộp tối đa bao nhiêu trang theo quy định mock?", "12", 75, 0, 1);
        upsertQuestion("qb-sample-q06-sequence", "ic3-gs6", "ic3-l1", topicId, "sequence", "core", "ready",
                "Sắp xếp quy trình học tập online theo thứ tự hợp lý.",
                "Đọc yêu cầu bài học -> Xem tài liệu hoặc video hướng dẫn -> Làm bài luyện tập -> Xem phản hồi và sửa lỗi", 75, 0, 1);
        upsertQuestion("qb-sample-q07-matching", "ic3-gs6", "ic3-l1", topicId, "matching", "core", "ready",
                "Ghép các bước trong chu trình quản lý dự án với mô tả của nó.", "Monitoring, Initiation, Execution, Close", 75, 0, 1);
        upsertQuestion("qb-sample-q08-matching-image", "ic3-gs6", "ic3-l1", topicId, "matching", "core", "ready",
                "Ghép tên từng đầu kết nối với đúng hình minh họa.", "USB-C, USB-A, HDMI, Lightning", 75, 0, 1);
        upsertQuestion("qb-sample-q09-fill-blank", "ic3-gs6", "ic3-l1", topicId, "fill_blank", "core", "ready",
                "Điền từ còn thiếu để hoàn thành câu bảo mật tài khoản.", "mật khẩu; xác thực", 75, 0, 1);
        upsertQuestion("qb-sample-q10-select-from-lists", "ic3-gs6", "ic3-l1", topicId, "select_from_lists", "core", "ready",
                "Với mỗi tình huống, chọn Có nếu nên báo cáo hoặc Không nếu không cần.", "Có/Không theo tình huống", 75, 0, 1);
        upsertQuestion("qb-sample-q11-drag-words", "ic3-gs6", "ic3-l1", topicId, "drag_words", "core", "ready",
                "Bấm từ trong ngân hàng để đưa vào đúng chỗ trống.", "ngân hàng từ", 75, 0, 1);
        upsertQuestion("qb-sample-q12-hotspot", "ic3-gs6", "ic3-l1", topicId, "hotspot", "core", "ready",
                "Bấm vào vùng thông tin phiên bản trong màn hình minh họa.", "vùng phiên bản", 75, 0, 1);
        upsertQuestion("qb-sample-q13-drag-drop", "ic3-gs6", "ic3-l1", topicId, "drag_drop", "core", "ready",
                "Phân loại các món vào nhóm phù hợp.", "thực phẩm tốt / đồ ăn vặt", 75, 0, 1);
        upsertQuestion("qb-sample-q14-likert", "ic3-gs6", "ic3-l1", topicId, "likert_scale", "core", "ready",
                "Đánh giá mức độ đồng ý của em sau bài học.", "khảo sát Likert", 75, 0, 1);
        upsertQuestion("qb-sample-q15-essay", "ic3-gs6", "ic3-l1", topicId, "essay", "core", "ready",
                "Viết ngắn cách em sẽ giải thích chủ đề an toàn tài khoản cho bạn cùng lớp.", "tự luận", 75, 0, 1);
    }

    private void seedQuizBankItems() {
        upsertQuizBankItem("quiz-ic3-l1-foundation-train", "IC3 GS6 L1 - Train nen tang may tinh", "train", "ic3-gs6", "ic3-l1", "ready",
                List.of("qb-ic3-001", "qb-ic3-002", "qb-ic3-003", "qb-ic3-004"));
        upsertQuizBankItem("quiz-ic3-l2-excel-test", "IC3 GS6 L2 - Test Excel nhap mon", "test", "ic3-gs6", "ic3-l2", "ready",
                List.of("qb-ic3-006", "qb-ic3-007"));
        upsertQuizBankItem("quiz-ic3-l3-security-train", "IC3 GS6 L3 - Train bao mat tai khoan", "train", "ic3-gs6", "ic3-l3", "draft",
                List.of("qb-ic3-009", "qb-ic3-010", "qb-ic3-011"));
        upsertQuizBankItem("quiz-ic3-l3-mock-test", "IC3 GS6 L3 - Test mo phong cuoi level", "test", "ic3-gs6", "ic3-l3", "reviewing",
                List.of("qb-ic3-009", "qb-ic3-010", "qb-ic3-011", "qb-ic3-012"));
        upsertQuizBankItem("quiz-python-foundation-train", "Python Foundation - Train bien va dieu kien", "train", "python", "python-foundation", "ready",
                List.of("qb-python-001", "qb-python-002"));
        upsertQuizBankItem("quiz-python-intermediate-test", "Python Intermediate - Test cau truc du lieu", "test", "python", "python-intermediate", "ready",
                List.of("qb-python-003"));
        upsertQuizBankItem("quiz-mos-office-readiness", "MOS Office - Test san sang chung chi", "test", "mos", "mos-excel", "ready",
                List.of("qb-mos-001", "qb-mos-002", "qb-mos-003"));
        upsertQuizBankItem("quiz-sample-editor-question-types", "Đây là bài kiểm tra mẫu", "train", "ic3-gs6", "ic3-l1", "ready",
                List.of(
                        "qb-sample-q01-single-choice",
                        "qb-sample-q02-multiple-response",
                        "qb-sample-q03-true-false",
                        "qb-sample-q04-short-answer",
                        "qb-sample-q05-numeric",
                        "qb-sample-q06-sequence",
                        "qb-sample-q07-matching",
                        "qb-sample-q08-matching-image",
                        "qb-sample-q09-fill-blank",
                        "qb-sample-q10-select-from-lists",
                        "qb-sample-q11-drag-words",
                        "qb-sample-q12-hotspot",
                        "qb-sample-q13-drag-drop",
                        "qb-sample-q14-likert",
                        "qb-sample-q15-essay"
                ));
        upsertSampleEditorDraft("quiz-sample-editor-question-types");
    }

    private void upsertSubject(String id, String name, String code) {
        Subject subject = subjectRepository.findById(id).orElseGet(() -> {
            Subject created = new Subject();
            created.setId(id);
            return created;
        });
        subject.setTenantId(TENANT_ID);
        subject.setName(name);
        subject.setCode(code);
        subject.setStatus(ACTIVE);
        subjectRepository.save(subject);
    }

    private void upsertLevel(String id, String subjectId, String name, String code, int order) {
        Level level = levelRepository.findById(id).orElseGet(() -> {
            Level created = new Level();
            created.setId(id);
            return created;
        });
        level.setTenantId(TENANT_ID);
        level.setSubjectId(subjectId);
        level.setName(name);
        level.setCode(code);
        level.setOrder(order);
        level.setStatus(ACTIVE);
        levelRepository.save(level);
    }

    private void upsertTopic(String id, String levelId, String name, String code, int order) {
        Topic topic = topicRepository.findById(id).orElseGet(() -> {
            Topic created = new Topic();
            created.setId(id);
            return created;
        });
        topic.setTenantId(TENANT_ID);
        topic.setLevelId(levelId);
        topic.setName(name);
        topic.setCode(code);
        topic.setOrder(order);
        topic.setStatus(ACTIVE);
        topicRepository.save(topic);
    }

    private void upsertQuestion(
            String id,
            String subjectId,
            String levelId,
            String topicId,
            String type,
            String difficulty,
            String status,
            String stem,
            String answer,
            double masteryRate,
            int usageCount,
            int schoolsUsing
    ) {
        Question question = questionRepository.findById(id).orElseGet(() -> {
            Question created = new Question();
            created.setId(id);
            return created;
        });
        question.setTenantId(TENANT_ID);
        question.setSubjectId(canonicalSubjectId(subjectId));
        question.setLevelId(canonicalLevelId(levelId));
        question.setTopicId(canonicalTopicId(topicId));
        question.setCategoryId(canonicalTopicId(topicId));
        question.setType(normalizeQuestionType(type));
        question.setStem(stem);
        question.setObjective(stem);
        question.setDifficulty(difficulty);
        question.setStatus(status);
        question.setTags(List.of(subjectId, levelId, topicId));
        question.setChoices(defaultChoices(answer));
        question.setAnswer(answer);
        question.setContent(map("stem", stem, "answer", answer, "source", "fe-mock-question-bank"));
        question.setMetadata(map("seededFrom", "erg-system mock-question-bank.ts"));
        question.setMasteryRate(masteryRate);
        question.setUsageCount(usageCount);
        question.setSchoolsUsing(schoolsUsing);
        question.setOwnerUserId(SEED_USER_ID);
        question.setSchemaVersion(1);
        questionRepository.save(question);
    }

    private void upsertQuizBankItem(
            String id,
            String title,
            String kind,
            String subjectId,
            String levelId,
            String status,
            List<String> questionIds
    ) {
        Quiz quiz = quizRepository.findById(id).orElseGet(() -> {
            Quiz created = new Quiz();
            created.setId(id);
            return created;
        });
        quiz.setTenantId(TENANT_ID);
        quiz.setPortal("lcms");
        quiz.setTitle(title);
        quiz.setKind(kind);
        quiz.setSubjectId(canonicalSubjectId(subjectId));
        quiz.setLevelId(canonicalLevelId(levelId));
        quiz.setCategoryId(questionIds.isEmpty() ? null : canonicalTopicId(topicIdForQuestion(questionIds.get(0))));
        quiz.setQuestionIds(questionIds);
        quiz.setStatus(status);
        quiz.setVersion(quiz.getVersion() == null ? 0 : quiz.getVersion());
        quiz.setOwnerUserId(SEED_USER_ID);
        quiz.setSchemaVersion(1);
        quiz.setSettings(map("sourceMode", "fe-mock-question-bank"));
        Quiz savedQuiz = quizRepository.save(quiz);
        savedQuiz.setQuestionIds(quizQuestionLinkService.syncManualQuestionIds(TENANT_ID, savedQuiz.getId(), questionIds));
        quizRepository.save(savedQuiz);
    }

    private void upsertSampleEditorDraft(String quizId) {
        QuizDraft draft = quizDraftRepository.findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc(quizId, TENANT_ID)
                .orElseGet(() -> {
                    QuizDraft created = new QuizDraft();
                    created.setQuizId(quizId);
                    created.setTenantId(TENANT_ID);
                    return created;
                });
        draft.setSettings(map(
                "info", map(
                        "title", "Đây là bài kiểm tra mẫu",
                        "subtitle", "IC3 GS6 - Level 1",
                        "showIntroductionPage", true,
                        "introduction", "Bộ câu hỏi mẫu từ ngân hàng câu hỏi."
                ),
                "settings", map(
                        "mode", "training",
                        "shuffleQuestions", false,
                        "shuffleChoices", false,
                        "revealFeedbackPerStep", true
                ),
                "result", map(
                        "passMessage", "Hoàn thành tốt bài luyện tập.",
                        "failMessage", "Hãy xem lại các câu cần cải thiện."
                )
        ));
        draft.setPlayerTemplate(map("layout", "classic", "showToolbar", true, "showPanel", true));
        draft.setTree(List.of(map(
                "id", "sample-editor-group",
                "title", "GS6 LV3 OTTHBS (Luyện tập)",
                "rule", "all",
                "randomCount", 0,
                "slides", List.of(
                        sampleSlide("qb-sample-q01-single-choice", "multiple-choice", "Bạn không thể gửi tập tin nào sau đây qua một nhà cung cấp email tiêu chuẩn?"),
                        sampleSlide("qb-sample-q02-multiple-response", "multiple-response", "Chọn các hành động giúp bài nộp trực tuyến rõ ràng và dễ chấm hơn."),
                        sampleSlide("qb-sample-q03-true-false", "true-false", "Bật xác thực hai lớp giúp tài khoản học tập an toàn hơn."),
                        sampleSlide("qb-sample-q04-short-answer", "short-answer", "Tên định dạng file thường dùng để nộp bài đọc được trên nhiều máy là gì?"),
                        sampleSlide("qb-sample-q05-numeric", "numeric", "Một tài liệu được phép nộp tối đa bao nhiêu trang theo quy định mock?"),
                        sampleSlide("qb-sample-q06-sequence", "sequence", "Sắp xếp quy trình học tập online theo thứ tự hợp lý."),
                        sampleSlide("qb-sample-q07-matching", "matching", "Ghép các bước trong chu trình quản lý dự án với mô tả của nó."),
                        sampleSlide("qb-sample-q08-matching-image", "matching", "Ghép tên từng đầu kết nối với đúng hình minh họa."),
                        sampleSlide("qb-sample-q09-fill-blank", "fill-blank", "Điền từ còn thiếu để hoàn thành câu bảo mật tài khoản."),
                        sampleSlide("qb-sample-q10-select-from-lists", "select-from-lists", "Với mỗi tình huống, chọn Có nếu nên báo cáo hoặc Không nếu không cần."),
                        sampleSlide("qb-sample-q11-drag-words", "drag-words", "Bấm từ trong ngân hàng để đưa vào đúng chỗ trống."),
                        sampleSlide("qb-sample-q12-hotspot", "hotspot", "Bấm vào vùng thông tin phiên bản trong màn hình minh họa."),
                        sampleSlide("qb-sample-q13-drag-drop", "drag-and-drop", "Phân loại các món vào nhóm phù hợp."),
                        sampleSlide("qb-sample-q14-likert", "likert-scale", "Đánh giá mức độ đồng ý của em sau bài học."),
                        sampleSlide("qb-sample-q15-essay", "essay", "Viết ngắn cách em sẽ giải thích chủ đề an toàn tài khoản cho bạn cùng lớp.")
                )
        )));
        draft.setSchemaVersion(1);
        draft.setDocVersion(draft.getDocVersion() == null ? 1L : draft.getDocVersion() + 1L);
        draft.setUpdatedBy(SEED_USER_ID);
        QuizDraft savedDraft = quizDraftRepository.save(draft);

        quizRepository.findByIdAndTenantId(quizId, TENANT_ID).ifPresent(quiz -> {
            quiz.setLatestDraftId(savedDraft.getId());
            quizRepository.save(quiz);
        });
    }

    private Map<String, Object> sampleSlide(String id, String kind, String title) {
        return map(
                "id", id,
                "slideId", id,
                "questionId", id,
                "kind", kind,
                "title", title
        );
    }

    private String topicIdForQuestion(String questionId) {
        return questionRepository.findById(questionId).map(Question::getTopicId).orElse(null);
    }

    private String canonicalSubjectId(String id) {
        return switch (id) {
            case "ic3-gs6" -> "sub_ic3";
            case "python" -> "sub_python";
            case "mos" -> "sub_mos";
            default -> id;
        };
    }

    private String canonicalLevelId(String id) {
        if (id != null && id.startsWith("ic3-l")) return "lvl_ic3_gs6_l1";
        return switch (id) {
            case "python-foundation" -> "lvl_python_foundation";
            case "python-intermediate" -> "lvl_python_intermediate";
            case "mos-word" -> "lvl_mos_word";
            case "mos-excel" -> "lvl_mos_excel";
            case "mos-powerpoint" -> "lvl_mos_powerpoint";
            default -> id;
        };
    }

    private String canonicalTopicId(String id) {
        if (id != null && id.startsWith("ic3-")) return "top_ic3_safety";
        if (id != null && id.startsWith("python-foundation")) return "top_python_foundation";
        if (id != null && id.startsWith("python-intermediate")) return "top_python_intermediate";
        if (id != null && id.startsWith("mos-word")) return "top_mos_word";
        if (id != null && id.startsWith("mos-excel")) return "top_mos_excel";
        if (id != null && id.startsWith("mos-powerpoint")) return "top_mos_powerpoint";
        return id;
    }

    private String normalizeQuestionType(String type) {
        if (type == null || type.isBlank()) {
            return "multiple_choice";
        }
        return type.trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replace('-', '_')
                .replace("fill_in_the_blanks", "fill_blank")
                .replace("drag_the_words", "drag_words")
                .replace("drag_and_drop", "drag_drop");
    }

    private List<QuestionChoice> defaultChoices(String answer) {
        return List.of(
                new QuestionChoice("a", answer, true),
                new QuestionChoice("b", "Phuong an gay nhieu 1", false),
                new QuestionChoice("c", "Phuong an gay nhieu 2", false),
                new QuestionChoice("d", "Phuong an gay nhieu 3", false)
        );
    }

    private Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return result;
    }
}
