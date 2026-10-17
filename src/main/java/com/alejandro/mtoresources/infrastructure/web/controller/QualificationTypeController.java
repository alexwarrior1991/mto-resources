package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeRequest;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeResponse;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeUpdateRequest;
import com.alejandro.mtoresources.application.service.QualificationTypeService;
import com.alejandro.mtoresources.configuration.security.SecurityRoles;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;
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
import java.util.UUID;

@Validated
@RestController
@RequestMapping(ResourcesApiPaths.QUALIFICATION_TYPES)
@RequiredArgsConstructor
@Tag(name = "Qualification Types")
public class QualificationTypeController {

    private final QualificationTypeService typeService;

    @Operation(summary = "Create qualification type", description = "The catalogue the qualifications of every person come from, and "
            + "what a vehicle model asks of its driver. drivingLicence if and only if kind is DRIVING_LICENCE; validityMonths is the "
            + "default validity of a qualification that does not say its expiry. 409 QTY-409 for a repeated code.")
    @PostMapping
    public ResponseEntity<QualificationTypeResponse> create(@Valid @RequestBody QualificationTypeRequest request) {
        QualificationTypeResponse response = typeService.create(request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.QUALIFICATION_TYPES + "/" + response.id())).body(response);
    }

    @Operation(summary = "Update qualification type", description = "A key left out is not touched; the code never changes. Another kind "
            + "than DRIVING_LICENCE drops the driving licence. Changing the kind or the licence republishes the people who hold it. "
            + "active=false retires it, like DELETE, and needs the delete role too.")
    @PreAuthorize("!#request.deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PutMapping("/{id}")
    public ResponseEntity<QualificationTypeResponse> update(@PathVariable UUID id, @Valid @RequestBody QualificationTypeUpdateRequest request) {
        return ResponseEntity.ok(typeService.update(id, request));
    }

    @Operation(summary = "Patch qualification type", description = "application/merge-patch+json: null empties drivingLicence, "
            + "validityMonths or description. With version, 409 CON-001 if it changed since it was read.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = QualificationTypeUpdateRequest.class))))
    @PreAuthorize("!#patch.values().deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PatchMapping(value = "/{id}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<QualificationTypeResponse> patch(@PathVariable UUID id,
                                                           @Parameter(hidden = true) @Valid MergePatch<QualificationTypeUpdateRequest> patch) {
        return ResponseEntity.ok(typeService.patch(id, patch));
    }

    @Operation(summary = "Get qualification type")
    @GetMapping("/{id}")
    public ResponseEntity<QualificationTypeResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(typeService.findById(id));
    }

    @Operation(summary = "Search qualification types", description = "search matches the code or the name.")
    @GetMapping
    public ResponseEntity<PageResponse<QualificationTypeResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) QualificationKind kind,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = {"code", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(typeService.search(search, kind, active, pageable));
    }

    @Operation(summary = "Retire qualification type", description = "Out of the catalogue: what every person already has stays, but no "
            + "new one is given (409 QUA-001).")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        typeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
