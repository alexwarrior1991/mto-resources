package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.telematics.TelematicsPositionsRequest;
import com.alejandro.mtoresources.application.dto.telematics.TelematicsPositionsResponse;
import com.alejandro.mtoresources.application.service.TelematicsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(ResourcesApiPaths.TELEMATICS)
@RequiredArgsConstructor
@Tag(name = "Telematics")
public class TelematicsController {

    private final TelematicsService telematicsService;

    @Operation(summary = "Positions of the GPS devices", description = "Only with resources-telematics-write (the service account "
            + "mto-telematics-svc). Each position of a road or road-rail vehicle becomes ON_ROAD with source TELEMATICS, at most one "
            + "every app.resources.telematics.min-interval (5 min) per device. Ignored, with the reason: an unknown device, a retired, "
            + "rail-only or attached vehicle, one on the track in a shift, and what comes too soon. Always 200 with the outcome of each.")
    @PostMapping("/positions")
    public ResponseEntity<TelematicsPositionsResponse> positions(@Valid @RequestBody TelematicsPositionsRequest request) {
        return ResponseEntity.ok(telematicsService.ingest(request));
    }
}
