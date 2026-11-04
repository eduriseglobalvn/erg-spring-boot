package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * P3-8: every {@code erg.*} key in application.yml (all profile documents) must be read by the code,
 * through a {@code @ConfigurationProperties} field, a {@code ${...}}/{@code getProperty} string, or a
 * {@code @ConditionalOn*Property}. A key nobody reads is a setting that silently does nothing.
 */
class ErgPropertyKeysAreBoundTest {

    private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");
    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern ERG_KEY = Pattern.compile("(?<![A-Za-z0-9.])erg\\.[a-z0-9][a-z0-9.-]*[a-z0-9]");
    private static final Pattern CONDITIONAL_ON_PROPERTY =
            Pattern.compile("@ConditionalOn(?:Boolean)?Property\\(([^)]*)\\)");
    private static final Pattern ATTRIBUTE = Pattern.compile("(prefix|havingValue)\\s*=\\s*\"([^\"]*)\"");

    @Test
    void everyErgKeyInApplicationYmlIsConsumedByMainCode() throws IOException {
        Set<String> keys = ergLeafKeys();
        Set<String> referenced = referencedKeys();
        Map<String, Class<?>> propertyClasses = configurationPropertiesClasses();

        Set<String> unbound = keys.stream()
                .filter(key -> !referenced.contains(key))
                .filter(key -> !boundToField(key, propertyClasses))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unbound).as("erg.* keys in application.yml that no main code reads").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> ergLeafKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Object document : new Yaml().loadAll(Files.readString(APPLICATION_YML))) {
            if (document instanceof Map<?, ?> root && root.get("erg") instanceof Map<?, ?> erg) {
                collectLeaves("erg", (Map<String, Object>) erg, keys);
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static void collectLeaves(String path, Map<String, Object> node, Set<String> keys) {
        node.forEach((name, value) -> {
            String key = path + "." + name;
            if (value instanceof Map<?, ?> child) {
                collectLeaves(key, (Map<String, Object>) child, keys);
            } else {
                keys.add(key);
            }
        });
    }

    /** Keys named literally in main code: {@code "${erg.x:...}"}, {@code getProperty("erg.x")}, conditions. */
    private static Set<String> referencedKeys() throws IOException {
        Set<String> referenced = new HashSet<>();
        List<Path> sources;
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            sources = files.filter(file -> file.toString().endsWith(".java")).toList();
        }
        for (Path source : sources) {
            String code = Files.readString(source);
            Matcher literal = STRING_LITERAL.matcher(code);
            while (literal.find()) {
                ERG_KEY.matcher(literal.group(1)).results().forEach(key -> referenced.add(key.group()));
            }
            Matcher condition = CONDITIONAL_ON_PROPERTY.matcher(code);
            while (condition.find()) {
                referenced.addAll(conditionKeys(condition.group(1)));
            }
        }
        return referenced;
    }

    private static Set<String> conditionKeys(String attributes) {
        Map<String, String> named = new HashMap<>();
        ATTRIBUTE.matcher(attributes).results().forEach(match -> named.put(match.group(1), match.group(2)));
        String prefix = named.getOrDefault("prefix", "");
        String withoutNamed = ATTRIBUTE.matcher(attributes).replaceAll("");
        Set<String> keys = new HashSet<>();
        STRING_LITERAL.matcher(withoutNamed).results().forEach(name ->
                keys.add(prefix.isEmpty() ? name.group(1) : prefix + "." + name.group(1)));
        return keys;
    }

    private static Map<String, Class<?>> configurationPropertiesClasses() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));
        Map<String, Class<?>> byPrefix = new HashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.erg.ergspringboot")) {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), null);
            ConfigurationProperties annotation =
                    AnnotatedElementUtils.findMergedAnnotation(type, ConfigurationProperties.class);
            byPrefix.put(annotation.prefix(), type);
        }
        return byPrefix;
    }

    /** Relaxed binding: walks kebab-case segments down real fields; a Map or List field accepts the rest. */
    private static boolean boundToField(String key, Map<String, Class<?>> propertyClasses) {
        return propertyClasses.entrySet().stream()
                .filter(entry -> key.startsWith(entry.getKey() + "."))
                .anyMatch(entry -> bindsTo(entry.getValue(), key.substring(entry.getKey().length() + 1).split("\\.")));
    }

    private static boolean bindsTo(Class<?> type, String[] segments) {
        Field field = findField(type, camelCase(segments[0]));
        if (field == null) {
            return false;
        }
        Class<?> fieldType = field.getType();
        if (segments.length == 1 || Map.class.isAssignableFrom(fieldType)
                || Collection.class.isAssignableFrom(fieldType) || fieldType.isArray()) {
            return true;
        }
        boolean leafType = fieldType.isPrimitive() || fieldType.isEnum() || fieldType.getName().startsWith("java.");
        return !leafType && bindsTo(fieldType, Arrays.copyOfRange(segments, 1, segments.length));
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(name) && !Modifier.isStatic(field.getModifiers())) {
                    return field;
                }
            }
        }
        return null;
    }

    private static String camelCase(String kebab) {
        StringBuilder camel = new StringBuilder();
        boolean upper = false;
        for (char c : kebab.toCharArray()) {
            if (c == '-') {
                upper = true;
            } else {
                camel.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return camel.toString();
    }
}
