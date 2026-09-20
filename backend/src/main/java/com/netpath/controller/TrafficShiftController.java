package com.netpath.controller;

import com.netpath.dto.TrafficShiftResponse;
import com.netpath.service.TrafficShiftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shifts")
public class TrafficShiftController {

    private final TrafficShiftService trafficShiftService;

    public TrafficShiftController(TrafficShiftService trafficShiftService) {
        this.trafficShiftService = trafficShiftService;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<TrafficShiftResponse>> getRecentShifts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(trafficShiftService.getRecentShifts(limit));
    }
}
