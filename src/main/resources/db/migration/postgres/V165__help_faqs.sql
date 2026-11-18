-- Frequently asked questions shown on the «Hướng dẫn» page of each portal (GET /api/v1/help/faqs).
-- Global product help, the same for every tenant: no tenant_id and no row level security.
-- Edited by migration only; there is no admin screen for it yet.
create table if not exists help_faqs (
    id varchar(64) primary key,
    portal varchar(16) not null,
    category varchar(120) not null,
    question text not null,
    answer text not null,
    sort_order integer not null default 0,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_help_faqs_portal check (portal in ('ADMIN', 'TEACHER', 'STUDENT', 'CRM'))
);

create index if not exists idx_help_faqs_portal on help_faqs (portal, active, sort_order);

insert into help_faqs (id, portal, category, question, answer, sort_order) values
-- ADMIN (also used by Phòng đào tạo)
('faq-admin-01', 'ADMIN', 'Đăng nhập', 'Tôi quên mật khẩu thì làm sao?',
'1. Ở màn hình đăng nhập, bấm «Quên mật khẩu?» để xem hướng dẫn.
2. Liên hệ quản trị viên ERG để được cấp lại mật khẩu.
3. Đăng nhập lại bằng mật khẩu mới.', 10),
('faq-admin-02', 'ADMIN', 'Đăng nhập', 'Tôi nhập sai mật khẩu nhiều lần và không vào được.',
'1. Nhập sai mật khẩu 5 lần trong 15 phút thì tài khoản bị tạm khoá.
2. Chờ 15 phút rồi đăng nhập lại.
3. Nếu vẫn không được, nhờ quản trị viên ERG kiểm tra tài khoản.', 20),
('faq-admin-03', 'ADMIN', 'Đăng nhập', 'Tôi đăng nhập nhưng vào nhầm trang của giáo viên hoặc học sinh.',
'1. Mỗi tài khoản chỉ vào được trang đúng với vai trò của mình.
2. Hệ thống tự chuyển bạn về đúng trang sau khi đăng nhập.
3. Nếu vẫn sai trang, đăng xuất rồi đăng nhập lại.
4. Nếu vai trò bị gán sai, nhờ người quản lý sửa trong «Nhân sự» → «Tài khoản nhân sự».', 30),
('faq-admin-04', 'ADMIN', 'Học sinh', 'Tôi nhập danh sách học sinh nhưng báo lỗi cột.',
'1. Mở Google Sheet danh sách học sinh, kiểm tra có đủ cột họ tên và lớp.
2. Mỗi học sinh nằm trên một dòng, không gộp ô.
3. Ở bước «Tab Sheet cần đọc», chọn đúng tab và vùng ô có dữ liệu.
4. Xem «Bảng xem trước dữ liệu học sinh». Dòng «Cần kiểm tra» thì sửa ngay trên bảng.
5. Bấm «Xác nhận lưu» khi mọi dòng đã hợp lệ.', 40),
('faq-admin-05', 'ADMIN', 'Học sinh', 'Nhập lại cùng một danh sách có bị trùng học sinh không?',
'1. Không. Hệ thống nhận ra học sinh đã có và không tạo thêm.
2. Bạn vẫn nên xem bảng xem trước trước khi bấm «Xác nhận lưu».', 50),
('faq-admin-06', 'ADMIN', 'Học sinh', 'Học sinh quên mật khẩu thì ai cấp lại?',
'1. Giáo viên phụ trách lớp cấp lại: vào «Lớp học», chọn lớp, chọn học sinh, bấm «Cấp lại mật khẩu».
2. Ở trang quản trị, bạn cũng cấp lại được trong danh sách học sinh của trường.
3. Gửi tên đăng nhập và mật khẩu mới cho học sinh, không gửi vào nhóm chung.', 60),
('faq-admin-07', 'ADMIN', 'Lịch', 'Lịch báo trùng giờ thì xử lý thế nào?',
'1. Mở «Lịch trung tâm».
2. Xem buổi có cảnh báo trùng: trùng giáo viên, trùng phòng hoặc trùng lớp.
3. Đổi giờ hoặc đổi giáo viên cho một trong hai buổi.
4. Khi hết cảnh báo, xuất bản lịch.', 70),
('faq-admin-08', 'ADMIN', 'Nhân sự', 'Giáo viên mới không thấy lớp của mình.',
'1. Vào «Nhân sự» → «Tài khoản nhân sự», mở tài khoản giáo viên.
2. Kiểm tra giáo viên đã có vai trò giáo viên và đúng trường.
3. Kiểm tra lớp đã có giáo viên phụ trách là người này.
4. Nhờ giáo viên đăng xuất rồi đăng nhập lại.', 80),
('faq-admin-09', 'ADMIN', 'Nhân sự', 'Làm sao khoá tài khoản nhân sự đã nghỉ việc?',
'1. Vào «Nhân sự» → «Tài khoản nhân sự».
2. Tìm người cần khoá, bấm nút ba chấm ở cuối dòng.
3. Chọn «Khoá tài khoản». Người đó sẽ không đăng nhập được nữa.
4. Muốn mở lại, chọn «Mở khoá tài khoản».', 90),
('faq-admin-10', 'ADMIN', 'Báo giảng', 'Làm sao biết giáo viên nào chưa báo giảng?',
'1. Vào nhóm «Giảng dạy» → «Báo giảng».
2. Chọn tuần cần xem.
3. Danh sách cho thấy giáo viên đã và chưa báo giảng.', 100),
('faq-admin-11', 'ADMIN', 'Cài ứng dụng', 'Tôi cài trang quản trị lên máy tính được không?',
'1. Mở trang bằng Chrome hoặc Edge.
2. Bấm biểu tượng cài đặt trên thanh địa chỉ, hoặc mở menu ba chấm (⋮) → «Cài đặt trang web này thành ứng dụng».
3. Bấm «Cài đặt». Ứng dụng có biểu tượng riêng trên máy.', 110),
-- TEACHER
('faq-teacher-01', 'TEACHER', 'Đăng nhập', 'Tôi quên mật khẩu thì làm sao?',
'1. Ở màn hình đăng nhập, bấm «Quên mật khẩu?» để xem hướng dẫn.
2. Liên hệ quản trị viên ERG hoặc phòng đào tạo để được cấp lại.
3. Đăng nhập lại bằng mật khẩu mới.', 10),
('faq-teacher-02', 'TEACHER', 'Đăng nhập', 'Tôi nhập sai mật khẩu nhiều lần và không vào được.',
'1. Nhập sai mật khẩu 5 lần trong 15 phút thì tài khoản bị tạm khoá.
2. Chờ 15 phút rồi đăng nhập lại.
3. Nếu vẫn không được, báo quản trị viên ERG.', 20),
('faq-teacher-03', 'TEACHER', 'Lớp học', 'Tôi không thấy lớp mình dạy.',
'1. Kiểm tra đã chọn đúng trường ở đầu trang.
2. Vào «Lớp học» và tìm theo tên lớp.
3. Nếu vẫn không có, báo phòng đào tạo gán bạn làm giáo viên phụ trách lớp đó.
4. Sau khi được gán, đăng xuất rồi đăng nhập lại.', 30),
('faq-teacher-04', 'TEACHER', 'Học sinh', 'Học sinh quên mật khẩu thì tôi làm gì?',
'1. Vào «Lớp học», chọn lớp của học sinh.
2. Chọn học sinh, bấm «Cấp lại mật khẩu».
3. Xác nhận ở hộp thoại «Cấp lại mật khẩu học sinh?».
4. Bấm «Sao chép» rồi gửi riêng tên đăng nhập và mật khẩu mới cho học sinh.', 40),
('faq-teacher-05', 'TEACHER', 'Điểm danh', 'Tôi điểm danh sai thì sửa thế nào?',
'1. Mở lại buổi học trong «Điểm danh & KCT».
2. Chọn lại trạng thái đúng cho học sinh: có mặt, vắng có phép, vắng không phép hoặc muộn.
3. Lưu lại. Bản mới sẽ thay bản cũ.', 50),
('faq-teacher-06', 'TEACHER', 'Sổ đầu bài', 'Tôi quên ghi sổ đầu bài thì có ghi bù được không?',
'1. Mở «Sổ đầu bài».
2. Chọn tuần và buổi học đã qua.
3. Ghi nội dung bài dạy rồi lưu.
4. Nếu buổi đó đã bị khoá, báo phòng đào tạo.', 60),
('faq-teacher-07', 'TEACHER', 'Bài tập', 'Học sinh nói không thấy bài tập tôi đã giao.',
'1. Kiểm tra bài đã giao đúng lớp, đúng nhóm hoặc đúng học sinh.
2. Kiểm tra ngày bắt đầu: bài chưa tới ngày mở thì học sinh chưa thấy.
3. Nhờ học sinh tải lại trang hoặc mở mục «Bài tập».
4. Nếu học sinh mới vào lớp sau khi giao, hãy giao thêm cho học sinh đó.', 70),
('faq-teacher-08', 'TEACHER', 'Bài tập', 'Học sinh làm bài thì mất mạng, bài có mất không?',
'1. Không. Câu trả lời được giữ trên máy của học sinh.
2. Khi có mạng lại, học sinh mở lại bài và bấm «Làm tiếp».
3. Hết giờ thì bài tự nộp.', 80),
('faq-teacher-09', 'TEACHER', 'Chấm công', 'Tôi chấm công không được.',
'1. Bật định vị cho trình duyệt nếu trang hỏi.
2. Đứng trong khu vực trường rồi chấm lại.
3. Nếu vẫn không được, tạo đơn giải trình trong «Đơn từ».', 90),
('faq-teacher-10', 'TEACHER', 'Cài ứng dụng', 'Tôi cài ứng dụng lên iPhone không được.',
'1. Mở trang bằng Safari, không dùng Chrome hay Zalo.
2. Bấm nút Chia sẻ (hình vuông có mũi tên lên).
3. Chọn «Thêm vào Màn hình chính», rồi bấm «Thêm».
4. Mở ứng dụng từ biểu tượng mới trên màn hình.', 100),
-- STUDENT
('faq-student-01', 'STUDENT', 'Đăng nhập', 'Em quên mật khẩu thì làm sao?',
'1. Báo giáo viên phụ trách lớp.
2. Thầy cô sẽ cấp lại mật khẩu mới cho em.
3. Đăng nhập bằng mật khẩu mới.', 10),
('faq-student-02', 'STUDENT', 'Đăng nhập', 'Em nhập sai mật khẩu nhiều lần và không vào được.',
'1. Sai 5 lần trong 15 phút thì tài khoản bị khoá tạm.
2. Em chờ 15 phút rồi thử lại.
3. Nếu vẫn không vào được, báo thầy cô.', 20),
('faq-student-03', 'STUDENT', 'Đăng nhập', 'Em vào nhầm trang của giáo viên.',
'1. Em không cần làm gì cả.
2. Hệ thống tự đưa em về trang học sinh.
3. Nếu vẫn sai, em đăng xuất rồi đăng nhập lại.', 30),
('faq-student-04', 'STUDENT', 'Bài tập', 'Em không thấy bài tập.',
'1. Mở mục «Bài tập».
2. Kéo xuống xem hết danh sách.
3. Bài chưa tới ngày mở thì em chưa thấy.
4. Nếu bạn cùng lớp thấy mà em không thấy, báo thầy cô.', 40),
('faq-student-05', 'STUDENT', 'Bài tập', 'Đang làm bài thì mất mạng, bài có mất không?',
'1. Không mất. Câu trả lời được giữ trên máy.
2. Em cứ làm tiếp nếu còn câu.
3. Khi có mạng lại, em bấm «Nộp bài».
4. Nếu lỡ tắt trang, mở lại bài và bấm «Làm tiếp».', 50),
('faq-student-06', 'STUDENT', 'Bài tập', 'Hết giờ mà em chưa bấm nộp thì sao?',
'1. Bài sẽ tự nộp khi hết giờ.
2. Những câu em đã trả lời vẫn được tính.', 60),
('faq-student-07', 'STUDENT', 'Bài tập', 'Làm sao xem lại bài đã làm?',
'1. Mở mục «Bài tập».
2. Chọn bài đã nộp.
3. Bấm «Xem lại bài làm» để xem câu đúng, câu sai.', 70),
('faq-student-08', 'STUDENT', 'Tài khoản', 'Em muốn đổi mật khẩu.',
'1. Mở «Tài khoản & cài đặt» (trên điện thoại là mục «Hồ sơ»).
2. Bấm «Đổi mật khẩu».
3. Nhập mật khẩu hiện tại, rồi nhập mật khẩu mới hai lần.
4. Bấm «Đổi mật khẩu». Việc này không bắt buộc.', 80),
('faq-student-09', 'STUDENT', 'Cài ứng dụng', 'Em không cài được ứng dụng trên iPhone.',
'1. Mở trang học bằng Safari, không dùng Chrome hay Zalo.
2. Bấm nút Chia sẻ (hình vuông có mũi tên lên).
3. Chọn «Thêm vào Màn hình chính», rồi bấm «Thêm».
4. iPhone 7 trở lên đều làm được.', 90),
('faq-student-10', 'STUDENT', 'Lớp học', 'Em không thấy lớp của mình hoặc thấy sai lớp.',
'1. Kiểm tra tên lớp ở trang «Tổng quan».
2. Nếu sai lớp, báo thầy cô để chuyển lớp cho em.
3. Sau đó em đăng xuất rồi đăng nhập lại.', 100),
-- CRM
('faq-crm-01', 'CRM', 'Đăng nhập', 'Tôi quên mật khẩu thì làm sao?',
'1. Ở màn hình đăng nhập, bấm «Quên mật khẩu?» để xem hướng dẫn.
2. Liên hệ quản trị viên ERG để được cấp lại mật khẩu.', 10),
('faq-crm-02', 'CRM', 'Đăng nhập', 'Tôi nhập sai mật khẩu nhiều lần và không vào được.',
'1. Nhập sai mật khẩu 5 lần trong 15 phút thì tài khoản bị tạm khoá.
2. Chờ 15 phút rồi đăng nhập lại.', 20),
('faq-crm-03', 'CRM', 'Đăng nhập', 'Tôi không thấy mục CRM sau khi đăng nhập.',
'1. Tài khoản cần có quyền vào CRM.
2. Nhờ quản trị viên gán vai trò phù hợp trong «Vai trò & quyền».
3. Đăng xuất rồi đăng nhập lại.', 30),
('faq-crm-04', 'CRM', 'Trường', 'Thêm một trường mới vào danh sách tư vấn ở đâu?',
'1. Mở «Trường đã tư vấn».
2. Bấm nút thêm trường.
3. Nhập tên trường, người liên hệ và ghi chú.
4. Lưu lại.', 40),
('faq-crm-05', 'CRM', 'Opportunity', 'Làm sao chuyển giai đoạn cho một Opportunity?',
'1. Mở «Opportunity».
2. Chọn Opportunity cần cập nhật.
3. Đổi giai đoạn, rồi lưu.', 50),
('faq-crm-06', 'CRM', 'Follow-up', 'Làm sao không quên lịch gọi lại cho trường?',
'1. Mở «Lịch follow-up».
2. Thêm lịch hẹn cho trường, chọn ngày giờ.
3. Mỗi ngày mở mục này để xem việc cần làm.', 60),
('faq-crm-07', 'CRM', 'P&L', 'Bảng «P&L chốt trường» dùng để làm gì?',
'1. Bảng cho thấy doanh thu và chi phí dự kiến của từng trường đã chốt.
2. Kiểm tra số liệu trước khi chuyển sang «Bàn giao triển khai».', 70),
('faq-crm-08', 'CRM', 'Bàn giao', 'Trường đã chốt thì bàn giao cho bộ phận triển khai thế nào?',
'1. Mở «Bàn giao triển khai».
2. Chọn trường đã chốt.
3. Điền đủ thông tin bàn giao, rồi lưu.', 80),
('faq-crm-09', 'CRM', 'Cài ứng dụng', 'Tôi cài CRM lên máy tính được không?',
'1. Mở trang bằng Chrome hoặc Edge.
2. Mở menu ba chấm (⋮) → «Cài đặt trang web này thành ứng dụng».
3. Bấm «Cài đặt».', 90)
on conflict (id) do nothing;
