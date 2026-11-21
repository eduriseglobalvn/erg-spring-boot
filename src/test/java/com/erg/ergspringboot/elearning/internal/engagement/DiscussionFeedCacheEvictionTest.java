package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.config.ElearningCacheDescriptorContributor;
import com.erg.ergspringboot.common.response.elearning.DiscussionFeedSnapshot;
import com.erg.ergspringboot.elearning.internal.engagement.StudentDiscussionModerationService.Moderator;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService.CommentsScope;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService.CreateDiscussionCommand;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService.ReplyCommand;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService.SetReactionCommand;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.LmsStudentIdentityLookup;
import com.erg.ergspringboot.lms.LmsStudentIdentityLookup.StudentIdentity;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.JacksonCacheValueCodec;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import tools.jackson.databind.json.JsonMapper;

/** The shared feed page is cached per class, so every writer of a post, comment, reaction or moderation state must evict that class. */
class DiscussionFeedCacheEvictionTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StudentDiscussionFeedCache feeds = mock(StudentDiscussionFeedCache.class);
    private final LmsStudentIdentityLookup identities = mock(LmsStudentIdentityLookup.class);
    private final StudentEngagementService students = new StudentEngagementService(identities, jdbc, event -> { }, feeds);
    private final StudentDiscussionModerationService moderation =
            new StudentDiscussionModerationService(mock(ClassroomRepository.class), jdbc, feeds);
    private final Moderator admin = new Moderator("t1", "admin", true);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void everyModerationWriterEvictsTheClassOfItsTarget() {
        // requireTarget (class of the post or comment) and requireClass (tenant-wide admin) read these two statements.
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq("t1"), anyString())).thenReturn("class-1");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("t1"), eq("class-1"))).thenReturn(1);

        moderation.setHidden(admin, "POST", "post-1", true);   // hidden post leaves student feeds at once
        moderation.setHidden(admin, "COMMENT", "c-1", true);   // hidden comment likewise
        moderation.setHidden(admin, "POST", "post-1", false);
        moderation.delete(admin, "POST", "post-1");
        moderation.delete(admin, "COMMENT", "c-1");
        // pin and accept re-read the post afterwards (not stubbed here), but they evict before that read
        assertThatThrownBy(() -> moderation.setPinned(admin, "post-1", true)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> moderation.acceptReply(admin, "post-1", null)).isInstanceOf(ResourceNotFoundException.class);

        verify(feeds, org.mockito.Mockito.times(7)).evictClass("t1", "class-1");
    }

    @Test
    void everyStudentWriterEvictsTheStudentsClass() {
        when(identities.byAuthUser("t1", "u1")).thenReturn(new StudentIdentity("s1", "u1", "class-1", "school-1", "An", "ENROLLED"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("t1"), eq("post-1"), eq("class-1"))).thenReturn(1);

        assertThatThrownBy(() -> students.createDiscussion(new CreateDiscussionCommand(
                "t1", "u1", "key-1", "Tiêu đề", "Nội dung", null, List.of()))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> students.reply(new ReplyCommand("t1", "u1", "key-2", "post-1", null, "Trả lời", List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
        students.setReaction(new SetReactionCommand("t1", "u1", "POST", "post-1", "like"));

        // post, reply and reaction: three writes, three evictions (the re-reads after each write are not stubbed)
        verify(feeds, org.mockito.Mockito.times(3)).evictClass("t1", "class-1");
        // following only changes the viewer's own row, never the shared page
        students.follow("t1", "u1", "post-1", true);
        verify(feeds, org.mockito.Mockito.times(3)).evictClass("t1", "class-1");
    }

    @Test
    void commentPagingIsLimitedToPostsAndRootCommentsOfTheStudentsClass() {
        when(identities.byAuthUser("t1", "u1")).thenReturn(new StudentIdentity("s1", "u1", "class-1", "school-1", "An", "ENROLLED"));
        when(identities.byAuthUser("t1", "stranger")).thenReturn(StudentIdentity.NONE);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("t1"), eq("post-other-class"), eq("class-1"))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("t1"), eq("post-1"), eq("class-1"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("t1"), eq("c-reply"), eq("post-1"), eq(false))).thenReturn(0);

        assertThatThrownBy(() -> students.comments(new CommentsScope("t1", "u1", "post-other-class", null, 0, 20)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("student class");
        assertThatThrownBy(() -> students.comments(new CommentsScope("t1", "u1", "post-1", "c-reply", 0, 20)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Parent comment");
        assertThatThrownBy(() -> students.comments(new CommentsScope("t1", "u1", " ", null, 0, 20)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> students.comments(new CommentsScope("t1", "stranger", "post-1", null, 0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void evictClassDropsOnlyThatClassAndIgnoresBlankIds() {
        LmsReadCacheInvalidator invalidator = mock(LmsReadCacheInvalidator.class);
        StudentDiscussionFeedCache cache = new StudentDiscussionFeedCache(jdbc, invalidator);

        cache.evictClass("t1", "class-1");
        cache.evictClass("t1", " ");
        cache.evictClass("t1", null);

        verify(invalidator).evictScopesAfterCommit("t1", Map.of("elearningDiscussionFeed", List.of("class-1")));
        verify(invalidator, org.mockito.Mockito.times(1)).evictScopesAfterCommit(anyString(), any());
    }

    @Test
    void cacheKeyCarriesEveryInputAndScopesByClass() throws Exception {
        Method snapshot = java.util.Arrays.stream(StudentDiscussionFeedCache.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("snapshot")).findFirst().orElseThrow();
        Cacheable cacheable = snapshot.getAnnotation(Cacheable.class);
        List<String> parts = java.util.Arrays.stream(cacheable.key().replaceAll("^\\{|}$", "").split(",")).map(String::trim).toList();

        assertThat(cacheable.sync()).isTrue();
        assertThat(parts).containsExactly("#tenantId", "#classId", "#page", "#size", "#kind", "#subject", "#author", "#includeHidden");

        var mapper = JsonMapper.builder().findAndAddModules().build();
        @SuppressWarnings("unchecked")
        CacheDescriptor<Object> descriptor = (CacheDescriptor<Object>) new ElearningCacheDescriptorContributor().descriptors(mapper)
                .getFirst();
        assertThat(descriptor.name()).isEqualTo("elearningDiscussionFeed");
        assertThat(descriptor.scopeFor(List.of("t1", "class-1", 0, 20, "ALL", "", "-", false))).isEqualTo("class-1");
        assertThat(descriptor.l1Ttl()).hasSeconds(20);

        var codec = new JacksonCacheValueCodec(mapper);
        var snapshotValue = new DiscussionFeedSnapshot(
                List.of(new DiscussionFeedSnapshot.Post("p1", "u1", "An", "class-1", "6A1", "Tiêu đề", "Nội dung", OffsetDateTime.parse("2026-01-02T03:04:05Z"),
                        "[]", null, false, null, true, null)),
                List.of(new DiscussionFeedSnapshot.Comment("c1", "p1", null, "u2", "Bình", "Ok", OffsetDateTime.parse("2026-01-02T03:04:05Z"), "[]", false)),
                List.of(new DiscussionFeedSnapshot.ReactionCount("POST", "p1", "like", 3)),
                Map.of("p1", 5), Map.of("p1", 2), Map.of("c1", 3), 7);
        assertThat(codec.decode(descriptor, codec.encode(descriptor, snapshotValue).bytes())).isEqualTo(snapshotValue);
    }
}
