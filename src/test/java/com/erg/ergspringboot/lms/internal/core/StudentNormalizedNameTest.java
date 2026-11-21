package com.erg.ergspringboot.lms.internal.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lms.persistence.Student;
import org.junit.jupiter.api.Test;

class StudentNormalizedNameTest {

    @Test
    void doubleSpaceNbspAndDecomposedVietnameseMatchTheStoredForm() {
        String nfd = java.text.Normalizer.normalize("Nguyễn  Văn\u00A0An ", java.text.Normalizer.Form.NFD);
        assertThat(Student.normalizeName(nfd)).isEqualTo("nguyễn văn an").isEqualTo(Student.normalizeName("NGUYỄN VĂN AN"));
    }

    @Test
    void entitySyncsTheColumnFromFullName() throws Exception {
        Student student = new Student();
        student.setFullName("Lê  Bình");
        var sync = Student.class.getDeclaredMethod("syncNormalizedName");
        sync.setAccessible(true);
        sync.invoke(student);
        assertThat(student.getFullNameNormalized()).isEqualTo("lê bình");
    }
}
