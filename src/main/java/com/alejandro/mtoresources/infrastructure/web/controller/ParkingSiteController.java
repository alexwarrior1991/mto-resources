package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteRequest;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteResponse;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteUpdateRequest;
import com.alejandro.mtoresources.application.service.ParkingSiteService;
import com.alejandro.mtoresources.configuration.security.SecurityRoles;
import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSiteKind;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping(ResourcesApiPaths.PARKING_SITES)
@RequiredArgsConstructor
@Tag(name = "Parking Sites")
public class ParkingSiteController {

    private final ParkingSiteService siteService;

    @Operation(summary = "Create parking site", description = "A place where it is known a vehicle can be left: a siding, a station "
            + "track, a depot, a road car park. Optional: a vehicle is parked wherever it can be, and a track and kp or a description "
            + "say it as well.")
    @PostMapping
    public ResponseEntity<ParkingSiteResponse> create(@Valid @RequestBody ParkingSiteRequest request) {
        ParkingSiteResponse response = siteService.create(request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.PARKING_SITES + "/" + response.id())).body(response);
    }

    @Operation(summary = "Update parking site")
    @PreAuthorize("!#request.deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PutMapping("/{id}")
    public ResponseEntity<ParkingSiteResponse> update(@PathVariable UUID id, @Valid @RequestBody ParkingSiteUpdateRequest request) {
        return ResponseEntity.ok(siteService.update(id, request));
    }

    @Operation(summary = "Patch parking site", description = "application/merge-patch+json: null empties the optional fields.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = ParkingSiteUpdateRequest.class))))
    @PreAuthorize("!#patch.values().deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PatchMapping(value = "/{id}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<ParkingSiteResponse> patch(@PathVariable UUID id,
                                                     @Parameter(hidden = true) @Valid MergePatch<ParkingSiteUpdateRequest> patch) {
        return ResponseEntity.ok(siteService.patch(id, patch));
    }

    @Operation(summary = "Get parking site")
    @GetMapping("/{id}")
    public ResponseEntity<ParkingSiteResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(siteService.findById(id));
    }

    @Operation(summary = "Search parking sites")
    @GetMapping
    public ResponseEntity<PageResponse<ParkingSiteResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ParkingSiteKind kind,
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Boolean railAccess,
            @RequestParam(required = false) Boolean roadAccess,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = {"code", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(siteService.search(search, kind, executionPackageId, trackId, railAccess, roadAccess, active, pageable));
    }

    @Operation(summary = "Retire parking site", description = "Out of the catalogue; the locations recorded there keep naming it.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        siteService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
