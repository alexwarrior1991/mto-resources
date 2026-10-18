package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.FleetPositionResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleUpdateRequest;
import com.alejandro.mtoresources.application.service.VehicleLocationService;
import com.alejandro.mtoresources.application.service.VehicleService;
import com.alejandro.mtoresources.configuration.security.SecurityRoles;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(ResourcesApiPaths.VEHICLES)
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleLocationService locationService;

    @Tag(name = "Vehicles")
    @Operation(summary = "Create vehicle", description = "A unit of the fleet, of a model. Its model decides what it needs: a registration "
            + "plate if it runs on the road (and none if rail-only), a rail vehicle number if it may run on open track in transport mode. "
            + "No home base: where it is comes from its locations.")
    @PostMapping
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse response = vehicleService.create(request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.VEHICLES + "/" + response.id())).body(response);
    }

    @Tag(name = "Vehicles")
    @Operation(summary = "Update vehicle", description = "A key left out is not touched. A status other than OPERATIONAL says why "
            + "(statusReason); UNDER_REPAIR and OUT_OF_SERVICE leave it unavailable for shifts. active=false retires it, like DELETE, "
            + "and needs the delete role too.")
    @PreAuthorize("!#request.deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PutMapping("/{id}")
    public ResponseEntity<VehicleResponse> update(@PathVariable UUID id, @Valid @RequestBody VehicleUpdateRequest request) {
        return ResponseEntity.ok(vehicleService.update(id, request));
    }

    @Tag(name = "Vehicles")
    @Operation(summary = "Patch vehicle", description = "application/merge-patch+json: null empties the optional fields. With version, "
            + "409 CON-001 if it changed since it was read.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = VehicleUpdateRequest.class))))
    @PreAuthorize("!#patch.values().deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PatchMapping(value = "/{id}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<VehicleResponse> patch(@PathVariable UUID id,
                                                 @Parameter(hidden = true) @Valid MergePatch<VehicleUpdateRequest> patch) {
        return ResponseEntity.ok(vehicleService.patch(id, patch));
    }

    @Tag(name = "Vehicles")
    @Operation(summary = "Get vehicle", description = "With its model and where it is now (position, absent if it was never located).")
    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(vehicleService.findById(id));
    }

    @Tag(name = "Vehicles")
    @Operation(summary = "Search vehicles", description = "available: active and OPERATIONAL or LIMITED.")
    @GetMapping
    public ResponseEntity<PageResponse<VehicleResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) VehicleMobility mobility,
            @RequestParam(required = false) VehicleCategory category,
            @RequestParam(required = false) VehicleOperationalStatus status,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = {"code", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(vehicleService.search(search, typeId, mobility, category, status, available, active, pageable));
    }

    @Tag(name = "Vehicles")
    @Operation(summary = "Retire vehicle", description = "Never deleted: its locations and the shifts name it. 409 VEH-001 while it is the "
            + "default vehicle of an active team.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        vehicleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Vehicles")
    @Operation(summary = "Vehicle change history", description = "Envers revisions of the vehicle record; where it was is in /locations.")
    @GetMapping("/{id}/revisions")
    public ResponseEntity<PageResponse<EntityRevisionResponse<VehicleResponse>>> revisions(@PathVariable UUID id,
                                                                                           @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(vehicleService.findRevisions(id, pageable));
    }

    @Tag(name = "Vehicle Locations")
    @Operation(summary = "Record where a vehicle is", description = "Append-only; resources-location-write is enough. Where it can be depends "
            + "on its mobility: a rail-only vehicle is never ON_ROAD and is PARKED on a track (a track and kp, or a site with rail access); "
            + "a road-only one is never ON_TRACK and is PARKED off it; a road-rail one may be on both. ATTACHED needs its carrier and "
            + "how (TOWED by one with TOWING, CARRIED by a road vehicle, or anything portable). accessPointId (GET /track-access-points) "
            + "says where it on-tracked or off-tracked: the access point is enabled, on the track of the location, admits road-rail "
            + "vehicles if it is one, is a PREPARED_ACCESS if the model needs one and takes its weight (409 VEH-001 otherwise; "
            + "404 TAP-404 if unknown). observedAt in the past is kept in the history and moves the position only if it is newer "
            + "than the current one. 409 VEH-001 when a rule says no.")
    @PostMapping("/{id}/locations")
    public ResponseEntity<VehicleLocationResponse> recordLocation(@PathVariable UUID id, @Valid @RequestBody VehicleLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.recordManual(id, request));
    }

    @Tag(name = "Vehicle Locations")
    @Operation(summary = "Location history of a vehicle", description = "The most recently observed first; from and to (ISO-8601 instants) "
            + "bound observedAt, from inclusive and to exclusive.")
    @GetMapping("/{id}/locations")
    public ResponseEntity<PageResponse<VehicleLocationResponse>> history(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(locationService.history(id, from, to, pageable));
    }

    @Tag(name = "Vehicle Locations")
    @Operation(summary = "Fleet board", description = "Every active vehicle with a known position. The filters look at where it really is: "
            + "an ATTACHED vehicle matches when its carrier does, and brings the carrier's position as effective.")
    @GetMapping("/positions")
    public ResponseEntity<PageResponse<FleetPositionResponse>> positions(
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) VehicleLocationKind kind,
            @PageableDefault(size = 50, sort = {"observedAt", "vehicleId"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(locationService.positions(executionPackageId, trackId, kind, pageable));
    }
}
