package com.netpath.service;

import com.netpath.dto.TrafficShiftRequest;
import com.netpath.dto.TrafficShiftResponse;
import com.netpath.entity.*;
import com.netpath.exception.BadRequestException;
import com.netpath.exception.ResourceNotFoundException;
import com.netpath.repository.NetworkPathRepository;
import com.netpath.repository.TrafficShiftLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrafficShiftServiceTest {

    private NetworkPathRepository networkPathRepository;
    private TrafficShiftLogRepository trafficShiftLogRepository;
    private TrafficShiftService service;

    private NetworkPath currentPath;
    private NetworkPath targetPath;

    @BeforeEach
    void setUp() {
        networkPathRepository = mock(NetworkPathRepository.class);
        trafficShiftLogRepository = mock(TrafficShiftLogRepository.class);
        service = new TrafficShiftService(networkPathRepository, trafficShiftLogRepository);

        Endpoint source = new Endpoint();
        source.setId(1L);
        Endpoint destination = new Endpoint();
        destination.setId(2L);

        currentPath = path(10L, "primary", source, destination);
        targetPath = path(20L, "alternative", source, destination);

        when(networkPathRepository.findById(10L)).thenReturn(Optional.of(currentPath));
        when(networkPathRepository.findById(20L)).thenReturn(Optional.of(targetPath));
        when(trafficShiftLogRepository.save(any(TrafficShiftLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private NetworkPath path(long id, String name, Endpoint source, Endpoint destination) {
        NetworkPath path = new NetworkPath();
        path.setId(id);
        path.setPathName(name);
        path.setSourceEndpoint(source);
        path.setDestinationEndpoint(destination);
        return path;
    }

    @Test
    void recordsASimulatedShiftWithoutClaimingTrafficWasMoved() {
        TrafficShiftRequest request = new TrafficShiftRequest("Degraded latency on the primary link");
        request.setRecommendedPathId(20L);

        TrafficShiftResponse response = service.simulateShift(10L, request);

        assertThat(response.getStatus()).isEqualTo(ShiftStatus.SIMULATED.name());
        assertThat(response.getOldPathName()).isEqualTo("primary");
        assertThat(response.getNewPathName()).isEqualTo("alternative");
        assertThat(response.getShiftedAt()).isNotNull();

        ArgumentCaptor<TrafficShiftLog> captor = ArgumentCaptor.forClass(TrafficShiftLog.class);
        verify(trafficShiftLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ShiftStatus.SIMULATED);
        assertThat(captor.getValue().getOldPath().getId()).isEqualTo(10L);
        assertThat(captor.getValue().getNewPath().getId()).isEqualTo(20L);
    }

    @Test
    void rejectsShiftingOntoADifferentEndpointPair() {
        Endpoint other = new Endpoint();
        other.setId(3L);
        NetworkPath unrelated = path(30L, "unrelated", currentPath.getSourceEndpoint(), other);
        when(networkPathRepository.findById(30L)).thenReturn(Optional.of(unrelated));

        TrafficShiftRequest request = new TrafficShiftRequest("nope");
        request.setRecommendedPathId(30L);

        assertThatThrownBy(() -> service.simulateShift(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("same source and destination");
    }

    @Test
    void rejectsShiftingAPathOntoItself() {
        TrafficShiftRequest request = new TrafficShiftRequest("self");
        request.setRecommendedPathId(10L);

        assertThatThrownBy(() -> service.simulateShift(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("itself");
    }

    @Test
    void requiresATargetPath() {
        assertThatThrownBy(() -> service.simulateShift(10L, new TrafficShiftRequest("missing target")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("recommendedPathId");
    }

    @Test
    void reportsAMissingSourcePath() {
        when(networkPathRepository.findById(999L)).thenReturn(Optional.empty());

        TrafficShiftRequest request = new TrafficShiftRequest("gone");
        request.setRecommendedPathId(20L);

        assertThatThrownBy(() -> service.simulateShift(999L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
