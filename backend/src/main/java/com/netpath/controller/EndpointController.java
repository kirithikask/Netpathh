package com.netpath.controller;

import com.netpath.dto.EndpointDto;
import com.netpath.service.EndpointService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/endpoints")
public class EndpointController {

    /** Only properties that exist on {@code Endpoint}, so a stale client cannot reach the query. */
    private static final Set<String> SORTABLE = Set.of(
            "id", "name", "ipAddress", "region", "status", "createdAt", "updatedAt");

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @GetMapping
    public ResponseEntity<Page<EndpointDto>> getAllEndpoints(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = PageRequests.sort(sortBy, sortDir, SORTABLE);
        return ResponseEntity.ok(endpointService.getAllEndpoints(PageRequests.of(page, size, sort)));
    }

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<Page<EndpointDto>> getEndpointsByApplication(
            @PathVariable Long applicationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(endpointService.getEndpointsByApplication(
                applicationId, PageRequests.of(page, size)));
    }

    @GetMapping("/region/{region}")
    public ResponseEntity<Page<EndpointDto>> getEndpointsByRegion(
            @PathVariable String region,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(endpointService.getEndpointsByRegion(
                region, PageRequests.of(page, size)));
    }

    @GetMapping("/application/{applicationId}/region/{region}")
    public ResponseEntity<Page<EndpointDto>> getEndpointsByApplicationAndRegion(
            @PathVariable Long applicationId,
            @PathVariable String region,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(endpointService.getEndpointsByApplicationAndRegion(
                applicationId, region, PageRequests.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EndpointDto> getEndpointById(@PathVariable Long id) {
        return ResponseEntity.ok(endpointService.getEndpointById(id));
    }

    @PostMapping
    public ResponseEntity<EndpointDto> createEndpoint(@Valid @RequestBody EndpointDto dto,
                                                       @RequestParam Long applicationId) {
        EndpointDto created = endpointService.createEndpoint(dto, applicationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EndpointDto> updateEndpoint(@PathVariable Long id,
                                                       @Valid @RequestBody EndpointDto dto) {
        return ResponseEntity.ok(endpointService.updateEndpoint(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable Long id) {
        endpointService.deleteEndpoint(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/regions")
    public ResponseEntity<java.util.List<String>> getAllRegions() {
        return ResponseEntity.ok(endpointService.getAllRegions());
    }
}
