package com.erg.ergspringboot.calendar.internal.room;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOption;
import com.erg.ergspringboot.model.calendar.CalendarRoom;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns transactional room catalog commands for the Calendar module. */
@Service
public class CalendarRoomCommandUseCase {

    private static final String ACTIVE = "ACTIVE";

    private final ObjectProvider<CalendarRoomRepository> roomProvider;
    private final CalendarRoomRepository compatibilityRooms;

    public CalendarRoomCommandUseCase(CalendarRoomRepository rooms) {
        this.roomProvider = null;
        this.compatibilityRooms = rooms;
    }

    @Autowired
    public CalendarRoomCommandUseCase(ObjectProvider<CalendarRoomRepository> roomProvider) {
        this.roomProvider = roomProvider;
        this.compatibilityRooms = null;
    }

    @Transactional("postgresTransactionManager")
    public CalendarRoomOption create(
            String tenantId, String schoolId, String roomName, String actorId) {
        String trimmedName = roomName == null ? null : roomName.trim();
        if (trimmedName == null || trimmedName.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank");
        }

        CalendarRoomRepository rooms = repository();
        Optional<CalendarRoom> existing = rooms.findBySchoolIdAndNameIgnoreCase(schoolId, trimmedName);
        if (existing.isPresent()) {
            CalendarRoom room = existing.get();
            return new CalendarRoomOption(room.getId(), room.getName(), room.getSchoolId());
        }

        CalendarRoom room = new CalendarRoom();
        room.setTenantId(tenantId);
        room.setSchoolId(schoolId);
        room.setName(trimmedName);
        room.setStatus(ACTIVE);
        room.setCreatedBy(actorId);
        CalendarRoom saved = rooms.save(room);
        return new CalendarRoomOption(saved.getId(), saved.getName(), saved.getSchoolId());
    }

    private CalendarRoomRepository repository() {
        if (compatibilityRooms != null) return compatibilityRooms;
        CalendarRoomRepository available = roomProvider == null ? null : roomProvider.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException("Calendar room repository is not available");
        }
        return available;
    }
}
