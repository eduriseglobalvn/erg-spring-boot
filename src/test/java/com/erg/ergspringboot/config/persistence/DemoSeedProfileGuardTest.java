package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lms.internal.seed.LmsBootstrapSeedInitializer;
import com.erg.ergspringboot.lms.internal.seed.QuestionBankDemoSeedInitializer;
import com.erg.ergspringboot.school.internal.seed.SchoolOperationsDemoSeedInitializer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/** P3-7: a stray LIFECYCLE__*_SEED_ON_STARTUP=true must not put demo data into a prod database. */
class DemoSeedProfileGuardTest {

    @Test
    void startupDemoSeedersNeverLoadUnderTheProdProfile() {
        for (Class<?> seeder : List.of(
                LmsBootstrapSeedInitializer.class,
                QuestionBankDemoSeedInitializer.class,
                SchoolOperationsDemoSeedInitializer.class)) {
            Profile profile = seeder.getAnnotation(Profile.class);
            assertThat(profile).as(seeder.getSimpleName()).isNotNull();
            assertThat(profile.value()).as(seeder.getSimpleName()).containsExactly("!prod");
        }
    }
}
