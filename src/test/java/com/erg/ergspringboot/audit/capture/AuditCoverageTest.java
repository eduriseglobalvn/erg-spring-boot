package com.erg.ergspringboot.audit.capture;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.audit.capture.AuditCaptureRules.Decision;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Every REST write and GraphQL mutation is either captured (the default) or deliberately listed in
 * {@link AuditCaptureRules}; and no list entry is stale (it must match a real handler).
 */
class AuditCoverageTest {

    private final Set<String> restWrites = new TreeSet<>();
    private final Set<String> mutations = new TreeSet<>();

    AuditCoverageTest() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        for (BeanDefinition definition : scanner.findCandidateComponents("com.erg.ergspringboot")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String prefix = base == null || base.path().length == 0 ? "" : base.path()[0];
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping != null && writes(mapping.method())) {
                    String[] paths = mapping.path().length == 0 ? new String[] {""} : mapping.path();
                    for (String path : paths) restWrites.add(prefix + path);
                }
                MutationMapping mutation = AnnotatedElementUtils.findMergedAnnotation(method, MutationMapping.class);
                if (mutation != null) mutations.add(mutation.name().isEmpty() ? method.getName() : mutation.name());
            }
        }
    }

    private static boolean writes(RequestMethod[] methods) {
        if (methods.length == 0) return true;
        for (RequestMethod method : methods) {
            if (method != RequestMethod.GET && method != RequestMethod.HEAD && method != RequestMethod.OPTIONS) return true;
        }
        return false;
    }

    @Test
    void everyWriteHandlerIsCapturedOrExplicitlySkipped() {
        assertThat(restWrites).as("scanned REST write handlers").isNotEmpty();
        assertThat(mutations).as("scanned GraphQL mutations").isNotEmpty();
        // The interceptor and instrumentation capture anything the rules do not skip, so each handler
        // resolves to exactly one decision; the lists below are the only opt-outs.
        List<String> unresolved = new ArrayList<>();
        restWrites.forEach(path -> { if (AuditCaptureRules.classifyRest(path) == null) unresolved.add(path); });
        mutations.forEach(name -> { if (AuditCaptureRules.classifyMutation(name) == null) unresolved.add(name); });
        assertThat(unresolved).isEmpty();
        assertThat(restWrites.stream().filter(p -> AuditCaptureRules.classifyRest(p) == Decision.CAPTURE)).isNotEmpty();
    }

    @Test
    void noSkipEntryIsStale() {
        List<String> stale = new ArrayList<>();
        for (String pattern : concat(AuditCaptureRules.SELF_SERVICE_REST, AuditCaptureRules.ALREADY_AUDITED_REST)) {
            if (restWrites.stream().noneMatch(path -> AuditCaptureRules.matchesAny(List.of(pattern), path))) stale.add(pattern);
        }
        for (String name : concat(List.copyOf(AuditCaptureRules.SELF_SERVICE_MUTATIONS),
                List.copyOf(AuditCaptureRules.ALREADY_AUDITED_MUTATIONS))) {
            if (!mutations.contains(name)) stale.add(name);
        }
        assertThat(stale).as("skip-list entries without a matching handler").isEmpty();
    }

    @Test
    void skipListsDoNotOverlap() {
        assertThat(AuditCaptureRules.SELF_SERVICE_MUTATIONS).doesNotContainAnyElementsOf(AuditCaptureRules.ALREADY_AUDITED_MUTATIONS);
        assertThat(AuditCaptureRules.SELF_SERVICE_REST).doesNotContainAnyElementsOf(AuditCaptureRules.ALREADY_AUDITED_REST);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
