package com.erg.ergspringboot.lms.internal.seed;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Demo catalogue mirrored from erg-system mock-question-bank.ts; seeded by {@link QuestionBankDemoBootstrapSeedService}. */
final class QuestionBankDemoSeedContent {

    static final String SAMPLE_EDITOR_QUIZ_ID = "quiz-sample-editor-question-types";
    static final List<String> LEGACY_SUBJECT_IDS = List.of("mathematics", "english", "science");

    static final List<SubjectSeed> SUBJECTS = List.of(
        subject("ic3-gs6", "IC3 GS6", "ic3-gs6"),
        subject("python", "Python", "python"),
        subject("mos", "MOS", "mos"),
        subject("scratch", "Scratch", "scratch"),
        subject("tin-hoc-tre", "Tin hoc tre", "tin-hoc-tre")
    );

    static final List<LevelSeed> LEVELS = List.of(
        level("ic3-l1", "ic3-gs6", "Level 1", "ic3-l1", 1),
        level("ic3-l2", "ic3-gs6", "Level 2", "ic3-l2", 2),
        level("ic3-l3", "ic3-gs6", "Level 3", "ic3-l3", 3),
        level("python-foundation", "python", "Foundation", "python-foundation", 1),
        level("python-intermediate", "python", "Intermediate", "python-intermediate", 2),
        level("mos-word", "mos", "Word Associate", "mos-word", 1),
        level("mos-excel", "mos", "Excel Associate", "mos-excel", 2),
        level("mos-powerpoint", "mos", "PowerPoint Associate", "mos-powerpoint", 3),
        level("scratch-intro", "scratch", "Thieu nhi", "scratch-intro", 1),
        level("tht-bang-a", "tin-hoc-tre", "Doi tuyen", "tht-bang-a", 1)
    );

    static final List<TopicSeed> TOPICS = List.of(
        topics("ic3-l1", List.of(
                "Thiet bi va he dieu hanh", "Quan ly tep", "Go phim va nhap lieu", "Internet an toan",
                "Email co ban", "Word can ban", "On tap level 1"
        )),
        topics("ic3-l2", List.of(
                "Word nang cao", "Excel nhap mon", "Cong thuc Excel", "PowerPoint",
                "Lam viec nhom online", "Tim kiem thong tin", "On tap level 2"
        )),
        topics("ic3-l3", List.of(
                "Bao mat tai khoan", "Du lieu va bieu do", "Tu duy thuat toan", "Ban quyen so",
                "Thuyet trinh du an", "Kiem tra mo phong IC3", "On tap level 3"
        )),
        topics("python-foundation", List.of("Bien va kieu du lieu", "Dieu kien", "Vong lap", "Ham co ban", "On tap Python foundation")),
        topics("python-intermediate", List.of("List va dictionary", "Xu ly chuoi", "Doc ghi file", "Debug va test", "Mini project")),
        topics("mos-word", List.of("Dinh dang van ban", "Bang va hinh anh", "Tham chieu va mail merge", "On tap Word")),
        topics("mos-excel", List.of("Cong thuc co ban", "Bang du lieu", "Bieu do", "On tap Excel")),
        topics("mos-powerpoint", List.of("Bo cuc slide", "Transition va animation", "Trinh chieu", "On tap PowerPoint")),
        topics("scratch-intro", List.of("Lam quen khoi lenh", "Chuyen dong va am thanh", "Su kien va dieu khien", "Du an me cung")),
        topics("tht-bang-a", List.of("Tu duy thuat toan", "Day so va uoc so", "Hinh hoc va do hoa", "Mini contest"))
    );

    static final List<QuestionSeed> QUESTIONS = List.of(
        question("qb-ic3-001", "ic3-gs6", "ic3-l1", "ic3-l1-topic-1", "multiple-choice", "core", "ready",
                "Thiết bị nào sau đây dùng để nhập dữ liệu chữ và số vào máy tính?", "Bàn phím (Keyboard)", 62, 18, 3),
        question("qb-ic3-002", "ic3-gs6", "ic3-l1", "ic3-l1-topic-1", "multiple-choice", "stretch", "ready",
                "Cổng kết nối nào truyền tải đồng thời cả tín hiệu hình ảnh và âm thanh kỹ thuật số chất lượng cao?", "Cổng HDMI", 69, 23, 4),
        question("qb-ic3-003", "ic3-gs6", "ic3-l1", "ic3-l1-topic-2", "multiple-choice", "core", "ready",
                "Phím tắt nào dùng để sao chép (Copy) tệp hoặc thư mục trong File Explorer?", "Ctrl + C", 76, 28, 5),
        question("qb-ic3-004", "ic3-gs6", "ic3-l1", "ic3-l1-topic-2", "multiple-choice", "stretch", "ready",
                "Đuôi mở rộng .docx cho biết đây là định dạng tệp của ứng dụng nào?", "Microsoft Word", 83, 33, 6),
        question("qb-ic3-005", "ic3-gs6", "ic3-l1", "ic3-l1-topic-4", "multiple-choice", "core", "ready",
                "Khi lướt web, biểu tượng ổ khóa cạnh thanh địa chỉ (HTTPS) biểu thị điều gì?", "Kết nối đã được mã hóa an toàn", 90, 38, 7),
        question("qb-ic3-006", "ic3-gs6", "ic3-l1", "ic3-l1-topic-4", "multiple-choice", "challenge", "ready",
                "Hành vi nào giúp bảo vệ thông tin cá nhân tốt nhất khi sử dụng mạng Wi-Fi công cộng?", "Không đăng nhập tài khoản ngân hàng và sử dụng VPN", 64, 43, 8),
        question("qb-ic3-007", "ic3-gs6", "ic3-l1", "ic3-l1-topic-6", "multiple-choice", "core", "ready",
                "Để in đậm đoạn văn bản đã chọn trong Word, em nhấn tổ hợp phím nào?", "Ctrl + B", 71, 48, 9),
        question("qb-ic3-008", "ic3-gs6", "ic3-l2", "ic3-l2-topic-1", "multiple-choice", "stretch", "ready",
                "Trong Word, mục lục tự động thường dựa vào thành phần nào?", "Heading styles", 78, 53, 3),
        question("qb-ic3-009", "ic3-gs6", "ic3-l2", "ic3-l2-topic-2", "multiple-choice", "core", "ready",
                "Trong Excel, ô A1 chứa 10 và A2 chứa 15. Công thức nào tính tổng hai ô?", "=SUM(A1:A2)", 85, 58, 4),
        question("qb-ic3-010", "ic3-gs6", "ic3-l2", "ic3-l2-topic-3", "multiple-choice", "core", "ready",
                "Trong Excel, công thức nào tính trung bình cộng của các ô từ B2 đến B10?", "=AVERAGE(B2:B10)", 92, 63, 5),
        question("qb-ic3-011", "ic3-gs6", "ic3-l2", "ic3-l2-topic-3", "multiple-choice", "stretch", "ready",
                "Khi sao chép công thức sang ô khác, tham chiếu tuyệt đối $A$1 sẽ thay đổi như thế nào?", "Giữ nguyên không thay đổi", 66, 68, 6),
        question("qb-ic3-012", "ic3-gs6", "ic3-l2", "ic3-l2-topic-4", "multiple-choice", "core", "ready",
                "Chức năng Transition trong PowerPoint dùng để thiết lập hiệu ứng cho đối tượng nào?", "Chuyển đổi giữa các trang slide", 73, 73, 7),
        question("qb-ic3-013", "ic3-gs6", "ic3-l2", "ic3-l2-topic-5", "multiple-choice", "stretch", "ready",
                "Khi cùng chỉnh sửa tài liệu trên Google Docs, tính năng nào giúp trao đổi ý kiến mà không sửa trực tiếp vào bài?", "Tính năng Bình luận (Comment)", 80, 78, 8),
        question("qb-ic3-014", "ic3-gs6", "ic3-l3", "ic3-l3-topic-1", "multiple-choice", "core", "ready",
                "Xác thực 2 yếu tố (2FA) bảo vệ tài khoản bằng cách nào?", "Yêu cầu mật khẩu và mã OTP gửi qua điện thoại", 87, 83, 9),
        question("qb-ic3-015", "ic3-gs6", "ic3-l3", "ic3-l3-topic-2", "multiple-choice", "stretch", "ready",
                "Dạng biểu đồ nào thích hợp nhất để thể hiện tỉ lệ phần trăm cơ cấu học sinh của các khối lớp?", "Biểu đồ tròn (Pie chart)", 94, 88, 3),
        question("qb-ic3-016", "ic3-gs6", "ic3-l3", "ic3-l3-topic-3", "multiple-choice", "challenge", "ready",
                "Thứ tự chuẩn để giải quyết một bài toán lập trình máy tính là gì?", "Xác định bài toán → Thiết kế thuật toán → Viết mã → Kiểm thử", 68, 93, 4),
        question("qb-ic3-017", "ic3-gs6", "ic3-l3", "ic3-l3-topic-6", "multiple-choice", "challenge", "reviewing",
                "Một bài kiểm tra mô phỏng IC3 nên ưu tiên điều gì?", "Đo đúng năng lực theo từng mục tiêu kỹ năng", 75, 98, 5),
        question("qb-python-001", "python", "python-foundation", "python-foundation-topic-1", "multiple-choice", "core", "ready",
                "Kieu du lieu nao phu hop de luu gia tri dung/sai trong Python?", "bool", 82, 22, 5),
        question("qb-python-002", "python", "python-foundation", "python-foundation-topic-2", "multiple-choice", "core", "ready",
                "Cau lenh nao dung de kiem tra dieu kien trong Python?", "if", 76, 28, 5),
        question("qb-python-003", "python", "python-intermediate", "python-intermediate-topic-1", "multiple-choice", "stretch", "ready",
                "Nhung cau truc nao co the luu nhieu gia tri trong Python?", "list; dictionary", 88, 23, 5),
        question("qb-python-004", "python", "python-foundation", "python-foundation-topic-3", "multiple-choice", "core", "ready",
                "Cú pháp vòng lặp for i in range(1, 10) sẽ lặp bao nhiêu lần?", "9 lần", 85, 22, 5),
        question("qb-python-005", "python", "python-foundation", "python-foundation-topic-4", "multiple-choice", "core", "ready",
                "Từ khóa nào trong Python dùng để định nghĩa một hàm mới?", "def", 90, 25, 5),
        question("qb-mos-001", "mos", "mos-word", "mos-word-topic-1", "multiple-choice", "core", "ready",
                "Trong Microsoft Word, cong cu nao dung de ap dung nhanh bo dinh dang tieu de?", "Styles", 91, 26, 5),
        question("qb-mos-002", "mos", "mos-excel", "mos-excel-topic-1", "multiple-choice", "core", "ready",
                "Trong Excel, cong thuc nao tinh tong vung A1 den A5?", "=SUM(A1:A5)", 84, 31, 6),
        question("qb-mos-003", "mos", "mos-powerpoint", "mos-powerpoint-topic-1", "multiple-choice", "stretch", "ready",
                "Trong PowerPoint, Slide Master giup lam gi?", "Quan ly bo cuc va dinh dang chung", 79, 19, 4),
        question("qb-mos-004", "mos", "mos-excel", "mos-excel-topic-3", "multiple-choice", "stretch", "ready",
                "Loại biểu đồ nào trong Excel thể hiện xu hướng thay đổi dữ liệu theo thời gian tốt nhất?", "Biểu đồ đường (Line chart)", 82, 34, 5),
        question("qb-scratch-001", "scratch", "scratch-intro", "scratch-intro-topic-1", "multiple-choice", "core", "ready",
                "Khối lệnh nào trong Scratch dùng để bắt đầu chạy chương trình khi bấm lá cờ xanh?", "Khối sự kiện Khi bấm cờ xanh", 95, 30, 5),
        question("qb-scratch-002", "scratch", "scratch-intro", "scratch-intro-topic-2", "multiple-choice", "core", "ready",
                "Để nhân vật di chuyển 10 bước về phía trước, ta sử dụng khối lệnh nào?", "Khối lệnh move 10 steps", 92, 27, 5),
        question("qb-scratch-003", "scratch", "scratch-intro", "scratch-intro-topic-4", "multiple-choice", "stretch", "ready",
                "Làm thế nào để phát hiện nhân vật chạm vào tường mê cung trong Scratch?", "Khối lệnh cảm biến Touching color", 80, 32, 5),
        question("qb-tht-001", "tin-hoc-tre", "tht-bang-a", "tht-bang-a-topic-1", "multiple-choice", "stretch", "ready",
                "Thuật toán tìm kiếm nhị phân yêu cầu dãy số đầu vào phải thỏa mãn điều kiện gì?", "Dãy số đã được sắp xếp tăng dần", 75, 26, 5),
        question("qb-tht-002", "tin-hoc-tre", "tht-bang-a", "tht-bang-a-topic-2", "multiple-choice", "core", "ready",
                "Số nguyên dương lớn hơn 1 chỉ có đúng hai ước số là 1 và chính nó được gọi là gì?", "Số nguyên tố", 88, 23, 5),
        question("qb-tht-003", "tin-hoc-tre", "tht-bang-a", "tht-bang-a-topic-4", "multiple-choice", "challenge", "ready",
                "Độ phức tạp thời gian O(log N) thường xuất hiện trong thuật toán nào?", "Tìm kiếm nhị phân", 70, 22, 5),
        question("qb-sample-q01-single-choice", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "single_choice", "core", "ready",
                "Bạn không thể gửi tập tin nào sau đây qua một nhà cung cấp email tiêu chuẩn?",
                "Tập tin văn bản thuần dài 400 trang.", 75, 0, 1),
        question("qb-sample-q02-multiple-response", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "multiple_response", "core", "ready",
                "Chọn các hành động giúp bài nộp trực tuyến rõ ràng và dễ chấm hơn.",
                "Đặt tên file theo cú pháp giáo viên yêu cầu; Nén ảnh hoặc video nếu dung lượng quá lớn; Kiểm tra lại file trước khi nộp.", 75, 0, 1),
        question("qb-sample-q03-true-false", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "true_false", "core", "ready",
                "Bật xác thực hai lớp giúp tài khoản học tập an toàn hơn.", "Đúng", 75, 0, 1),
        question("qb-sample-q04-short-answer", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "short_answer", "core", "ready",
                "Tên định dạng file thường dùng để nộp bài đọc được trên nhiều máy là gì?", "PDF", 75, 0, 1),
        question("qb-sample-q05-numeric", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "numeric", "core", "ready",
                "Một tài liệu được phép nộp tối đa bao nhiêu trang theo quy định mock?", "12", 75, 0, 1),
        question("qb-sample-q06-sequence", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "sequence", "core", "ready",
                "Sắp xếp quy trình học tập online theo thứ tự hợp lý.",
                "Đọc yêu cầu bài học -> Xem tài liệu hoặc video hướng dẫn -> Làm bài luyện tập -> Xem phản hồi và sửa lỗi", 75, 0, 1),
        question("qb-sample-q07-matching", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "matching", "core", "ready",
                "Ghép các bước trong chu trình quản lý dự án với mô tả của nó.", "Monitoring, Initiation, Execution, Close", 75, 0, 1),
        question("qb-sample-q08-matching-image", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "matching", "core", "ready",
                "Ghép tên từng đầu kết nối với đúng hình minh họa.", "USB-C, USB-A, HDMI, Lightning", 75, 0, 1),
        question("qb-sample-q09-fill-blank", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "fill_blank", "core", "ready",
                "Điền từ còn thiếu để hoàn thành câu bảo mật tài khoản.", "mật khẩu; xác thực", 75, 0, 1),
        question("qb-sample-q10-select-from-lists", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "select_from_lists", "core", "ready",
                "Với mỗi tình huống, chọn Có nếu nên báo cáo hoặc Không nếu không cần.", "Có/Không theo tình huống", 75, 0, 1),
        question("qb-sample-q11-drag-words", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "drag_words", "core", "ready",
                "Bấm từ trong ngân hàng để đưa vào đúng chỗ trống.", "ngân hàng từ", 75, 0, 1),
        question("qb-sample-q12-hotspot", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "hotspot", "core", "ready",
                "Bấm vào vùng thông tin phiên bản trong màn hình minh họa.", "vùng phiên bản", 75, 0, 1),
        question("qb-sample-q13-drag-drop", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "drag_drop", "core", "ready",
                "Phân loại các món vào nhóm phù hợp.", "thực phẩm tốt / đồ ăn vặt", 75, 0, 1),
        question("qb-sample-q14-likert", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "likert_scale", "core", "ready",
                "Đánh giá mức độ đồng ý của em sau bài học.", "khảo sát Likert", 75, 0, 1),
        question("qb-sample-q15-essay", "ic3-gs6", "ic3-l1", "ic3-l1-topic-7", "essay", "core", "ready",
                "Viết ngắn cách em sẽ giải thích chủ đề an toàn tài khoản cho bạn cùng lớp.", "tự luận", 75, 0, 1)
    );

    static final List<QuizBankItemSeed> QUIZ_BANK_ITEMS = List.of(
        quizBankItem("quiz-ic3-l1-foundation-train", "IC3 GS6 L1 - Train nen tang may tinh", "train", "ic3-gs6", "ic3-l1", "ready",
                List.of("qb-ic3-001", "qb-ic3-002", "qb-ic3-003", "qb-ic3-004")),
        quizBankItem("quiz-ic3-l2-excel-test", "IC3 GS6 L2 - Test Excel nhap mon", "test", "ic3-gs6", "ic3-l2", "ready",
                List.of("qb-ic3-006", "qb-ic3-007")),
        quizBankItem("quiz-ic3-l3-security-train", "IC3 GS6 L3 - Train bao mat tai khoan", "train", "ic3-gs6", "ic3-l3", "draft",
                List.of("qb-ic3-009", "qb-ic3-010", "qb-ic3-011")),
        quizBankItem("quiz-ic3-l3-mock-test", "IC3 GS6 L3 - Test mo phong cuoi level", "test", "ic3-gs6", "ic3-l3", "reviewing",
                List.of("qb-ic3-009", "qb-ic3-010", "qb-ic3-011", "qb-ic3-012")),
        quizBankItem("quiz-python-foundation-train", "Python Foundation - Train bien va dieu kien", "train", "python", "python-foundation", "ready",
                List.of("qb-python-001", "qb-python-002")),
        quizBankItem("quiz-python-intermediate-test", "Python Intermediate - Test cau truc du lieu", "test", "python", "python-intermediate", "ready",
                List.of("qb-python-003")),
        quizBankItem("quiz-mos-office-readiness", "MOS Office - Test san sang chung chi", "test", "mos", "mos-excel", "ready",
                List.of("qb-mos-001", "qb-mos-002", "qb-mos-003")),
        quizBankItem("quiz-sample-editor-question-types", "Đây là bài kiểm tra mẫu", "train", "ic3-gs6", "ic3-l1", "ready",
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
                ))
    );

    private QuestionBankDemoSeedContent() {
    }

    static Map<String, Object> sampleEditorDraftSettings() {
        return map(
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
        );
    }

    static Map<String, Object> sampleEditorPlayerTemplate() {
        return map("layout", "classic", "showToolbar", true, "showPanel", true);
    }

    static List<Map<String, Object>> sampleEditorTree() {
        return List.of(map(
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
        ));
    }

    private static Map<String, Object> sampleSlide(String id, String kind, String title) {
        return map(
                "id", id,
                "slideId", id,
                "questionId", id,
                "kind", kind,
                "title", title
        );
    }

    private static SubjectSeed subject(String id, String name, String code) {
        return new SubjectSeed(id, name, code);
    }

    private static LevelSeed level(String id, String subjectId, String name, String code, int order) {
        return new LevelSeed(id, subjectId, name, code, order);
    }

    private static TopicSeed topics(String levelId, List<String> labels) {
        return new TopicSeed(levelId, labels);
    }

    private static QuestionSeed question(
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
        return new QuestionSeed(id, subjectId, levelId, topicId, type, difficulty, status, stem, answer,
                masteryRate, usageCount, schoolsUsing);
    }

    private static QuizBankItemSeed quizBankItem(
            String id,
            String title,
            String kind,
            String subjectId,
            String levelId,
            String status,
            List<String> questionIds
    ) {
        return new QuizBankItemSeed(id, title, kind, subjectId, levelId, status, questionIds);
    }

    static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return result;
    }

    record SubjectSeed(String id, String name, String code) {
    }

    record LevelSeed(String id, String subjectId, String name, String code, int order) {
    }

    record TopicSeed(String levelId, List<String> labels) {
    }

    record QuestionSeed(
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
    }

    record QuizBankItemSeed(
            String id,
            String title,
            String kind,
            String subjectId,
            String levelId,
            String status,
            List<String> questionIds
    ) {
    }
}
