package com.erg.ergspringboot.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapClass;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapResponse;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapScope;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapUnit;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SessionResponsesTest {

    @Test
    void sessionBootstrapCarriesManageableUnitsAndClassesForTeacherScope() {
        assertThat(recordComponentNames(SessionBootstrapResponse.class))
                .containsExactly("user", "permissions", "portals", "lifecycle", "currentScope", "manageableUnits", "manageableClasses");
        assertThat(recordComponentNames(SessionBootstrapScope.class))
                .containsExactly("level", "unitId", "classId");
        assertThat(recordComponentNames(SessionBootstrapUnit.class))
                .containsExactly("id", "name", "avatar", "type", "level");
        assertThat(recordComponentNames(SessionBootstrapClass.class))
                .containsExactly("id", "name", "unitId", "grade", "academicYear", "status");
    }

    private static String[] recordComponentNames(Class<? extends Record> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);
    }
}
