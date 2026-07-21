package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOptionResponse;
import com.erg.ergspringboot.model.calendar.CalendarRoom;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/** Calendar reference/catalog queries isolated from workspace orchestration. */
@Service
public class CalendarCatalogQueryUseCase {
    private static final String ACTIVE = "ACTIVE";
    private final ObjectProvider<CalendarRoomRepository> rooms;
    private final CalendarRoomRepository compatibilityRooms;
    @Autowired public CalendarCatalogQueryUseCase(ObjectProvider<CalendarRoomRepository> rooms) { this.rooms = rooms; this.compatibilityRooms = null; }
    public CalendarCatalogQueryUseCase(CalendarRoomRepository rooms) { this.rooms = null; this.compatibilityRooms = rooms; }
    public List<CalendarRoomOptionResponse> activeRooms(String tenantId, String schoolId) {
        if (schoolId == null || schoolId.isBlank()) return List.of();
        CalendarRoomRepository repository = compatibilityRooms != null ? compatibilityRooms : rooms.getIfAvailable();
        if (repository == null) return List.of();
        return repository.findByTenantIdAndSchoolIdAndStatus(tenantId, schoolId, ACTIVE).stream()
                .map(this::response).toList();
    }
    private CalendarRoomOptionResponse response(CalendarRoom room) {
        return new CalendarRoomOptionResponse(room.getId(), room.getName(), room.getSchoolId());
    }
}
