package com.erg.ergspringboot.platform.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisInfrastructureConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisInfrastructureConfig.class);

    @Test
    void createsIndependentStateAndCacheConnections() {
        contextRunner.withPropertyValues(
                        "erg.redis.state.enabled=true",
                        "erg.redis.state.url=redis://:state-secret@localhost:6379/0",
                        "erg.redis.cache.enabled=true",
                        "erg.redis.cache.url=redis://:cache-secret@localhost:6380/1"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ErgRedisProperties.class);
                    assertThat(context).hasBean("redisConnectionFactory");
                    assertThat(context).hasBean("stateRedisConnectionFactory");
                    assertThat(context).hasBean("stateStringRedisTemplate");
                    assertThat(context).hasBean("stringRedisTemplate");
                    assertThat(context).hasBean("cacheRedisConnectionFactory");
                    assertThat(context).hasBean("cacheStringRedisTemplate");

                    RedisConnectionFactory state = context.getBean(
                            "redisConnectionFactory", RedisConnectionFactory.class);
                    RedisConnectionFactory cache = context.getBean(
                            "cacheRedisConnectionFactory", RedisConnectionFactory.class);
                    assertThat(state).isNotSameAs(cache);
                    assertThat(context.getBean(StringRedisTemplate.class))
                            .isSameAs(context.getBean("stateStringRedisTemplate"));

                    LettuceConnectionFactory stateLettuce = (LettuceConnectionFactory) state;
                    LettuceConnectionFactory cacheLettuce = (LettuceConnectionFactory) cache;
                    assertThat(stateLettuce.getHostName()).isEqualTo("localhost");
                    assertThat(stateLettuce.getPort()).isEqualTo(6379);
                    assertThat(stateLettuce.getDatabase()).isZero();
                    assertThat(cacheLettuce.getHostName()).isEqualTo("localhost");
                    assertThat(cacheLettuce.getPort()).isEqualTo(6380);
                    assertThat(cacheLettuce.getDatabase()).isEqualTo(1);
                });
    }

    @Test
    void rejectsEnabledCacheWithoutUrl() {
        contextRunner.withPropertyValues("erg.redis.cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("erg.redis.cache.url must be configured");
                });
    }

    @Test
    void createsNoRedisInfrastructureWhenBothEndpointsAreDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
        });
    }
}
