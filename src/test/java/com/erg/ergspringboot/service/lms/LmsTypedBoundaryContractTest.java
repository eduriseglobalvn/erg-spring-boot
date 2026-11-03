package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ClassesBySchoolResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.EducationUnitListResponse;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LmsTypedBoundaryContractTest {
    @Test
    void educationAndClassQueriesDoNotExposeMapPayloads() throws Exception {
        Method education = com.erg.ergspringboot.lms.LmsRuntimePort.class.getMethod("listEducationUnits",
                com.erg.ergspringboot.platform.security.UserAccessContext.class);
        Method classes = com.erg.ergspringboot.lms.LmsRuntimePort.class.getMethod("listClassesBySchool", String.class,
                com.erg.ergspringboot.platform.security.UserAccessContext.class);

        assertThat(education.getReturnType()).isEqualTo(EducationUnitListResponse.class);
        assertThat(classes.getReturnType()).isEqualTo(ClassesBySchoolResponse.class);
    }
}
