package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeUpdateRequest;
import com.alejandro.mtoresources.application.service.VehicleTypeService;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(ResourcesApiPaths.VEHICLE_TYPES)
@RequiredArgsConstructor
@Tag(name = "Vehicle Types")
public class VehicleTypeController {

    private final VehicleTypeService typeService;

    @Operation(summary = "Create vehicle type", description = "A model: what all its units share. mobility (RAIL_ONLY, ROAD_RAIL, ROAD_ONLY) "
            + "decides the rest: a category of that mobility, rail gear only and always on a road-rail model, no track figures on a "
            + "road-only one, no road figures on a rail-only one, and crane, platform, insulation and towing figures with their "
            + "capability. 400 otherwise.")
    @PostMapping
    public ResponseEntity<VehicleTypeResponse> create(@Valid @RequestBody VehicleTypeRequest request) {
        VehicleTypeResponse response = typeService.create(request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.VEHICLE_TYPES + "/" + response.id())).body(response);
    }

    @Operation(summary = "Update vehicle type", description = "Code and mobility do not change. Every vehicle of the model is republished, "
            + "because its event carries the model.")
    @PutMapping("/{id}")
    public ResponseEntity<VehicleTypeResponse> update(@PathVariable UUID id, @Valid @RequestBody VehicleTypeUpdateRequest request) {
        return ResponseEntity.ok(typeService.update(id, request));
    }

    @Operation(summary = "Patch vehicle type", description = "application/merge-patch+json: null empties the optional figures.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = VehicleTypeUpdateRequest.class))))
    @PatchMapping(value = "/{id}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<VehicleTypeResponse> patch(@PathVariable UUID id,
                                                     @Parameter(hidden = true) @Valid MergePatch<VehicleTypeUpdateRequest> patch) {
        return ResponseEntity.ok(typeService.patch(id, patch));
    }

    @Operation(summary = "Get vehicle type")
    @GetMapping("/{id}")
    public ResponseEntity<VehicleTypeResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(typeService.findById(id));
    }

    @Operation(summary = "Search vehicle types")
    @GetMapping
    public ResponseEntity<PageResponse<VehicleTypeResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) VehicleMobility mobility,
            @RequestParam(required = false) VehicleCategory category,
            @RequestParam(required = false) VehicleCapability capability,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = {"code", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(typeService.search(search, mobility, category, capability, active, pageable));
    }

    @Operation(summary = "Retire vehicle type", description = "No new vehicle is of it; the existing ones stay.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        typeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
