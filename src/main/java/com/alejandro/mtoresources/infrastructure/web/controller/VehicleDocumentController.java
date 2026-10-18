package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentUpdateRequest;
import com.alejandro.mtoresources.application.service.VehicleDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping(ResourcesApiPaths.VEHICLE_DOCUMENTS)
@RequiredArgsConstructor
@Tag(name = "Vehicle Documents")
public class VehicleDocumentController {

    private final VehicleDocumentService documentService;

    @Operation(summary = "Documents of a vehicle", description = "All of them, by the name of their kind, the expired ones too (expired: "
            + "their expiry is before today).")
    @GetMapping
    public ResponseEntity<List<VehicleDocumentResponse>> findAll(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok(documentService.findAll(vehicleId));
    }

    @Operation(summary = "Add a document to a vehicle", description = "One per kind (409 VDO-409 otherwise), always with its expiry; an "
            + "expiry before the issue is 400 VAL-001. Publishes vehicle.updated with all of them (availabilityChanged=false).")
    @PostMapping
    public ResponseEntity<VehicleDocumentResponse> create(@PathVariable UUID vehicleId, @Valid @RequestBody VehicleDocumentRequest request) {
        VehicleDocumentResponse response = documentService.create(vehicleId, request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.VEHICLES + "/" + vehicleId + "/documents/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Patch a document", description = "application/merge-patch+json: renewing it is changing its expiry; null "
            + "empties reference, issuedOn or notes, never expiresOn. With version, 409 CON-001 if it changed since it was read. "
            + "Publishes vehicle.updated.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = VehicleDocumentUpdateRequest.class))))
    @PatchMapping(value = "/{documentId}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<VehicleDocumentResponse> patch(@PathVariable UUID vehicleId, @PathVariable UUID documentId,
                                                         @Parameter(hidden = true) @Valid MergePatch<VehicleDocumentUpdateRequest> patch) {
        return ResponseEntity.ok(documentService.patch(vehicleId, documentId, patch));
    }

    @Operation(summary = "Remove a document", description = "A physical delete, for one given by mistake; the history keeps it. "
            + "Publishes vehicle.updated.")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID vehicleId, @PathVariable UUID documentId) {
        documentService.delete(vehicleId, documentId);
        return ResponseEntity.noContent().build();
    }
}
