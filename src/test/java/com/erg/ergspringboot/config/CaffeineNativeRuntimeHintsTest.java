package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ImportRuntimeHints;

class CaffeineNativeRuntimeHintsTest {

    @Test
    void cacheConfigurationImportsNativeRuntimeHints() {
        assertThat(CacheConfig.class.getAnnotation(ImportRuntimeHints.class)).isNotNull();
    }

    @Test
    void registersTheCaffeineImplementationsSelectedByOurBuilders() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        new CaffeineNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

        Caffeine<Object, Object> tieredCacheBuilder = Caffeine.newBuilder();
        tieredCacheBuilder.maximumWeight(1)
                .weigher((key, value) -> 1)
                .expireAfterWrite(Duration.ofMinutes(1))
                .recordStats();
        Caffeine<Object, Object> rateLimitBuilder = Caffeine.newBuilder();
        rateLimitBuilder.maximumSize(1)
                .expireAfterAccess(Duration.ofMinutes(1));

        String tieredCacheImplementation = localCacheImplementation(tieredCacheBuilder);
        String tieredNodeFactory = nodeFactory(tieredCacheBuilder);
        String rateLimitImplementation = localCacheImplementation(rateLimitBuilder);
        String rateLimitNodeFactory = nodeFactory(rateLimitBuilder);

        assertThat(tieredCacheImplementation).endsWith(".SSSMWW");
        assertThat(tieredNodeFactory).endsWith(".PSWMW");
        assertThat(rateLimitImplementation).endsWith(".SSMSA");
        assertThat(rateLimitNodeFactory).endsWith(".PSAMS");

        assertThat(hints.reflection().getTypeHint(TypeReference.of(tieredCacheImplementation)))
                .isNotNull()
                .satisfies(typeHint -> assertThat(typeHint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.ACCESS_DECLARED_FIELDS));
        assertThat(hints.reflection().getTypeHint(TypeReference.of(rateLimitImplementation)))
                .isNotNull()
                .satisfies(typeHint -> assertThat(typeHint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.ACCESS_DECLARED_FIELDS));
        assertThat(hints.reflection()
                .getTypeHint(TypeReference.of(tieredNodeFactory)))
                .isNotNull()
                .satisfies(typeHint -> assertThat(typeHint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertThat(hints.reflection()
                .getTypeHint(TypeReference.of(rateLimitNodeFactory)))
                .isNotNull()
                .satisfies(typeHint -> assertThat(typeHint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
    }

    private String localCacheImplementation(Caffeine<?, ?> builder) throws Exception {
        Class<?> factory = Class.forName("com.github.benmanes.caffeine.cache.LocalCacheFactory");
        Method className = factory.getDeclaredMethod("getClassName", Caffeine.class);
        className.setAccessible(true);
        return factory.getPackageName() + "." + className.invoke(null, builder);
    }

    private String nodeFactory(Caffeine<?, ?> builder) throws Exception {
        Class<?> factory = Class.forName("com.github.benmanes.caffeine.cache.NodeFactory");
        Method className = factory.getDeclaredMethod("getClassName", Caffeine.class, boolean.class);
        className.setAccessible(true);
        return factory.getPackageName() + "." + className.invoke(null, builder, false);
    }
}
