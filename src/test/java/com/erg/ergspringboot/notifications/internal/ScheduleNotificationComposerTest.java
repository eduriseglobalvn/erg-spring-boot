package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.notifications.internal.ScheduleNotificationComposer.Kind;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScheduleNotificationComposerTest {

    private static Map<String, NotificationCommand> byUser(Kind kind, String type) {
        List<NotificationCommand> list = ScheduleNotificationComposer.compose(
                kind, "tenant-1", "admin-1", "key", 7L, type, ScheduleNotificationFixtures.published());
        return list.stream().collect(Collectors.toMap(c -> c.userIds().get(0), c -> c));
    }

    @Test
    void oneNotificationPerPersonWithVietnameseTextAndSchoolName() {
        Map<String, NotificationCommand> sent = byUser(Kind.PUBLISHED, "SCHEDULE_PUBLISHED");
        assertThat(sent).containsOnlyKeys("t1", "t2");

        NotificationCommand main = sent.get("t1");
        assertThat(main.title()).isEqualTo("3 tiết mới ở THCS Trương Công Định");
        assertThat(main.bodyPreview()).isEqualTo("Từ 11/11 (Thứ 4, tiết 6–7) · được cấp quyền lớp 9A2, 9A4");
        assertThat(main.actionUrl()).isEqualTo("/teacher/calendar?date=2026-11-11");
        assertThat(main.idempotencyKey()).isEqualTo("key:t1:7");
        assertThat(main.data()).containsEntry("role", "MAIN").containsEntry("newPeriods", "3")
                .containsEntry("schoolShortName", "TCĐ").containsEntry("weekdays", "[3,4]")
                .containsEntry("periodRange", "3-7").containsEntry("fromDate", "2026-11-11")
                .containsEntry("classes", "[{\"id\":\"c2\",\"name\":\"9A2\"},{\"id\":\"c4\",\"name\":\"9A4\"}]")
                .doesNotContainKey("assistantPeriods");
    }

    @Test
    void assistantOnlyGetsAssistantWording() {
        NotificationCommand assistant = byUser(Kind.PUBLISHED, "SCHEDULE_PUBLISHED").get("t2");
        assertThat(assistant.title()).isEqualTo("2 tiết trợ giảng mới ở THCS Trương Công Định");
        assertThat(assistant.bodyPreview())
                .isEqualTo("Từ 11/11 (Thứ 4, tiết 6–7) · trợ giảng cho Nguyễn Văn An · được cấp quyền lớp 9A2");
        assertThat(assistant.data()).containsEntry("role", "ASSISTANT").containsEntry("assistantPeriods", "2");
    }

    @Test
    void cancelledUsesItsOwnWording() {
        NotificationCommand cancelled = byUser(Kind.CANCELLED, "SCHEDULE_CANCELLED").get("t1");
        assertThat(cancelled.eventType()).isEqualTo("SCHEDULE_CANCELLED");
        assertThat(cancelled.title()).isEqualTo("3 tiết bị hủy ở THCS Trương Công Định");
        assertThat(cancelled.data()).containsEntry("reason", "BATCH_CANCELLED").containsEntry("cancelledPeriods", "3");
    }
}
