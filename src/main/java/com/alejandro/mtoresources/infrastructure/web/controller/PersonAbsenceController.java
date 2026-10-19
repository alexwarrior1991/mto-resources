package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceRequest;
import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceResponse;
import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceUpdateRequest;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.service.PersonAbsenceService;
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
@RequestMapping(ResourcesApiPaths.PERSON_ABSENCES)
@RequiredArgsConstructor
@Tag(name = "Person Absences")
public class PersonAbsenceController {

    private final PersonAbsenceService absenceService;

    @Operation(summary = "Absences of a person", description = "All of them, the most recent first.")
    @GetMapping
    public ResponseEntity<List<PersonAbsenceResponse>> findAll(@PathVariable UUID personId) {
        return ResponseEntity.ok(absenceService.findAll(personId));
    }

    @Operation(summary = "Record an absence", description = "Both dates included. One that overlaps another of the person is 409 "
            + "ABS-001; an end before the start, 400 VAL-001. Publishes person.updated with its unavailable periods (never their kind).")
    @PostMapping
    public ResponseEntity<PersonAbsenceResponse> create(@PathVariable UUID personId, @Valid @RequestBody PersonAbsenceRequest request) {
        PersonAbsenceResponse response = absenceService.create(personId, request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.PEOPLE + "/" + personId + "/absences/" + response.id())).body(response);
    }

    @Operation(summary = "Patch an absence", description = "application/merge-patch+json: only the notes can be emptied. With version, "
            + "409 CON-001 if it changed since it was read. Publishes person.updated.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = PersonAbsenceUpdateRequest.class))))
    @PatchMapping(value = "/{absenceId}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<PersonAbsenceResponse> patch(@PathVariable UUID personId, @PathVariable UUID absenceId,
                                                       @Parameter(hidden = true) @Valid MergePatch<PersonAbsenceUpdateRequest> patch) {
        return ResponseEntity.ok(absenceService.patch(personId, absenceId, patch));
    }

    @Operation(summary = "Remove an absence", description = "A physical delete, for one recorded by mistake or called off; the "
            + "history keeps it. Publishes person.updated.")
    @DeleteMapping("/{absenceId}")
    public ResponseEntity<Void> delete(@PathVariable UUID personId, @PathVariable UUID absenceId) {
        absenceService.delete(personId, absenceId);
        return ResponseEntity.noContent().build();
    }
}
