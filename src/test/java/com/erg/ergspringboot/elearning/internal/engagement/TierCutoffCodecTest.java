package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierCutoffCell;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierCutoffsResponse;
import com.erg.ergspringboot.config.LmsCacheDescriptorContributor;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.JacksonCacheValueCodec;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The tierCutoffs cache value survives the codec and is scoped by school (key {tenantId, schoolId, grade, week}). */
class TierCutoffCodecTest {

    @Test
    @SuppressWarnings("unchecked")
    void cutoffCellsRoundTripThroughTheCacheCodec() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var descriptor = (CacheDescriptor<Object>) new LmsCacheDescriptorContributor().descriptors(mapper).stream()
                .filter(candidate -> candidate.name().equals("tierCutoffs")).findFirst().orElseThrow();
        var codec = new JacksonCacheValueCodec(mapper);
        var cutoffs = new TierCutoffsResponse(List.of(new TierCutoffCell("ct", null, 1, 3, 900), new TierCutoffCell("kc", 2, 4, 9, 500)));

        assertThat(codec.decode(descriptor, codec.encode(descriptor, cutoffs).bytes())).isEqualTo(cutoffs);
        assertThat(descriptor.scopeFor(List.of("t1", "school-1", "10", "latest"))).isEqualTo("school-1");
    }
}
