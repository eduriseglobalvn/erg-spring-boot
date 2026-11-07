package com.erg.ergspringboot.timekeeping.internal.request;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.notifications.NotificationAdminOperations;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Request notifications through the public notification API, sent after the surrounding transaction commits so a
 * notification failure can never roll back a submit or an approval (and a rolled-back one sends nothing). The
 * outbox key {@code tk-req:{requestId}:{event}:{stepNo}} makes a retry a no-op.
 */
@Component
public class RequestNotifier {

    static final String REQUESTER_URL = "/teacher/timekeeping/requests?id=";
    static final String APPROVER_URL = "/teacher/timekeeping/approvals?id=";
    private static final Logger log = LoggerFactory.getLogger(RequestNotifier.class);

    private final NotificationAdminOperations notifications;
    private final TransactionTemplate ownTransaction;

    RequestNotifier(NotificationAdminOperations notifications, PlatformTransactionManager transactionManager) {
        this.notifications = notifications;
        // after commit the finished transaction's resources are still bound: the outbox write needs its own one
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** SUBMITTED (step 1) or STEP (a later step became active): the step's approvers. */
    void toApprovers(TkRequest r, String event, int stepNo, Collection<String> approverIds) {
        send(r, key(r, event, stepNo), event, "Đơn chờ duyệt",
                "Đơn " + r.getCode() + " (" + label(r.getType()) + ") đang chờ bạn duyệt.", APPROVER_URL, approverIds);
    }

    /** The requester asked again; {@code nonce} keeps each (24h-throttled) reminder distinct in the outbox. */
    void remind(TkRequest r, int stepNo, long nonce, Collection<String> approverIds) {
        send(r, key(r, "REMIND", stepNo) + ":" + nonce, "REMIND", "Nhắc duyệt đơn",
                "Đơn " + r.getCode() + " (" + label(r.getType()) + ") vẫn đang chờ bạn duyệt.", APPROVER_URL, approverIds);
    }

    void cancelled(TkRequest r, int stepNo, Collection<String> approverIds) {
        send(r, key(r, "CANCELLED", stepNo), "CANCELLED", "Đơn đã huỷ",
                "Đơn " + r.getCode() + " (" + label(r.getType()) + ") đã được người gửi huỷ.", APPROVER_URL, approverIds);
    }

    void approved(TkRequest r, int stepNo) {
        send(r, key(r, "APPROVED", stepNo), "APPROVED", "Đơn đã được duyệt",
                "Đơn " + r.getCode() + " (" + label(r.getType()) + ") đã được duyệt.", REQUESTER_URL, List.of(r.getUserId()));
    }

    void rejected(TkRequest r, int stepNo, String comment) {
        String body = "Đơn " + r.getCode() + " (" + label(r.getType()) + ") bị từ chối: " + comment;
        send(r, key(r, "REJECTED", stepNo), "REJECTED", "Đơn bị từ chối",
                body.length() > 500 ? body.substring(0, 499) + "…" : body, REQUESTER_URL, List.of(r.getUserId()));
    }

    static String label(RequestType type) {
        return switch (type) {
            case LEAVE -> "Nghỉ phép";
            case EXPLANATION -> "Giải trình chấm công";
            case WFH -> "Làm việc tại nhà";
            case BUSINESS -> "Công tác";
        };
    }

    private static String key(TkRequest r, String event, int stepNo) {
        return "tk-req:" + r.getId() + ":" + event + ":" + stepNo;
    }

    private void send(TkRequest r, String key, String event, String title, String body, String url,
            Collection<String> userIds) {
        if (userIds.isEmpty()) return;
        AdminSendRequest message = new AdminSendRequest(key, "TIMEKEEPING_REQUEST_" + event, "timekeeping_request",
                r.getId(), title, body, url + r.getId(), "normal", "lms", List.copyOf(userIds), null, Map.of());
        String tenantId = r.getTenantId();
        Runnable dispatch = () -> {
            try {
                ownTransaction.executeWithoutResult(status -> notifications.send(tenantId, "system", message));
            } catch (RuntimeException ex) {
                log.warn("Request notification {} failed for request {}: {}", event, r.getId(), ex.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }
}
