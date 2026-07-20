package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.PublishRequest;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.service.quiz.publish.QuizPublishService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizDemoBootstrapSeedService {

    private static final String TENANT_ID = "erg";
    private static final String SEED_USER_ID = "seed.quiz.demo";
    private static final int SCHEMA_VERSION = 1;

    private final QuizRepository quizRepository;
    private final QuizDraftRepository quizDraftRepository;
    private final QuizPublishService quizPublishService;

    public QuizDemoBootstrapSeedService(
            QuizRepository quizRepository,
            QuizDraftRepository quizDraftRepository,
            QuizPublishService quizPublishService
    ) {
        this.quizRepository = quizRepository;
        this.quizDraftRepository = quizDraftRepository;
        this.quizPublishService = quizPublishService;
    }

    @Transactional("postgresTransactionManager")
    public void seedQuizDemos() {
        seedDemo(
                "erg-training-demo",
                "ERG on tap ky nang so (Training)",
                "Che do luyen tap tung cau",
                "train",
                "training",
                false,
                false,
                true,
                "client-first",
                "v1-training-demo"
        );
        seedDemo(
                "erg-testing-demo",
                "ERG kiem tra ky nang so (Testing)",
                "Che do kiem tra day du",
                "test",
                "testing",
                true,
                true,
                false,
                "server-authoritative",
                "v1-testing-demo"
        );
    }

    private void seedDemo(
            String quizId,
            String title,
            String subtitle,
            String kind,
            String mode,
            boolean shuffleQuestions,
            boolean shuffleChoices,
            boolean revealFeedbackPerStep,
            String gradingMode,
            String versionLabel
    ) {
        Quiz quiz = quizRepository.findByIdAndTenantId(quizId, TENANT_ID).orElseGet(() -> {
            Quiz created = new Quiz();
            created.setId(quizId);
            created.setTenantId(TENANT_ID);
            created.setPortal("lcms");
            created.setOwnerUserId(SEED_USER_ID);
            created.setSchemaVersion(SCHEMA_VERSION);
            created.setIdempotencyKey("seed:" + quizId);
            return created;
        });
        quiz.setTitle(title);
        quiz.setSubtitle(subtitle);
        quiz.setKind(kind);
        quiz.setSubjectId("sub_ic3");
        quiz.setLevelId("lvl_ic3_gs6_l1");
        quiz.setCategoryId("top_ic3_safety");
        quiz.setStatus(quiz.getCurrentVersionId() == null ? "draft" : "published");
        quiz.setVersion(quiz.getVersion() == null ? 0 : quiz.getVersion());
        quiz.setQuestionIds(questionIds());
        quiz.setSettings(runtimeSettings(mode, shuffleQuestions, shuffleChoices, revealFeedbackPerStep));
        quiz.setResult(resultSettings());
        quiz.setTheme(theme());
        quiz.setThemeId("erg-default");
        Quiz savedQuiz = quizRepository.save(quiz);

        if (savedQuiz.getCurrentVersionId() != null) {
            return;
        }

        QuizDraft draft = new QuizDraft();
        draft.setQuizId(savedQuiz.getId());
        draft.setTenantId(TENANT_ID);
        draft.setSettings(authoringSettings(title, subtitle, mode, shuffleQuestions, shuffleChoices, revealFeedbackPerStep));
        draft.setPlayerTemplate(playerTemplate());
        draft.setTree(tree());
        draft.setSchemaVersion(SCHEMA_VERSION);
        draft.setDocVersion(1L);
        draft.setUpdatedBy(SEED_USER_ID);
        QuizDraft savedDraft = quizDraftRepository.save(draft);

        savedQuiz.setLatestDraftId(savedDraft.getId());
        quizRepository.save(savedQuiz);

        quizPublishService.publish(
                TENANT_ID,
                savedQuiz.getId(),
                SEED_USER_ID,
                "seed-publish:" + savedQuiz.getId() + ":" + versionLabel,
                new PublishRequest(savedDraft.getDocVersion(), gradingMode, versionLabel)
        );
    }

    private Map<String, Object> authoringSettings(
            String title,
            String subtitle,
            String mode,
            boolean shuffleQuestions,
            boolean shuffleChoices,
            boolean revealFeedbackPerStep
    ) {
        return map(
                "info", map(
                        "title", title,
                        "subtitle", subtitle,
                        "author", "ERG Teacher Hub",
                        "introduction", "Demo du lieu that cho FE tich hop quiz runtime."
                ),
                "runtime", runtimeSettings(mode, shuffleQuestions, shuffleChoices, revealFeedbackPerStep),
                "result", resultSettings(),
                "theme", theme()
        );
    }

    private Map<String, Object> runtimeSettings(
            String mode,
            boolean shuffleQuestions,
            boolean shuffleChoices,
            boolean revealFeedbackPerStep
    ) {
        return map(
                "mode", mode,
                "timeLimitMinutes", mode.equals("testing") ? 20 : 30,
                "passPercent", 80,
                "shuffleQuestions", shuffleQuestions,
                "shuffleChoices", shuffleChoices,
                "revealFeedbackPerStep", revealFeedbackPerStep
        );
    }

    private Map<String, Object> resultSettings() {
        return map(
                "passMessage", "Chuc mung, em da hoan thanh tot bai demo.",
                "failMessage", "Em da hoan thanh bai demo. Hay xem lai cac cau can sua.",
                "reviewButtonLabel", "XEM LAI BAI",
                "thankYouMessage", "Cam on em da hoan thanh bai lam.",
                "showReviewButton", true,
                "submitAllPrompt", "Em da tra loi het cau hoi. Em muon nop bai ngay khong?",
                "confirmSubmitPrompt", "Em chac chan muon nop bai va ket thuc luot lam nay chu?",
                "submitAllLabel", "NOP BAI",
                "returnToQuizLabel", "QUAY LAI",
                "confirmYesLabel", "CO",
                "confirmNoLabel", "KHONG"
        );
    }

    private Map<String, Object> theme() {
        return map(
                "pageBackground", "#f8fafc",
                "playerBackground", "#ffffff",
                "canvasBorder", "#000088",
                "headerBackground", "#000088",
                "headerText", "#ffffff",
                "accentStart", "#000088",
                "accentEnd", "#e82828",
                "optionText", "#172033",
                "optionSelectedBackground", "#eef2ff",
                "inputBackground", "#ffffff",
                "sidebarActiveBackground", "#eef2ff",
                "sidebarActiveText", "#000088"
        );
    }

    private Map<String, Object> playerTemplate() {
        return map(
                "layout", "classic",
                "showToolbar", true,
                "showPanel", true,
                "roundedCorners", true,
                "backgroundTone", "soft-blue",
                "playerSize", "standard",
                "accentColor", "#d12b2b",
                "highlightColor", "#ffb35c"
        );
    }

    private List<Map<String, Object>> tree() {
        return list(map(
                "groupId", "group-question-types",
                "title", "Demo tat ca loai cau hoi",
                "rule", "all",
                "randomCount", 0,
                "slides", list(
                        singleChoice(),
                        multipleResponse(),
                        trueFalse(),
                        shortAnswer(),
                        numeric(),
                        sequence(),
                        matching(),
                        fillBlank(),
                        selectFromLists(),
                        dragWords(),
                        hotspot(),
                        dragDrop(),
                        likert(),
                        essay()
                )
        ));
    }

    private Map<String, Object> singleChoice() {
        return question("q1-single-choice", "multiple-choice",
                "Ban khong the gui tap tin nao sau day qua mot nha cung cap email tieu chuan?",
                "choices", choices(
                        choice("q1-a", "Tap tin van ban thuan dai 400 trang.", true),
                        choice("q1-b", "Hinh GIF dong 1080p voi 30 khung hinh.", false),
                        choice("q1-c", "Video dai 1 phut dinh dang AVI 8K.", false),
                        choice("q1-d", "Podcast dai 5 phut dinh dang MP3.", false)
                ));
    }

    private Map<String, Object> multipleResponse() {
        return question("q2-multiple-response", "multiple-response",
                "Chon cac hanh dong giup bai nop truc tuyen ro rang va de cham hon.",
                "choices", choices(
                        choice("q2-a", "Dat ten file theo cu phap giao vien yeu cau.", true),
                        choice("q2-b", "Nen anh hoac video neu dung luong qua lon.", true),
                        choice("q2-c", "Kiem tra lai file truoc khi nop.", true),
                        choice("q2-d", "Tai mot file bat ky de hoan thanh cho nhanh.", false)
                ),
                "acceptPartial", true);
    }

    private Map<String, Object> trueFalse() {
        return question("q3-true-false", "true-false",
                "Bat xac thuc hai lop giup tai khoan hoc tap an toan hon.",
                "choices", choices(
                        choice("q3-true", "Dung", true),
                        choice("q3-false", "Sai", false)
                ));
    }

    private Map<String, Object> shortAnswer() {
        return question("q4-short-answer", "short-answer",
                "Ten dinh dang file thuong dung de nop bai doc duoc tren nhieu may la gi?",
                "correctAnswers", list("PDF", "file PDF", "tep PDF"),
                "textBlanks", list(map(
                        "id", "q4-answer",
                        "label", "Cau tra loi ngan",
                        "placeholder", "Vi du: PDF",
                        "correctAnswers", list("PDF", "file PDF", "tep PDF")
                )));
    }

    private Map<String, Object> numeric() {
        return question("q5-numeric", "numeric",
                "Mot tai lieu duoc phep nop toi da bao nhieu trang theo quy dinh mock?",
                "numericAnswer", map("correctValue", 400, "tolerance", 0, "unit", "trang"));
    }

    private Map<String, Object> sequence() {
        return question("q6-sequence", "sequence",
                "Sap xep quy trinh hoc tap online theo thu tu hop ly.",
                "sequenceItems", list(
                        item("q6-step-1", "Doc yeu cau bai hoc"),
                        item("q6-step-2", "Xem tai lieu hoac video huong dan"),
                        item("q6-step-3", "Lam bai luyen tap"),
                        item("q6-step-4", "Xem phan hoi va sua loi")
                ));
    }

    private Map<String, Object> matching() {
        return question("q7-matching", "matching",
                "Ghep cac buoc trong chu trinh quan ly du an voi mo ta cua no.",
                "matching", list(
                        match("q7-pair-1", "Giam sat", "Theo doi tien do va rui ro du an."),
                        match("q7-pair-2", "Khoi tao", "Quyet dinh co theo duoi du an hay khong."),
                        match("q7-pair-3", "Thuc hien", "Tao ra san pham du an."),
                        match("q7-pair-4", "Dong", "Ban giao va danh gia du an.")
                ));
    }

    private Map<String, Object> fillBlank() {
        return question("q8-fill-blank", "fill-in-the-blanks",
                "Dien tu con thieu de hoan thanh cau bao mat tai khoan.",
                "textBlanks", list(
                        map("id", "q8-blank-1", "label", "mat khau", "prefix", "Em nen dung", "suffix", "manh", "correctAnswers", list("mat khau")),
                        map("id", "q8-blank-2", "label", "hai lop", "prefix", "va bat xac thuc", "suffix", "khi co the.", "correctAnswers", list("hai lop", "2 lop"))
                ));
    }

    private Map<String, Object> selectFromLists() {
        return question("q9-select-from-lists", "select-from-lists",
                "Voi moi tinh huong, chon Co neu nen bao cao hoac Khong neu khong can.",
                "inlineBlanks", list(
                        map("id", "q9-blank-1", "statement", "Ban sao bai kiem tra bi dang len mang.", "options", list("yes", "no"), "correctOptionId", "yes"),
                        map("id", "q9-blank-2", "statement", "Anh chup vui khong chua thong tin rieng tu.", "options", list("yes", "no"), "correctOptionId", "no"),
                        map("id", "q9-blank-3", "statement", "De doa bao luc trong cau lac bo.", "options", list("yes", "no"), "correctOptionId", "yes")
                ));
    }

    private Map<String, Object> dragWords() {
        return question("q10-drag-words", "drag-the-words",
                "Bam tu trong ngan hang de dua vao dung cho trong.",
                "wordBank", list(
                        item("word-source", "nguon"),
                        item("word-update", "cap nhat"),
                        item("word-safe", "an toan")
                ),
                "wordSlots", list(
                        map("id", "slot-source", "label", "Khi tai tai lieu, hay kiem tra", "correctWordId", "word-source"),
                        map("id", "slot-update", "label", "va luon", "correctWordId", "word-update")
                ));
    }

    private Map<String, Object> hotspot() {
        return question("q11-hotspot", "hotspot",
                "Bam vao vung thong tin phien ban trong man hinh minh hoa.",
                "hotspotImage", map("url", "https://media.erg.edu.vn/quiz/demo/hotspot-version.svg", "width", 1200, "height", 675),
                "hotspotAreas", list(map(
                        "id", "q11-area-version",
                        "shape", "rect",
                        "x", 0.52,
                        "y", 0.42,
                        "width", 0.26,
                        "height", 0.22,
                        "correct", true
                )));
    }

    private Map<String, Object> dragDrop() {
        return question("q12-drag-drop", "drag-and-drop",
                "Phan loai cac mon vao nhom phu hop.",
                "dropTargets", list(
                        item("target-healthy", "Thuc pham tot"),
                        item("target-snack", "Do an vat")
                ),
                "dragDropItems", list(
                        map("id", "item-broccoli", "label", "Bong cai xanh", "correctTargetId", "target-healthy"),
                        map("id", "item-apple", "label", "Tao", "correctTargetId", "target-healthy"),
                        map("id", "item-cookie", "label", "Banh quy", "correctTargetId", "target-snack"),
                        map("id", "item-chips", "label", "Khoai tay chien", "correctTargetId", "target-snack")
                ));
    }

    private Map<String, Object> likert() {
        return question("q13-likert", "likert-scale",
                "Danh gia muc do dong y cua em sau bai hoc.",
                "likertRows", list(
                        item("likert-understand", "Em hieu noi dung bai hoc"),
                        item("likert-confident", "Em tu tin lam bai tap tuong tu"),
                        item("likert-practice", "Em muon luyen them dang nay")
                ),
                "likertScale", list(
                        map("id", "scale-1", "label", "Rat thap", "value", 1),
                        map("id", "scale-2", "label", "Thap", "value", 2),
                        map("id", "scale-3", "label", "Vua", "value", 3),
                        map("id", "scale-4", "label", "Tot", "value", 4),
                        map("id", "scale-5", "label", "Rat tot", "value", 5)
                ));
    }

    private Map<String, Object> essay() {
        return question("q14-essay", "essay",
                "Viet ngan cach em se giai thich chu de an toan tai khoan cho ban cung lop.",
                "essayRubric", list(
                        map("id", "rubric-example", "label", "Co vi du ro rang", "points", 4),
                        map("id", "rubric-explain", "label", "Giai thich de hieu", "points", 4),
                        map("id", "rubric-clean", "label", "Trinh bay sach", "points", 2)
                ));
    }

    private Map<String, Object> question(String id, String type, String title, Object... fields) {
        String bankQuestionId = bankQuestionId(id);
        Map<String, Object> slide = map(
                "slideId", id,
                "questionId", bankQuestionId,
                "bankQuestionId", bankQuestionId,
                "type", type,
                "title", title,
                "feedback", feedback()
        );
        for (int index = 0; index < fields.length; index += 2) {
            slide.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return slide;
    }

    private Map<String, Object> feedback() {
        return map(
                "correct", "Chinh xac.",
                "incorrect", "Chua chinh xac.",
                "partial", "Dung mot phan."
        );
    }

    private List<String> questionIds() {
        return list(
                "qb-sample-q01-single-choice", "qb-sample-q02-multiple-response",
                "qb-sample-q03-true-false", "qb-sample-q04-short-answer",
                "qb-sample-q05-numeric", "qb-sample-q06-sequence", "qb-sample-q07-matching",
                "qb-sample-q09-fill-blank", "qb-sample-q10-select-from-lists",
                "qb-sample-q11-drag-words", "qb-sample-q12-hotspot", "qb-sample-q13-drag-drop",
                "qb-sample-q14-likert", "qb-sample-q15-essay"
        );
    }

    private String bankQuestionId(String slideId) {
        return switch (slideId) {
            case "q1-single-choice" -> "qb-sample-q01-single-choice";
            case "q2-multiple-response" -> "qb-sample-q02-multiple-response";
            case "q3-true-false" -> "qb-sample-q03-true-false";
            case "q4-short-answer" -> "qb-sample-q04-short-answer";
            case "q5-numeric" -> "qb-sample-q05-numeric";
            case "q6-sequence" -> "qb-sample-q06-sequence";
            case "q7-matching" -> "qb-sample-q07-matching";
            case "q8-fill-blank" -> "qb-sample-q09-fill-blank";
            case "q9-select-from-lists" -> "qb-sample-q10-select-from-lists";
            case "q10-drag-words" -> "qb-sample-q11-drag-words";
            case "q11-hotspot" -> "qb-sample-q12-hotspot";
            case "q12-drag-drop" -> "qb-sample-q13-drag-drop";
            case "q13-likert" -> "qb-sample-q14-likert";
            case "q14-essay" -> "qb-sample-q15-essay";
            default -> throw new IllegalArgumentException("Unknown quiz demo slide: " + slideId);
        };
    }

    private Map<String, Object> choice(String id, String label, boolean correct) {
        return map("id", id, "label", label, "correct", correct);
    }

    @SafeVarargs
    private final List<Map<String, Object>> choices(Map<String, Object>... choices) {
        return list(choices);
    }

    private Map<String, Object> item(String id, String label) {
        return map("id", id, "label", label);
    }

    private Map<String, Object> match(String id, String prompt, String response) {
        return map("id", id, "prompt", prompt, "response", response);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> list(T... items) {
        List<T> result = new ArrayList<>();
        for (T item : items) {
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return result;
    }
}
