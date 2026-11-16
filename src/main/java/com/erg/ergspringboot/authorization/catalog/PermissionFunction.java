package com.erg.ergspringboot.authorization.catalog;

public enum PermissionFunction {
    SCHOOL_CLASS("Trường & lớp"),
    STUDENT("Học sinh"),
    SCHEDULE("Lịch dạy"),
    ATTENDANCE_LOG("Điểm danh & sổ đầu bài"),
    GRADES_ASSIGNMENTS("Điểm & bài tập"),
    MATERIALS_EXAMS("Học liệu & đề thi"),
    CURRICULUM("Chương trình"),
    ELEARNING("E-learning"),
    TIMEKEEPING("Chấm công"),
    NOTIFICATIONS("Thông báo"),
    ACCOUNTS_STAFF("Tài khoản & nhân viên"),
    AUTHORIZATION("Phân quyền"),
    CRM("CRM"),
    AUDIT("Nhật ký"),
    WEBSITE_CONTENT("Website & nội dung"),
    SYSTEM("Hệ thống");

    private final String label;

    PermissionFunction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
