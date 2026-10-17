package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.fleet.TrackAccessPointResponse;
import com.alejandro.mtoresources.application.service.TrackAccessPointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Los puntos de acceso a la via, de solo lectura: el maestro es de {@code mto-configuration} y llega aqui
 * por sus datos maestros. Leer es {@code resources-read}, por la regla de todo {@code GET}; cualquier
 * escritura cae en la denegacion de lo que nadie declaro.
 */
@RestController
@RequestMapping(ResourcesApiPaths.TRACK_ACCESS_POINTS)
@RequiredArgsConstructor
@Tag(name = "Track Access Points")
public class TrackAccessPointController {

    private final TrackAccessPointService accessPointService;

    @Operation(summary = "List track access points", description = "Where a road-rail vehicle on-tracks and off-tracks, as mto-configuration "
            + "publishes it (its ids, kp in metres): what the location dialog offers for accessPointId. A list, not a page, ordered by track, "
            + "kp and id; a filter left out does not filter. Read-only here: the master data is mto-configuration's.")
    @GetMapping
    public ResponseEntity<List<TrackAccessPointResponse>> list(
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) Boolean enabled) {
        return ResponseEntity.ok(accessPointService.list(trackId, executionPackageId, enabled));
    }
}
