package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.person.MeResponse;
import com.alejandro.mtoresources.application.dto.person.PersonRequest;
import com.alejandro.mtoresources.application.dto.person.PersonResponse;
import com.alejandro.mtoresources.application.dto.person.PersonUpdateRequest;
import com.alejandro.mtoresources.application.service.PersonService;
import com.alejandro.mtoresources.configuration.security.SecurityRoles;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "People")
public class PersonController {

    private final PersonService personService;

    @Operation(summary = "Create person", description = "Every person has a record, with or without a Keycloak user: username is their "
            + "preferred_username when they log in. The code (PER-000001) comes from a sequence.")
    @PostMapping(ResourcesApiPaths.PEOPLE)
    public ResponseEntity<PersonResponse> create(@Valid @RequestBody PersonRequest request) {
        PersonResponse response = personService.create(request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.PEOPLE + "/" + response.id())).body(response);
    }

    @Operation(summary = "Update person", description = "A key left out is not touched. A new category has to fit what the person leads "
            + "and the team they are a lineman of (409 UNI-001). active=false deactivates, like DELETE, and needs the delete role too.")
    @PreAuthorize("!#request.deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PutMapping(ResourcesApiPaths.PEOPLE + "/{id}")
    public ResponseEntity<PersonResponse> update(@PathVariable UUID id, @Valid @RequestBody PersonUpdateRequest request) {
        return ResponseEntity.ok(personService.update(id, request));
    }

    @Operation(summary = "Patch person", description = "application/merge-patch+json: null empties employeeNumber, username, "
            + "keycloakUserId, company or phone. With version, 409 CON-001 if it changed since it was read.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = PersonUpdateRequest.class))))
    @PreAuthorize("!#patch.values().deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PatchMapping(value = ResourcesApiPaths.PEOPLE + "/{id}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<PersonResponse> patch(@PathVariable UUID id,
                                                @Parameter(hidden = true) @Valid MergePatch<PersonUpdateRequest> patch) {
        return ResponseEntity.ok(personService.patch(id, patch));
    }

    @Operation(summary = "Get person")
    @GetMapping(ResourcesApiPaths.PEOPLE + "/{id}")
    public ResponseEntity<PersonResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(personService.findById(id));
    }

    @Operation(summary = "Search people", description = "search matches the code, the name, the username or the employee number; "
            + "unitId, who leads that unit or is one of its linemen now.")
    @GetMapping(ResourcesApiPaths.PEOPLE)
    public ResponseEntity<PageResponse<PersonResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PersonCategory category,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = {"lastName", "firstName", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(personService.search(search, category, unitId, active, pageable));
    }

    @Operation(summary = "Deactivate person", description = "Never deleted: shifts and teams name them. Someone who leads a unit is not "
            + "deactivated until it has another head; a lineman leaves their team today, unless it would drop below 2 (409 UNI-001).")
    @DeleteMapping(ResourcesApiPaths.PEOPLE + "/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        personService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Person change history", description = "Envers revisions, the most recent first.")
    @GetMapping(ResourcesApiPaths.PEOPLE + "/{id}/revisions")
    public ResponseEntity<PageResponse<EntityRevisionResponse<PersonResponse>>> revisions(@PathVariable UUID id,
                                                                                          @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(personService.findRevisions(id, pageable));
    }

    @Operation(summary = "Me", description = "The person of the token (by preferred_username), the units they lead or are a lineman of, "
            + "and the execution packages of those units and of everything under them. 404 PER-404 when no record has the username.")
    @GetMapping(ResourcesApiPaths.ME)
    public ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(personService.me());
    }
}
