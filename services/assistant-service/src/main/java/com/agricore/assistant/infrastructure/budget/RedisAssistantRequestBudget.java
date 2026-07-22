package com.agricore.assistant.infrastructure.budget;

import com.agricore.assistant.application.port.AssistantRequestBudget;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.infrastructure.configuration.AssistantBudgetProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "agricore.assistant.budget",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisAssistantRequestBudget implements AssistantRequestBudget {

    private static final Logger log = LoggerFactory.getLogger(RedisAssistantRequestBudget.class);
    private static final int ALLOWED = 0;
    private static final int USER_REQUESTS_EXCEEDED = 1;
    private static final int IP_REQUESTS_EXCEEDED = 2;
    private static final int USER_TOKENS_EXCEEDED = 3;
    private static final int IP_TOKENS_EXCEEDED = 4;
    private static final String RESERVE_SCRIPT = """
            local maxRequests = tonumber(ARGV[1])
            local maxTokens = tonumber(ARGV[2])
            local requestedTokens = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local requestedRequests = tonumber(ARGV[5])
            local userRequests = tonumber(redis.call('GET', KEYS[1]) or '0')
            local ipRequests = tonumber(redis.call('GET', KEYS[2]) or '0')
            local userTokens = tonumber(redis.call('GET', KEYS[3]) or '0')
            local ipTokens = tonumber(redis.call('GET', KEYS[4]) or '0')
            if userRequests + requestedRequests > maxRequests then return 1 end
            if ipRequests + requestedRequests > maxRequests then return 2 end
            if userTokens + requestedTokens > maxTokens then return 3 end
            if ipTokens + requestedTokens > maxTokens then return 4 end
            local incrementAndExpire = function(key, amount)
                local value = redis.call('INCRBY', key, amount)
                if value == amount then redis.call('EXPIRE', key, ttl) end
            end
            if requestedRequests > 0 then
                incrementAndExpire(KEYS[1], requestedRequests)
                incrementAndExpire(KEYS[2], requestedRequests)
            end
            incrementAndExpire(KEYS[3], requestedTokens)
            incrementAndExpire(KEYS[4], requestedTokens)
            return 0
            """;
    private static final DefaultRedisScript<Long> SCRIPT =
            new DefaultRedisScript<>(RESERVE_SCRIPT, Long.class);

    private final StringRedisTemplate redis;
    private final AssistantBudgetProperties properties;

    public RedisAssistantRequestBudget(
            StringRedisTemplate redis,
            AssistantBudgetProperties properties
    ) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void reserve(AssistantActor actor, String clientIp, int estimatedInputTokens) {
        reserve(actor, clientIp, estimatedInputTokens, 1);
    }

    @Override
    public void reserveAdditionalTokens(
            AssistantActor actor,
            String clientIp,
            int additionalInputTokens
    ) {
        if (additionalInputTokens <= 0) {
            return;
        }
        reserve(actor, clientIp, additionalInputTokens, 0);
    }

    private void reserve(
            AssistantActor actor,
            String clientIp,
            int estimatedInputTokens,
            int requestedRequests
    ) {
        if (actor == null || actor.subject() == null) {
            throw AssistantException.invalidActorSubject();
        }
        if (estimatedInputTokens < 1 || estimatedInputTokens > properties.getMaxTokens()) {
            throw AssistantException.requestBudgetExceeded();
        }
        String userHash = hash(actor.subject().toString());
        String ipHash = hash(clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.strip());
        List<String> keys = List.of(
                key("user:req", userHash),
                key("ip:req", ipHash),
                key("user:token", userHash),
                key("ip:token", ipHash)
        );
        Duration window = properties.getWindow();
        try {
            Long result = redis.execute(
                    SCRIPT,
                    keys,
                    Integer.toString(properties.getMaxRequests()),
                    Integer.toString(properties.getMaxTokens()),
                    Integer.toString(estimatedInputTokens),
                    Long.toString(window.toSeconds()),
                    Integer.toString(requestedRequests)
            );
            if (result == null) {
                throw AssistantException.requestBudgetUnavailable();
            }
            switch (result.intValue()) {
                case ALLOWED -> {
                }
                case USER_REQUESTS_EXCEEDED, IP_REQUESTS_EXCEEDED,
                     USER_TOKENS_EXCEEDED, IP_TOKENS_EXCEEDED
                        -> throw AssistantException.requestBudgetExceeded();
                default -> throw AssistantException.requestBudgetUnavailable();
            }
        } catch (AssistantException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Assistant request budget Redis error; denying request type={}",
                    exception.getClass().getName());
            throw AssistantException.requestBudgetUnavailable();
        }
    }

    private String key(String dimension, String subjectHash) {
        return properties.getKeyPrefix() + ":" + dimension + ":" + subjectHash;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
