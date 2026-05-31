package com.erg.ergspringboot.model.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.model.organization.EducationUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CrmSchoolModelMappingTest {

    @Test
    void educationUnitRemainsTheSingleSchoolMasterWithCrmOwnershipMetadata() throws NoSuchFieldException {
        assertThat(EducationUnit.class.getAnnotation(Table.class).name()).isEqualTo("education_units");
        assertField(EducationUnit.class, "ownerUserId", "owner_user_id", String.class);
        assertField(EducationUnit.class, "createdBy", "created_by", String.class);
        assertField(EducationUnit.class, "source", "source", String.class);
        assertField(EducationUnit.class, "signedAt", "signed_at", OffsetDateTime.class);
        assertField(EducationUnit.class, "activatedAt", "activated_at", OffsetDateTime.class);
    }

    @Test
    void crmSchoolTablesAttachToSchoolIdInsteadOfDuplicatingSchool() throws NoSuchFieldException {
        assertEntity(CrmSchoolProfile.class, "crm_school_profiles");
        assertEntity(CrmSchoolContact.class, "crm_school_contacts");
        assertEntity(CrmSchoolDeal.class, "crm_school_deals");
        assertEntity(CrmSchoolActivity.class, "crm_school_activities");
        assertEntity(CrmReferral.class, "crm_referrals");

        assertField(CrmSchoolProfile.class, "schoolId", "school_id", String.class);
        assertField(CrmSchoolContact.class, "schoolId", "school_id", String.class);
        assertField(CrmSchoolDeal.class, "schoolId", "school_id", String.class);
        assertField(CrmSchoolActivity.class, "schoolId", "school_id", String.class);
        assertField(CrmReferral.class, "schoolId", "school_id", String.class);

        assertField(CrmSchoolDeal.class, "ownerUserId", "owner_user_id", String.class);
        assertField(CrmSchoolDeal.class, "signedRevenue", "signed_revenue", BigDecimal.class);
        assertField(CrmSchoolDeal.class, "lostReason", "lost_reason", String.class);
    }

    private static void assertEntity(Class<?> modelClass, String tableName) {
        assertThat(modelClass).hasAnnotation(Entity.class);
        assertThat(modelClass.getAnnotation(Table.class).name()).isEqualTo(tableName);
    }

    private static void assertField(Class<?> modelClass, String fieldName, String columnName, Class<?> type) throws NoSuchFieldException {
        Field field = modelClass.getDeclaredField(fieldName);
        assertThat(field.getType()).isEqualTo(type);
        assertThat(field.getAnnotation(Column.class).name()).isEqualTo(columnName);
    }
}
