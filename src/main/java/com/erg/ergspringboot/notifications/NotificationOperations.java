package com.erg.ergspringboot.notifications;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.HeartbeatDeviceRequest;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.RegisterDeviceRequest;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.UnregisterDeviceRequest;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.PreferenceUpdateItemRequest;
import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.*;
import com.erg.ergspringboot.service.notifications.NotificationDeviceService;
import com.erg.ergspringboot.service.notifications.NotificationService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Public notification read, preference and device boundary. */
@Service
public class NotificationOperations {
    private final NotificationService notifications;
    private final NotificationDeviceService devices;

    public NotificationOperations(NotificationService notifications, NotificationDeviceService devices) {
        this.notifications = notifications;
        this.devices = devices;
    }

    public PageResponse<NotificationItemResponse> listInbox(String tenant, String user, String portal, String status, int page, int size) {
        return notifications.listInbox(tenant, user, portal, status, page, size);
    }
    public UnreadCountResponse unreadCount(String tenant, String user, String portal) { return notifications.unreadCount(tenant, user, portal); }
    public NotificationItemResponse detail(String tenant, String user, String portal, String id) { return notifications.detail(tenant, user, portal, id); }
    public MarkReadResponse markAllRead(String tenant, String user, String portal) { return notifications.markAllRead(tenant, user, portal); }
    public MarkReadResponse markRead(String tenant, String user, String portal, String id) { return notifications.markRead(tenant, user, portal, id); }
    public ArchiveResponse archive(String tenant, String user, String portal, String id) { return notifications.archive(tenant, user, portal, id); }
    public List<NotificationPreferenceResponse> preferences(String tenant, String user, String portal) { return notifications.preferences(tenant, user, portal); }
    public List<NotificationPreferenceResponse> updatePreferences(String tenant, String user, String portal, List<PreferenceUpdateItemRequest> preferences) {
        return notifications.updatePreferences(tenant, user, portal, preferences);
    }
    public DeviceRegistrationResponse registerDevice(String tenant, String user, String portal, RegisterDeviceRequest body) { return devices.register(tenant, user, portal, body); }
    public void unregisterDevice(String tenant, String user, String portal, UnregisterDeviceRequest body) { devices.unregister(tenant, user, portal, body); }
    public DeviceRegistrationResponse heartbeat(String tenant, String user, String portal, HeartbeatDeviceRequest body) { return devices.heartbeat(tenant, user, portal, body); }
    public void revokeDevice(String tenant, String user, String portal, String deviceId) { devices.revokeOwnDevice(tenant, user, portal, deviceId); }
}
