package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.JacksonCacheValueCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CacheDescriptorContributorTest {

    @Test
    void contributorsPreserveTheExistingCacheCatalogWithoutDuplicates() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var descriptors = List.of(new LmsCacheDescriptorContributor(), new PlatformCacheDescriptorContributor())
                .stream().flatMap(contributor -> contributor.descriptors(mapper).stream()).toList();
        Set<String> names = new HashSet<>(descriptors.stream().map(descriptor -> descriptor.name()).toList());

        assertThat(descriptors).hasSize(27);
        assertThat(names).hasSize(27);
        assertThat(names).contains("lmsStudentIdentity", "quizStartInfo");
        assertThat(names).contains("lmsClassWorkspace", "quizRuntimePackage", "calendarWorkspace", "curriculumTree",
                "studentTier", "studentFrames", "tierCutoffs", "notificationUnread");
        assertThat(names).doesNotContain("lmsSchoolClasses", "contentLibraryBootstrap", "contentStudioBootstrap",
                "contentResources", "contentResourceDetail");
    }

    @Test
    @SuppressWarnings("unchecked")
    void studentTierEncodesAndDecodesDespiteTheGraphQlProtectedGetter() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var descriptor = (CacheDescriptor<Object>) new LmsCacheDescriptorContributor().descriptors(mapper).stream()
                .filter(candidate -> candidate.name().equals("studentTier")).findFirst().orElseThrow();
        var codec = new JacksonCacheValueCodec(mapper);
        var tier = new com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyTierResponse(
                "bk", 3, 2, 20, 150, true, 4, true, null, null, null);

        assertThat(codec.decode(descriptor, codec.encode(descriptor, tier).bytes())).isEqualTo(tier);
    }

    @Test
    void noCacheDeclaresAnOptionalValueBecauseSpringStoresTheUnwrappedValue() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var optionalCaches = List.of(new LmsCacheDescriptorContributor(), new PlatformCacheDescriptorContributor())
                .stream().flatMap(contributor -> contributor.descriptors(mapper).stream())
                .filter(descriptor -> Optional.class.isAssignableFrom(descriptor.valueType().getRawClass()))
                .map(CacheDescriptor::name).toList();

        assertThat(optionalCaches).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void quizRuntimePackageEncodesAndDecodesTheValueSpringPuts() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var descriptor = (CacheDescriptor<Object>) new LmsCacheDescriptorContributor().descriptors(mapper).stream()
                .filter(candidate -> candidate.name().equals("quizRuntimePackage")).findFirst().orElseThrow();
        var codec = new JacksonCacheValueCodec(mapper);
        var pkg = new QuizPackageResponse(3, "pkg-hash", "content-hash", "sig", "auto", null, Map.of("q1", "A"), "pkg-1");

        var encoded = codec.encode(descriptor, pkg);

        assertThat(encoded.cacheable()).isTrue();
        assertThat(codec.decode(descriptor, encoded.bytes())).isEqualTo(pkg);
    }

    @Test
    @SuppressWarnings("unchecked")
    void studentIdentityAndQuizStartInfoAreSmallTypedRecordsThatRoundTrip() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var codec = new JacksonCacheValueCodec(mapper);
        var all = new LmsCacheDescriptorContributor().descriptors(mapper);
        var identityDescriptor = (CacheDescriptor<Object>) all.stream()
                .filter(candidate -> candidate.name().equals("lmsStudentIdentity")).findFirst().orElseThrow();
        var startDescriptor = (CacheDescriptor<Object>) all.stream()
                .filter(candidate -> candidate.name().equals("quizStartInfo")).findFirst().orElseThrow();
        var identity = new com.erg.ergspringboot.lms.LmsStudentIdentityLookup.StudentIdentity(
                "stu-1", "usr-1", "class-1", "school-1", "Nguyen An", "ENROLLED");
        var none = com.erg.ergspringboot.lms.LmsStudentIdentityLookup.StudentIdentity.NONE;
        var info = new com.erg.ergspringboot.lms.QuizStartInfo(true, "Quiz", "test", "ver-1", 2, true, "pkg-1", "hash",
                "ver-1", "7", "server-authoritative", 20);

        assertThat(codec.decode(identityDescriptor, codec.encode(identityDescriptor, identity).bytes())).isEqualTo(identity);
        // "No student" is cached as a value, so it must survive the codec as well.
        var decodedNone = (com.erg.ergspringboot.lms.LmsStudentIdentityLookup.StudentIdentity)
                codec.decode(identityDescriptor, codec.encode(identityDescriptor, none).bytes());
        assertThat(decodedNone.exists()).isFalse();
        assertThat(codec.decode(startDescriptor, codec.encode(startDescriptor, info).bytes())).isEqualTo(info);
        // Scoped by the login (key {tenantId, authUserId}) so a student write evicts exactly that learner.
        assertThat(identityDescriptor.scopeFor(List.of("t1", "usr-1"))).isEqualTo("usr-1");
        assertThat(identityDescriptor.l1Ttl()).hasSeconds(60);
    }

    @Test
    void platformCacheConfigDoesNotImportBusinessDtoTypes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/config/CacheConfig.java"));
        assertThat(source).doesNotContain("common.response").doesNotContain("common.dto");
        assertThat(source).contains("CacheDescriptorContributor").contains("CacheDescriptorRegistry");
    }
}
