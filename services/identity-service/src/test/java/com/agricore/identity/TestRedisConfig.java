package com.agricore.identity;

import com.agricore.identity.infrastructure.security.LoginRateLimiter;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    LoginRateLimiter loginRateLimiter() {
        LoginRateLimiter limiter = Mockito.mock(LoginRateLimiter.class);
        when(limiter.allow(anyString())).thenReturn(true);
        return limiter;
    }

    @Bean
    @Primary
    StringRedisTemplate stringRedisTemplate() {
        return Mockito.mock(StringRedisTemplate.class);
    }

    @Bean
    @Primary
    RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }
}
