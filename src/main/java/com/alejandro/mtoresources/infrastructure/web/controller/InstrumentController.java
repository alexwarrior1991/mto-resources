package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentRequest;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentResponse;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentUpdateRequest;
import com.alejandro.mtoresources.application.service.InstrumentService;
import com.alejandro.mtoresources.configuration.security.SecurityRoles;
import com.alejandro.mtoresources.infrastructure.persistence.entity.InstrumentKind;
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
import java.time.LocalDate;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(ResourcesApiPaths.INSTRUMENTS)
@RequiredArgsConstructor
@Tag(name = "Instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    @Operation(summary = "Create instrument", description = "A calibrated instrument taken to the work site: an earthing pole, a voltage "
            + "detector, a measuring device. mto-maintenance picks them in a shift. Without calibrationExpiresOn it has no calibration "
            + "that expires; an expiry before the calibration is 400 VAL-001; a repeated code, 409 INT-409.")
    @PostMapping
    public ResponseEntity<InstrumentResponse> create(@Valid @RequestBody InstrumentRequest request) {
        InstrumentResponse response = instrumentService.create(request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.INSTRUMENTS + "/" + response.id())).body(response);
    }

    @Operation(summary = "Update instrument", description = "A key left out is not touched; the code never changes. active=false "
            + "retires it, like DELETE, and needs the delete role too.")
    @PreAuthorize("!#request.deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PutMapping("/{id}")
    public ResponseEntity<InstrumentResponse> update(@PathVariable UUID id, @Valid @RequestBody InstrumentUpdateRequest request) {
        return ResponseEntity.ok(instrumentService.update(id, request));
    }

    @Operation(summary = "Patch instrument", description = "application/merge-patch+json: null empties serialNumber, calibratedOn, "
            + "calibrationExpiresOn or notes. With version, 409 CON-001 if it changed since it was read.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = InstrumentUpdateRequest.class))))
    @PreAuthorize("!#patch.values().deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PatchMapping(value = "/{id}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<InstrumentResponse> patch(@PathVariable UUID id,
                                                    @Parameter(hidden = true) @Valid MergePatch<InstrumentUpdateRequest> patch) {
        return ResponseEntity.ok(instrumentService.patch(id, patch));
    }

    @Operation(summary = "Get instrument", description = "calibrationExpired: its calibration expired before today.")
    @GetMapping("/{id}")
    public ResponseEntity<InstrumentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(instrumentService.findById(id));
    }

    @Operation(summary = "Search instruments", description = "search matches the code, the name or the serial number; "
            + "calibrationExpiresBefore (ISO date), the ones with a calibration that expires before that day.")
    @GetMapping
    public ResponseEntity<PageResponse<InstrumentResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InstrumentKind kind,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate calibrationExpiresBefore,
            @PageableDefault(size = 20, sort = {"code", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(instrumentService.search(search, kind, active, calibrationExpiresBefore, pageable));
    }

    @Operation(summary = "Retire instrument", description = "Never deleted: the shifts name it.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        instrumentService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Instrument change history", description = "Envers revisions, the most recent first.")
    @GetMapping("/{id}/revisions")
    public ResponseEntity<PageResponse<EntityRevisionResponse<InstrumentResponse>>> revisions(@PathVariable UUID id,
                                                                                              @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(instrumentService.findRevisions(id, pageable));
    }
}
