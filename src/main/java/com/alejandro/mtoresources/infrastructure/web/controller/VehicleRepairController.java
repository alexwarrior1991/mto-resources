package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.repair.VehicleRepairRequest;
import com.alejandro.mtoresources.application.dto.repair.VehicleRepairResponse;
import com.alejandro.mtoresources.application.dto.repair.VehicleRepairUpdateRequest;
import com.alejandro.mtoresources.application.service.VehicleRepairService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(ResourcesApiPaths.VEHICLE_REPAIRS)
@RequiredArgsConstructor
@Tag(name = "Vehicle Repairs")
public class VehicleRepairController {

    private final VehicleRepairService repairService;

    @Operation(summary = "Repairs of a vehicle", description = "All of them, the most recent first.")
    @GetMapping
    public ResponseEntity<List<VehicleRepairResponse>> findAll(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok(repairService.findAll(vehicleId));
    }

    @Operation(summary = "Plan or open a repair", description = "Born PLANNED (with plannedFrom) or OPEN (opened now). One that "
            + "immobilises (the default) and is open puts the vehicle UNDER_REPAIR; planned or open, the vehicle is not available on "
            + "its dates. Publishes vehicle.updated with its unavailable periods.")
    @PostMapping
    public ResponseEntity<VehicleRepairResponse> create(@PathVariable UUID vehicleId, @Valid @RequestBody VehicleRepairRequest request) {
        VehicleRepairResponse response = repairService.create(vehicleId, request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.VEHICLES + "/" + vehicleId + "/repairs/" + response.id())).body(response);
    }

    @Operation(summary = "Patch a repair", description = "application/merge-patch+json. The planned dates change while it is planned "
            + "or open, whether it immobilises only while planned (409 REP-001 otherwise). With version, 409 CON-001 if it changed since "
            + "it was read. Publishes vehicle.updated.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = VehicleRepairUpdateRequest.class))))
    @PatchMapping(value = "/{repairId}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<VehicleRepairResponse> patch(@PathVariable UUID vehicleId, @PathVariable UUID repairId,
                                                       @Parameter(hidden = true) @Valid MergePatch<VehicleRepairUpdateRequest> patch) {
        return ResponseEntity.ok(repairService.patch(vehicleId, repairId, patch));
    }

    @Operation(summary = "Open a planned repair", description = "The vehicle enters the workshop now (UNDER_REPAIR if it immobilises "
            + "it). 409 REP-001 if it is not planned.")
    @PostMapping("/{repairId}/open")
    public ResponseEntity<VehicleRepairResponse> open(@PathVariable UUID vehicleId, @PathVariable UUID repairId) {
        return ResponseEntity.ok(repairService.open(vehicleId, repairId));
    }

    @Operation(summary = "Close an open repair", description = "The vehicle leaves the workshop now: back to OPERATIONAL if it was the "
            + "last open one that immobilised it and it is still UNDER_REPAIR. 409 REP-001 if it is not open.")
    @PostMapping("/{repairId}/close")
    public ResponseEntity<VehicleRepairResponse> close(@PathVariable UUID vehicleId, @PathVariable UUID repairId) {
        return ResponseEntity.ok(repairService.close(vehicleId, repairId));
    }

    @Operation(summary = "Cancel a repair", description = "Planned or open; an open one releases the vehicle as closing it would. "
            + "Nothing is deleted. 409 REP-001 if it is already closed or cancelled.")
    @PostMapping("/{repairId}/cancel")
    public ResponseEntity<VehicleRepairResponse> cancel(@PathVariable UUID vehicleId, @PathVariable UUID repairId) {
        return ResponseEntity.ok(repairService.cancel(vehicleId, repairId));
    }
}
