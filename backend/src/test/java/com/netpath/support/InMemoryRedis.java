package com.netpath.support;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Backs a mocked {@link RedisTemplate} with a map so cache behaviour can be exercised without a
 * running Redis instance. TTL is not enforced; tests that care about expiry assert the TTL that was
 * requested instead.
 */
public final class InMemoryRedis {

    private InMemoryRedis() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> install(RedisTemplate<String, Object> template) {
        Map<String, Object> store = new ConcurrentHashMap<>();
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);

        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> store.get(invocation.getArgument(0, String.class)));

        doAnswer(invocation -> {
            store.put(invocation.getArgument(0, String.class), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), any(Duration.class));

        when(template.opsForValue()).thenReturn(valueOperations);
        when(template.delete(anyString()))
                .thenAnswer(invocation -> store.remove(invocation.getArgument(0, String.class)) != null);

        return store;
    }
}
