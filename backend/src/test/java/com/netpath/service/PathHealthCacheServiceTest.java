package com.netpath.service;

import com.netpath.config.PathHealthProperties;
import com.netpath.dto.PathHealthResponseDto;
import com.netpath.entity.NetworkPath;
import com.netpath.entity.PathStatus;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.PathMetricRepository;
import com.netpath.support.InMemoryRedis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PathHealthCacheServiceTest {

    private static final long PATH_ID = 11L;

    private RedisTemplate<String, Object> redisTemplate;
    private Map<String, Object> store;
    private NetworkPathRepository networkPathRepository;
    private PathMetricRepository pathMetricRepository;
    private PathHealthCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        store = InMemoryRedis.install(redisTemplate);

        networkPathRepository = mock(NetworkPathRepository.class);
        pathMetricRepository = mock(PathMetricRepository.class);

        PathHealthService pathHealthService = new PathHealthService(
                pathMetricRepository, networkPathRepository, new PathHealthProperties());

        service = new PathHealthCacheService(
                redisTemplate, pathHealthService, networkPathRepository, new PathHealthProperties());

        NetworkPath path = new NetworkPath();
        path.setId(PATH_ID);
        when(networkPathRepository.findById(PATH_ID)).thenReturn(Optional.of(path));
    }

    private void stubHealthyTelemetry() {
        Object[] row = { PATH_ID, 40.0, 0.1, 60.0, 0.4, 5L, Instant.now() };
        when(pathMetricRepository.summariseForPaths(anyCollection(), any())).thenReturn(List.<Object[]>of(row));
    }

    @Test
    void rebuildsFromTheDatabaseOnAMissAndThenServesTheCachedValue() {
        stubHealthyTelemetry();

        PathHealthResponseDto first = service.get(PATH_ID);

        assertThat(first.getStatus()).isEqualTo(PathStatus.HEALTHY.name());
        assertThat(store).containsKey("path:health:" + PATH_ID);

        PathHealthResponseDto second = service.get(PATH_ID);

        assertThat(second.getStatus()).isEqualTo(PathStatus.HEALTHY.name());
        // The second read is served from the cache, so the aggregate query runs only once.
        verify(pathMetricRepository, times(1)).summariseForPaths(anyCollection(), any());
        verify(networkPathRepository, times(1)).findById(PATH_ID);
    }

    @Test
    void fallsBackToRecomputationWhenRedisFails() {
        stubHealthyTelemetry();
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis is down"));

        assertThat(service.get(PATH_ID).getStatus()).isEqualTo(PathStatus.HEALTHY.name());
        verify(pathMetricRepository, times(1)).summariseForPaths(anyCollection(), any());
    }

    @Test
    void returnsNullForAnUnknownPath() {
        when(networkPathRepository.findById(404L)).thenReturn(Optional.empty());

        assertThat(service.get(404L)).isNull();
    }

    @Test
    void putStoresWithoutTouchingTheDatabase() {
        PathHealthResponseDto health = new PathHealthResponseDto(
                PATH_ID, PathStatus.HEALTHY.name(), 40.0, 0.1, 60.0, 0.4, 5L, Instant.now().toString());

        service.put(PATH_ID, health);

        assertThat(store).containsKey("path:health:" + PATH_ID);
        verify(networkPathRepository, times(0)).save(any(NetworkPath.class));
    }

    @Test
    void cachingNeverWritesDomainState() {
        stubHealthyTelemetry();

        service.get(PATH_ID);

        // Persisting a status belongs to PathHealthService, not to the cache.
        verify(networkPathRepository, times(0)).save(any(NetworkPath.class));
    }

    @Test
    void invalidateRemovesTheEntry() {
        stubHealthyTelemetry();
        service.get(PATH_ID);
        assertThat(store).isNotEmpty();

        service.invalidate(PATH_ID);

        assertThat(store).doesNotContainKey("path:health:" + PATH_ID);
    }
}
