-- Help FAQ corrections found while testing the «Hướng dẫn» pages (BUG-U-002), and the student password rule:
-- a re-issued student password is the student's own login name.
update help_faqs set updated_at = now(), answer =
'1. Giáo viên phụ trách lớp cấp lại: vào «Lớp học», chọn lớp, chọn học sinh, bấm «Cấp lại mật khẩu».
2. Ở trang quản trị: vào «Quản lý trường học» → «Danh sách trường», mở trường, sang bước «Danh sách học sinh», bấm «Cấp lại mật khẩu» ở dòng của học sinh.
3. Sau khi cấp lại, mật khẩu của học sinh chính là tên đăng nhập của em đó.
4. Báo riêng cho học sinh, không gửi vào nhóm chung.'
where id = 'faq-admin-06';

update help_faqs set updated_at = now(), answer =
'1. Mở «Lịch trung tâm», bấm «Xếp lịch».
2. Xem buổi có cảnh báo trùng: trùng giáo viên, trùng phòng hoặc trùng lớp.
3. Đổi giờ hoặc đổi giáo viên cho một trong hai buổi.
4. Khi hết cảnh báo, bấm «Áp dụng & thông báo» ở trang «Xếp lịch».'
where id = 'faq-admin-07';

update help_faqs set updated_at = now(), answer =
'1. Ở thanh bên trái, bấm «Báo giảng».
2. Chọn tuần cần xem.
3. Danh sách cho thấy giáo viên đã và chưa báo giảng.'
where id = 'faq-admin-10';

update help_faqs set updated_at = now(), answer =
'1. Vào «Lớp học», chọn lớp của học sinh.
2. Chọn học sinh, bấm «Cấp lại mật khẩu».
3. Xác nhận ở hộp thoại «Cấp lại mật khẩu học sinh?».
4. Mật khẩu mới chính là tên đăng nhập của học sinh. Bấm «Sao chép» rồi báo riêng cho em đó.'
where id = 'faq-teacher-04';

update help_faqs set updated_at = now(), answer =
'1. Báo giáo viên phụ trách lớp.
2. Thầy cô sẽ cấp lại mật khẩu cho em. Mật khẩu mới chính là tên đăng nhập của em.
3. Đăng nhập lại: gõ tên đăng nhập vào cả hai ô.'
where id = 'faq-student-01';
