package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.CalendarCatalogSearchResponse.Item;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Paged text search over the calendar catalog: stays inside the tenant and the given schools. */
class CalendarCatalogSearchIT extends TeachingScheduleITBase {

    @Autowired CalendarReadPort calendar;

    @Test
    void findsEntriesByTextInsideTheGivenSchoolsOnly() {
        String tenant = id();
        String school = school(tenant, "THCS A");
        String otherSchool = school(tenant, "THCS B");
        classroom(tenant, school, "9A1");
        classroom(tenant, school, "9A2");
        classroom(tenant, school, "8B1");
        classroom(tenant, otherSchool, "9A9");
        classroom(id(), school, "9A0");
        room(tenant, school, "PM 01");
        subject(tenant, "Toán");
        instructor(tenant, "Lê Thanh Tuấn", school);
        instructor(tenant, "Phạm Hà My", otherSchool);

        CalendarCatalogSearchResponse firstPage = calendar.catalogSearch(tenant, "classes", List.of(school), "9a", 0, 1);
        assertThat(firstPage.items()).extracting(Item::label).containsExactly("9A1");
        assertThat(firstPage.hasMore()).isTrue();
        CalendarCatalogSearchResponse secondPage = calendar.catalogSearch(tenant, "classes", List.of(school), "9a", 1, 1);
        assertThat(secondPage.items()).extracting(Item::label).containsExactly("9A2");
        assertThat(secondPage.hasMore()).isFalse();

        assertThat(calendar.catalogSearch(tenant, "rooms", List.of(school), "pm", 0, 20).items())
                .extracting(Item::label).containsExactly("PM 01");
        assertThat(calendar.catalogSearch(tenant, "subjects", List.of(), "to", 0, 20).items())
                .extracting(Item::label).containsExactly("Toán");
        assertThat(calendar.catalogSearch(tenant, "teachers", List.of(school), "", 0, 20).items())
                .extracting(Item::label).containsExactly("Lê Thanh Tuấn");
        // A literal % must not act as a wildcard.
        assertThat(calendar.catalogSearch(tenant, "classes", List.of(school), "%", 0, 20).items()).isEmpty();
    }
}
