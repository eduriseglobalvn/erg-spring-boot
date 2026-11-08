package com.erg.ergspringboot.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CampaignAudienceTest {

    @Test
    void nullListsBecomeEmpty() {
        CampaignAudience audience = new CampaignAudience(false, null, null, null, null, null, null);

        assertThat(audience.schoolIds()).isEmpty();
        assertThat(audience.gradeIds()).isEmpty();
        assertThat(audience.classIds()).isEmpty();
        assertThat(audience.groupIds()).isEmpty();
        assertThat(audience.roles()).isEmpty();
        assertThat(audience.userIds()).isEmpty();
    }

    @Test
    void listsAreImmutableAndNullEntriesDropped() {
        CampaignAudience audience = new CampaignAudience(
                false, null, null, Arrays.asList("c1", null, " ", "c2"), null, null, null);

        assertThat(audience.classIds()).containsExactly("c1", "c2");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> audience.classIds().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void jsonWithMissingAndNullListsRoundTrips() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        CampaignAudience parsed = mapper.readValue(
                "{\"wholeTenant\":true,\"classIds\":null,\"userIds\":[\"u1\"]}", CampaignAudience.class);
        assertThat(parsed.wholeTenant()).isTrue();
        assertThat(parsed.classIds()).isEmpty();
        assertThat(parsed.roles()).isEmpty();
        assertThat(parsed.userIds()).isEqualTo(List.of("u1"));

        CampaignAudience again = mapper.readValue(mapper.writeValueAsString(parsed), CampaignAudience.class);
        assertThat(again).isEqualTo(parsed);
    }
}
