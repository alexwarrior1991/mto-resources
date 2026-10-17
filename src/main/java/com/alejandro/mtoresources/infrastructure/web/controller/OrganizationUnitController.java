package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.organization.OrganizationTreeNode;
import com.alejandro.mtoresources.application.dto.organization.OrganizationUnitRequest;
import com.alejandro.mtoresources.application.dto.organization.OrganizationUnitResponse;
import com.alejandro.mtoresources.application.dto.organization.OrganizationUnitUpdateRequest;
import com.alejandro.mtoresources.application.dto.organization.TeamMemberLeaveRequest;
import com.alejandro.mtoresources.application.dto.organization.TeamMemberRequest;
import com.alejandro.mtoresources.application.dto.organization.TeamMemberResponse;
import com.alejandro.mtoresources.application.dto.organization.TeamMembersRequest;
import com.alejandro.mtoresources.application.service.OrganizationUnitService;
import com.alejandro.mtoresources.configuration.security.SecurityRoles;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;
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
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(ResourcesApiPaths.UNITS)
@RequiredArgsConstructor
@Tag(name = "Organization")
public class OrganizationUnitController {

    private final OrganizationUnitService unitService;

    @Operation(summary = "Create organization unit", description = "A unit hangs from one of a strictly higher level (project, department, "
            + "chief supervision, supervision, team) and is led by a person of the category of its level or above. A team is created "
            + "complete: its team leader (headPersonId) and 2 to 6 linemen (memberPersonIds); 400 otherwise.")
    @PostMapping
    public ResponseEntity<OrganizationUnitResponse> create(@Valid @RequestBody OrganizationUnitRequest request) {
        OrganizationUnitResponse response = unitService.create(request);
        return ResponseEntity.created(URI.create(ResourcesApiPaths.UNITS + "/" + response.id())).body(response);
    }

    @Operation(summary = "Update organization unit", description = "A key left out is not touched. A team's leader is replaced, never "
            + "emptied. Changing a head or a parent republishes every team under it. active=false deactivates, like DELETE, and needs "
            + "the delete role too; active=true brings a team back with its linemen (memberPersonIds).")
    @PreAuthorize("!#request.deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PutMapping("/{id}")
    public ResponseEntity<OrganizationUnitResponse> update(@PathVariable UUID id, @Valid @RequestBody OrganizationUnitUpdateRequest request) {
        return ResponseEntity.ok(unitService.update(id, request));
    }

    @Operation(summary = "Patch organization unit", description = "application/merge-patch+json: null empties headPersonId (not in a team), "
            + "executionPackageIds or defaultVehicleId. With version, 409 CON-001 if it changed since it was read.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = OrganizationUnitUpdateRequest.class))))
    @PreAuthorize("!#patch.values().deactivating or hasRole('" + SecurityRoles.RESOURCES_DELETE + "')")
    @PatchMapping(value = "/{id}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<OrganizationUnitResponse> patch(@PathVariable UUID id,
                                                          @Parameter(hidden = true) @Valid MergePatch<OrganizationUnitUpdateRequest> patch) {
        return ResponseEntity.ok(unitService.patch(id, patch));
    }

    @Operation(summary = "Get organization unit", description = "A team brings its linemen, whether it is complete, and the supervisor "
            + "and chief supervisor above it.")
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationUnitResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(unitService.findById(id));
    }

    @Operation(summary = "Search organization units")
    @GetMapping
    public ResponseEntity<PageResponse<OrganizationUnitResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrganizationUnitKind kind,
            @RequestParam(required = false) WorkDiscipline discipline,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) UUID headPersonId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = {"code", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(unitService.search(search, kind, discipline, parentId, executionPackageId, headPersonId, active, pageable));
    }

    @Operation(summary = "Organization tree", description = "The whole tree from each project, with the head of every unit and the "
            + "linemen of every team. The organization is small: tens of units, not thousands.")
    @GetMapping("/tree")
    public ResponseEntity<List<OrganizationTreeNode>> tree(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(unitService.tree(includeInactive));
    }

    @Operation(summary = "Deactivate organization unit", description = "409 UNI-001 while it has active units under it. A team releases "
            + "its linemen today.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        unitService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Linemen of a team", description = "The open ones; includeFormer adds those who left, with their dates.")
    @GetMapping("/{id}/members")
    public ResponseEntity<List<TeamMemberResponse>> members(@PathVariable UUID id,
                                                            @RequestParam(defaultValue = "false") boolean includeFormer) {
        return ResponseEntity.ok(unitService.members(id, includeFormer));
    }

    @Operation(summary = "Replace the linemen of a team", description = "The whole list, 2 to 6: those missing leave and the new ones join "
            + "on effectiveOn (today by default). It is how several change at once without going below 2 or above 6.")
    @PutMapping("/{id}/members")
    public ResponseEntity<OrganizationUnitResponse> replaceMembers(@PathVariable UUID id, @Valid @RequestBody TeamMembersRequest request) {
        return ResponseEntity.ok(unitService.replaceMembers(id, request));
    }

    @Operation(summary = "Add a lineman", description = "409 UNI-001 if the team already has 6, or the person is a lineman or a leader "
            + "of another team.")
    @PostMapping("/{id}/members")
    public ResponseEntity<OrganizationUnitResponse> addMember(@PathVariable UUID id, @Valid @RequestBody TeamMemberRequest request) {
        return ResponseEntity.ok(unitService.addMember(id, request));
    }

    @Operation(summary = "A lineman leaves", description = "409 UNI-001 if the team would be left with fewer than 2: add another one first "
            + "or replace the list in one go.")
    @PostMapping("/{id}/members/{personId}/leave")
    public ResponseEntity<OrganizationUnitResponse> removeMember(@PathVariable UUID id, @PathVariable UUID personId,
                                                                 @Valid @RequestBody(required = false) TeamMemberLeaveRequest request) {
        return ResponseEntity.ok(unitService.removeMember(id, personId, request));
    }

    @Operation(summary = "Organization unit change history", description = "Envers revisions, the most recent first; the linemen keep "
            + "their own history in /members?includeFormer=true.")
    @GetMapping("/{id}/revisions")
    public ResponseEntity<PageResponse<EntityRevisionResponse<OrganizationUnitResponse>>> revisions(@PathVariable UUID id,
                                                                                                    @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(unitService.findRevisions(id, pageable));
    }
}
