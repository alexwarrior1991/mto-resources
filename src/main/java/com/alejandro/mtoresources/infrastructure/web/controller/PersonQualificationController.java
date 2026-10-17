package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationRequest;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationResponse;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationUpdateRequest;
import com.alejandro.mtoresources.application.service.PersonQualificationService;
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
@RequestMapping(ResourcesApiPaths.PERSON_QUALIFICATIONS)
@RequiredArgsConstructor
@Tag(name = "Person Qualifications")
public class PersonQualificationController {

    private final PersonQualificationService qualificationService;

    @Operation(summary = "Qualifications of a person", description = "All of them, by the code of their type, the expired ones too "
            + "(expired: their expiry is before today).")
    @GetMapping
    public ResponseEntity<List<PersonQualificationResponse>> findAll(@PathVariable UUID personId) {
        return ResponseEntity.ok(qualificationService.findAll(personId));
    }

    @Operation(summary = "Give a qualification to a person", description = "One per type, of an active type (409 QUA-001 otherwise). "
            + "Without expiresOn, issuedOn plus the validityMonths of the type, if it has one; otherwise it does not expire. An expiry "
            + "before the issue is 400 VAL-001. Publishes person.updated with all of them.")
    @PostMapping
    public ResponseEntity<PersonQualificationResponse> create(@PathVariable UUID personId,
                                                              @Valid @RequestBody PersonQualificationRequest request) {
        PersonQualificationResponse response = qualificationService.create(personId, request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.PEOPLE + "/" + personId + "/qualifications/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Patch a qualification", description = "application/merge-patch+json: renewing it is changing its expiry; null "
            + "empties anything but the version (expiresOn: null, it no longer expires). With version, 409 CON-001 if it changed since "
            + "it was read. Publishes person.updated.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = PersonQualificationUpdateRequest.class))))
    @PatchMapping(value = "/{qualificationId}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<PersonQualificationResponse> patch(@PathVariable UUID personId, @PathVariable UUID qualificationId,
                                                             @Parameter(hidden = true) @Valid MergePatch<PersonQualificationUpdateRequest> patch) {
        return ResponseEntity.ok(qualificationService.patch(personId, qualificationId, patch));
    }

    @Operation(summary = "Remove a qualification", description = "A physical delete, for one given by mistake; the history keeps it. "
            + "Publishes person.updated.")
    @DeleteMapping("/{qualificationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID personId, @PathVariable UUID qualificationId) {
        qualificationService.delete(personId, qualificationId);
        return ResponseEntity.noContent().build();
    }
}
