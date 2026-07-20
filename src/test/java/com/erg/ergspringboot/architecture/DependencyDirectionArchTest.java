package com.erg.ergspringboot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class DependencyDirectionArchTest {
    @Test
    void migratedCalendarRestAdapterDoesNotReachPersistence() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.erg.ergspringboot.controller.calendar");

        noClasses()
                .that().resideInAnyPackage("..controller.calendar..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..repository..", "..model..", "jakarta.persistence..")
                .check(classes);
    }
}
