package com.alejandro.mtoresources.application.service.impl;

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
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.FleetRuleException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.OrganizationRuleException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.AuditableMapper;
import com.alejandro.mtoresources.application.mapper.OrganizationUnitMapper;
import com.alejandro.mtoresources.application.mapper.PageMapper;
import com.alejandro.mtoresources.application.mapper.PersonMapper;
import com.alejandro.mtoresources.application.service.EntityAuditService;
import com.alejandro.mtoresources.application.service.OrganizationUnitService;
import com.alejandro.mtoresources.domain.model.CrewComposition;
import com.alejandro.mtoresources.domain.model.OrganizationRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.TeamMember;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;
import com.alejandro.mtoresources.infrastructure.persistence.repository.OrganizationUnitRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.TeamMemberRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.OrganizationUnitSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class OrganizationUnitServiceImpl implements OrganizationUnitService {

    private static final Set<String> CLEARABLE = Set.of("headPersonId", "executionPackageIds", "defaultVehicleId");

    private final OrganizationUnitRepository repository;
    private final TeamMemberRepository memberRepository;
    private final OrganizationLookups lookups;
    private final OrganizationUnitMapper mapper;
    private final PersonMapper personMapper;
    private final AuditableMapper auditableMapper;
    private final EntityAuditService auditService;
    private final Clock clock;

    // ------------------------------------------------------------------------------ alta

    @Override
    @Transactional
    public OrganizationUnitResponse create(OrganizationUnitRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Organization unit", code);
        }
        OrganizationUnitKind kind = request.kind();
        OrganizationUnit parent = request.parentId() == null ? null : lookups.unit(request.parentId());
        requireParent(kind, parent);
        WorkDiscipline discipline = discipline(kind, request.discipline(), parent);

        Person head = request.headPersonId() == null ? null : lookups.person(request.headPersonId());
        boolean team = kind == OrganizationUnitKind.TEAM;
        if (team && head == null) {
            throw new ValidationException("A team is created with its team leader (headPersonId): a team always works with one");
        }
        if (head != null) {
            requireHead(kind, discipline, head, null);
        }
        List<Person> members = List.of();
        if (team) {
            members = newMembers("Team " + code, head, request.memberPersonIds(), Set.of(), null);
            CrewComposition.completeViolation("Team " + code, true, members.size()).ifPresent(reason -> {
                throw new ValidationException(reason);
            });
        } else if (request.memberPersonIds() != null && !request.memberPersonIds().isEmpty()) {
            throw new ValidationException("Only a team has linemen; a " + label(kind) + " has a head and the units under it");
        }

        OrganizationUnit unit = OrganizationUnit.builder()
                .code(code)
                .name(PatchRules.requireText(request.name(), "name"))
                .kind(kind)
                .discipline(discipline)
                .parent(parent)
                .head(head)
                .defaultVehicle(defaultVehicle(kind, request.defaultVehicleId()))
                .executionPackageIds(new LinkedHashSet<>(request.executionPackageIds() == null ? Set.of() : request.executionPackageIds()))
                .build();
        OrganizationUnit saved = repository.save(unit);

        LocalDate joinedOn = request.joinedOn() == null ? LocalDate.now(clock) : request.joinedOn();
        for (Person member : members) {
            memberRepository.save(TeamMember.builder().team(saved).person(member).joinedOn(joinedOn).build());
        }
        if (team) {
            lookups.publishTeams(List.of(saved), ResourcesEvents.CREATED);
        }
        return toResponse(saved);
    }

    // ---------------------------------------------------------------------- modificacion

    @Override
    @Transactional
    public OrganizationUnitResponse update(UUID id, OrganizationUnitUpdateRequest request) {
        return patch(id, MergePatch.of(request));
    }

    @Override
    @Transactional
    public OrganizationUnitResponse patch(UUID id, MergePatch<OrganizationUnitUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        OrganizationUnitUpdateRequest values = patch.values();
        OrganizationUnit unit = lookups.lockedUnit(id);
        StaleVersionException.check("Organization unit " + unit.getCode(), unit.getVersion(), values.version());

        if (values.isDeactivating()) {
            deactivate(unit);
            return toResponse(unit);
        }
        boolean reactivating = Boolean.TRUE.equals(values.active()) && !Boolean.TRUE.equals(unit.getActive());
        if (values.memberPersonIds() != null && !(reactivating && unit.isTeam())) {
            throw new ValidationException("The linemen of a team change through /units/{id}/members; memberPersonIds only travel "
                    + "to reactivate a team");
        }

        boolean parentChanges = values.parentId() != null
                && (unit.getParent() == null || !values.parentId().equals(unit.getParent().getId()));
        boolean headChanges = patch.clears("headPersonId")
                || (values.headPersonId() != null && (unit.getHead() == null || !values.headPersonId().equals(unit.getHead().getId())));

        // Los equipos cuyo evento cambia: el propio, y los de debajo si cambia su jefe o de donde cuelga.
        Set<UUID> affected = new LinkedHashSet<>();
        if (unit.isTeam()) {
            affected.add(unit.getId());
        }
        if (parentChanges || headChanges) {
            affected.addAll(lookups.teamsUnder(List.of(unit.getId())));
        }
        List<OrganizationUnit> teams = lookups.lockTeams(affected);

        if (values.name() != null) {
            unit.setName(PatchRules.requireText(values.name(), "name"));
        }
        if (parentChanges) {
            moveUnder(unit, lookups.unit(values.parentId()));
        }
        if (patch.clears("headPersonId")) {
            if (unit.isTeam()) {
                throw new ValidationException("A team always works with its team leader: replace them with headPersonId, do not empty it");
            }
            unit.setHead(null);
        } else if (headChanges) {
            Person head = lookups.person(values.headPersonId());
            requireHead(unit.getKind(), unit.getDiscipline(), head, unit.getId());
            unit.setHead(head);
        }
        if (values.executionPackageIds() != null || patch.clears("executionPackageIds")) {
            unit.getExecutionPackageIds().clear();
            if (values.executionPackageIds() != null) {
                unit.getExecutionPackageIds().addAll(new TreeSet<>(values.executionPackageIds()));
            }
        }
        if (patch.clears("defaultVehicleId")) {
            unit.setDefaultVehicle(null);
        } else if (values.defaultVehicleId() != null) {
            unit.setDefaultVehicle(defaultVehicle(unit.getKind(), values.defaultVehicleId()));
        }
        if (reactivating) {
            reactivate(unit, values);
        }

        OrganizationUnit saved = repository.saveAndFlush(unit);
        lookups.publishActiveTeamsUpdated(teams);
        return toResponse(saved);
    }

    /** Un equipo desactivado solto a sus linemen: vuelve como nacio, con su jefe y de 2 a 6. */
    private void reactivate(OrganizationUnit unit, OrganizationUnitUpdateRequest values) {
        if (unit.getParent() != null && !Boolean.TRUE.equals(unit.getParent().getActive())) {
            throw new OrganizationRuleException(unit.getCode() + " hangs from " + unit.getParent().getCode()
                    + ", which is deactivated: reactivate it first or move " + unit.getCode() + " elsewhere");
        }
        if (unit.isTeam()) {
            if (values.memberPersonIds() == null) {
                throw new ValidationException("A team comes back complete: reactivate it with its linemen (memberPersonIds, "
                        + CrewComposition.MIN_LINEMEN + " to " + CrewComposition.MAX_LINEMEN + ")");
            }
            requireHead(unit.getKind(), unit.getDiscipline(), unit.getHead(), unit.getId());
            List<Person> members = newMembers("Team " + unit.getCode(), unit.getHead(), values.memberPersonIds(), Set.of(), unit.getId());
            CrewComposition.completeViolation("Team " + unit.getCode(), true, members.size()).ifPresent(reason -> {
                throw new ValidationException(reason);
            });
            LocalDate joinedOn = values.joinedOn() == null ? LocalDate.now(clock) : values.joinedOn();
            for (Person member : members) {
                memberRepository.save(TeamMember.builder().team(unit).person(member).joinedOn(joinedOn).build());
            }
        }
        unit.setActive(true);
    }

    private void moveUnder(OrganizationUnit unit, OrganizationUnit parent) {
        if (unit.getKind() == OrganizationUnitKind.PROJECT) {
            throw new OrganizationRuleException("A project is the root of the organization: it has no parent");
        }
        requireParent(unit.getKind(), parent);
        WorkDiscipline inherited = OrganizationLookups.nearestWithDiscipline(parent).map(OrganizationUnit::getDiscipline).orElse(null);
        if (inherited != null && unit.getDiscipline() != null && inherited != unit.getDiscipline()) {
            throw new OrganizationRuleException(unit.getCode() + " is of " + unit.getDiscipline() + " and " + parent.getCode()
                    + " of " + inherited + ": a unit does not change discipline");
        }
        unit.setParent(parent);
    }

    // -------------------------------------------------------------------------------- baja

    @Override
    @Transactional
    public void deactivate(UUID id) {
        deactivate(lookups.lockedUnit(id));
    }

    private void deactivate(OrganizationUnit unit) {
        if (!Boolean.TRUE.equals(unit.getActive())) {
            return;
        }
        if (repository.existsByParentIdAndActiveTrue(unit.getId())) {
            throw new OrganizationRuleException(unit.getCode() + " has active units under it: deactivate or move them first");
        }
        if (unit.isTeam()) {
            LocalDate today = LocalDate.now(clock);
            for (TeamMember member : memberRepository.findOpenByTeam(unit.getId())) {
                member.setLeftOn(today.isBefore(member.getJoinedOn()) ? member.getJoinedOn() : today);
            }
        }
        unit.setActive(false);
        repository.saveAndFlush(unit);
        if (unit.isTeam()) {
            lookups.publishTeams(List.of(unit), ResourcesEvents.DEACTIVATED);
        }
    }

    // ---------------------------------------------------------------------------- linemen

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> members(UUID teamId, boolean includeFormer) {
        OrganizationUnit team = lookups.unit(teamId);
        requireTeam(team);
        List<TeamMember> members = includeFormer ? memberRepository.findAllByTeam(teamId) : memberRepository.findOpenByTeam(teamId);
        return members.stream().map(mapper::toMemberResponse).toList();
    }

    @Override
    @Transactional
    public OrganizationUnitResponse replaceMembers(UUID teamId, TeamMembersRequest request) {
        OrganizationUnit team = activeTeam(teamId);
        String label = "Team " + team.getCode();
        CrewComposition.completeViolation(label, team.getHead() != null, request.memberPersonIds().size()).ifPresent(reason -> {
            throw new ValidationException(reason);
        });
        LocalDate date = request.effectiveOn() == null ? LocalDate.now(clock) : request.effectiveOn();
        List<TeamMember> current = memberRepository.findOpenByTeam(teamId);
        Set<UUID> currentIds = new HashSet<>();
        current.forEach(member -> currentIds.add(member.getPerson().getId()));
        Set<UUID> wanted = new LinkedHashSet<>(request.memberPersonIds());

        List<Person> joining = newMembers(label, team.getHead(), request.memberPersonIds(), currentIds, teamId);
        for (TeamMember member : current) {
            if (!wanted.contains(member.getPerson().getId())) {
                leave(member, date);
            }
        }
        memberRepository.flush();
        for (Person person : joining) {
            memberRepository.save(TeamMember.builder().team(team).person(person).joinedOn(date).build());
        }
        lookups.publishTeams(List.of(team), ResourcesEvents.UPDATED);
        return toResponse(team);
    }

    @Override
    @Transactional
    public OrganizationUnitResponse addMember(UUID teamId, TeamMemberRequest request) {
        OrganizationUnit team = activeTeam(teamId);
        String label = "Team " + team.getCode();
        CrewComposition.addViolation(label, memberRepository.countOpenByTeam(teamId)).ifPresent(reason -> {
            throw new OrganizationRuleException(reason);
        });
        Person person = newMembers(label, team.getHead(), List.of(request.personId()), Set.of(), teamId).getFirst();
        LocalDate joinedOn = request.joinedOn() == null ? LocalDate.now(clock) : request.joinedOn();
        memberRepository.save(TeamMember.builder().team(team).person(person).joinedOn(joinedOn).build());
        lookups.publishTeams(List.of(team), ResourcesEvents.UPDATED);
        return toResponse(team);
    }

    @Override
    @Transactional
    public OrganizationUnitResponse removeMember(UUID teamId, UUID personId, TeamMemberLeaveRequest request) {
        OrganizationUnit team = activeTeam(teamId);
        List<TeamMember> current = memberRepository.findOpenByTeam(teamId);
        TeamMember member = current.stream().filter(candidate -> candidate.getPerson().getId().equals(personId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Organization unit",
                        "Person " + personId + " is not a lineman of team " + team.getCode()));
        CrewComposition.removeViolation("Team " + team.getCode(), current.size()).ifPresent(reason -> {
            throw new OrganizationRuleException(reason);
        });
        leave(member, request == null || request.leftOn() == null ? LocalDate.now(clock) : request.leftOn());
        lookups.publishTeams(List.of(team), ResourcesEvents.UPDATED);
        return toResponse(team);
    }

    private static void leave(TeamMember member, LocalDate leftOn) {
        if (leftOn.isBefore(member.getJoinedOn())) {
            throw new ValidationException(member.getPerson().fullName() + " joined on " + member.getJoinedOn()
                    + ": they cannot leave on " + leftOn);
        }
        member.setLeftOn(leftOn);
    }

    /**
     * Los que entran en un equipo, comprobados: activos, operarios o jefes de equipo, que no sean el
     * jefe de este, ni linemen de otro equipo, ni jefes de otro equipo activo. Los que ya estaban
     * ({@code alreadyIn}) no se vuelven a comprobar ni se devuelven.
     */
    private List<Person> newMembers(String team, Person head, Collection<UUID> personIds, Set<UUID> alreadyIn, UUID teamId) {
        if (personIds == null || personIds.isEmpty()) {
            throw new ValidationException(team + " needs its linemen (memberPersonIds, " + CrewComposition.MIN_LINEMEN
                    + " to " + CrewComposition.MAX_LINEMEN + ")");
        }
        Set<UUID> distinct = new LinkedHashSet<>(personIds);
        if (distinct.size() != personIds.size()) {
            throw new ValidationException("A person appears twice among the linemen of " + team);
        }
        List<Person> joining = new ArrayList<>();
        for (UUID personId : distinct) {
            if (head != null && head.getId().equals(personId)) {
                throw new ValidationException(head.fullName() + " leads " + team + ": the team leader is not one of its linemen");
            }
            if (alreadyIn.contains(personId)) {
                continue;
            }
            Person person = lookups.person(personId);
            if (!Boolean.TRUE.equals(person.getActive())) {
                throw new OrganizationRuleException(person.fullName() + " is deactivated");
            }
            if (!OrganizationRules.canBeLineman(person.getCategory())) {
                throw new OrganizationRuleException("A lineman is a WORKER or a TEAM_LEADER; " + person.fullName() + " is a "
                        + person.getCategory());
            }
            memberRepository.findOpenByPerson(personId).ifPresent(membership -> {
                if (!membership.getTeam().getId().equals(teamId)) {
                    throw new OrganizationRuleException(person.fullName() + " is already a lineman of team "
                            + membership.getTeam().getCode() + ": take them out of it first");
                }
            });
            repository.findByHeadIdAndActiveTrue(personId).stream()
                    .filter(OrganizationUnit::isTeam)
                    .filter(other -> !other.getId().equals(teamId))
                    .findFirst()
                    .ifPresent(other -> {
                        throw new OrganizationRuleException(person.fullName() + " leads team " + other.getCode()
                                + ": a person works in one team at a time");
                    });
            joining.add(person);
        }
        return joining;
    }

    // ------------------------------------------------------------------------- consultas

    @Override
    @Transactional(readOnly = true)
    public OrganizationUnitResponse findById(UUID id) {
        return toResponse(lookups.unit(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrganizationUnitResponse> search(String search, OrganizationUnitKind kind, WorkDiscipline discipline, UUID parentId,
                                                         Long executionPackageId, UUID headPersonId, Boolean active, Pageable pageable) {
        return PageMapper.toPageResponse(repository.findAll(
                OrganizationUnitSpecification.filter(search, kind, discipline, parentId, executionPackageId, headPersonId, active),
                pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationTreeNode> tree(boolean includeInactive) {
        List<OrganizationUnit> units = repository.findAllForTree();
        Map<UUID, Long> sizes = new HashMap<>();
        memberRepository.countOpenPerTeam().forEach(size -> sizes.put(size.getTeamId(), size.getLinemen()));
        Map<UUID, List<OrganizationUnit>> children = new HashMap<>();
        List<OrganizationUnit> roots = new ArrayList<>();
        for (OrganizationUnit unit : units) {
            if (!includeInactive && !Boolean.TRUE.equals(unit.getActive())) {
                continue;
            }
            if (unit.getParent() == null) {
                roots.add(unit);
            } else {
                children.computeIfAbsent(unit.getParent().getId(), ignored -> new ArrayList<>()).add(unit);
            }
        }
        return roots.stream().sorted(Comparator.comparing(OrganizationUnit::getCode)).map(root -> node(root, children, sizes)).toList();
    }

    private OrganizationTreeNode node(OrganizationUnit unit, Map<UUID, List<OrganizationUnit>> children, Map<UUID, Long> sizes) {
        List<OrganizationTreeNode> nodes = children.getOrDefault(unit.getId(), List.of()).stream()
                .sorted(Comparator.comparing((OrganizationUnit child) -> child.getKind().level()).thenComparing(OrganizationUnit::getCode))
                .map(child -> node(child, children, sizes))
                .toList();
        return new OrganizationTreeNode(unit.getId(), unit.getCode(), unit.getName(), unit.getKind(), unit.getDiscipline(),
                unit.getHead() == null ? null : personMapper.toSummary(unit.getHead()),
                new TreeSet<>(unit.getExecutionPackageIds()),
                unit.isTeam() ? sizes.getOrDefault(unit.getId(), 0L).intValue() : null,
                unit.getActive(), nodes);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<OrganizationUnitResponse>> findRevisions(UUID id, Pageable pageable) {
        return auditService.findRevisions(OrganizationUnit.class, id, this::toRevisionResponse, pageable);
    }

    // -------------------------------------------------------------------------- reglas

    private static void requireParent(OrganizationUnitKind kind, OrganizationUnit parent) {
        if (kind != OrganizationUnitKind.PROJECT && parent == null) {
            throw new ValidationException("A " + label(kind) + " hangs from a unit of a higher level (parentId)");
        }
        OrganizationRules.parentViolation(kind, parent == null ? null : parent.getKind()).ifPresent(reason -> {
            throw new OrganizationRuleException(reason);
        });
        if (parent != null && !Boolean.TRUE.equals(parent.getActive())) {
            throw new OrganizationRuleException(parent.getCode() + " is deactivated: nothing new hangs from it");
        }
    }

    /**
     * Un proyecto no tiene disciplina; un departamento dice la suya; lo demas la hereda de arriba, y si la
     * dice tiene que ser la misma.
     */
    private static WorkDiscipline discipline(OrganizationUnitKind kind, WorkDiscipline requested, OrganizationUnit parent) {
        if (kind == OrganizationUnitKind.PROJECT) {
            if (requested != null) {
                throw new ValidationException("A project spans every discipline: it has none");
            }
            return null;
        }
        WorkDiscipline inherited = parent == null ? null
                : OrganizationLookups.nearestWithDiscipline(parent).map(OrganizationUnit::getDiscipline).orElse(null);
        if (requested != null && inherited != null && requested != inherited) {
            throw new ValidationException("A unit under " + parent.getCode() + " is of " + inherited + ", not of " + requested);
        }
        WorkDiscipline discipline = requested != null ? requested : inherited;
        if (discipline == null) {
            throw new ValidationException("Nothing above this " + label(kind) + " has a discipline: say it (MAINTENANCE or CONSTRUCTION)");
        }
        return discipline;
    }

    /** Quien dirige: activo, de la categoria del nivel y, en un equipo, sin otro equipo. */
    private void requireHead(OrganizationUnitKind kind, WorkDiscipline discipline, Person head, UUID unitId) {
        if (!Boolean.TRUE.equals(head.getActive())) {
            throw new OrganizationRuleException(head.fullName() + " is deactivated");
        }
        OrganizationRules.headViolation(kind, discipline, head.getCategory()).ifPresent(reason -> {
            throw new OrganizationRuleException(reason);
        });
        if (kind != OrganizationUnitKind.TEAM) {
            return;
        }
        repository.findByHeadIdAndActiveTrue(head.getId()).stream()
                .filter(OrganizationUnit::isTeam)
                .filter(other -> !other.getId().equals(unitId))
                .findFirst()
                .ifPresent(other -> {
                    throw new OrganizationRuleException(head.fullName() + " already leads team " + other.getCode()
                            + ": a team leader leads one team");
                });
        memberRepository.findOpenByPerson(head.getId()).ifPresent(membership -> {
            String where = membership.getTeam().getId().equals(unitId) ? "this team" : "team " + membership.getTeam().getCode();
            throw new OrganizationRuleException(head.fullName() + " is a lineman of " + where + ": take them out of it first");
        });
    }

    private Vehicle defaultVehicle(OrganizationUnitKind kind, UUID vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        if (kind != OrganizationUnitKind.TEAM) {
            throw new ValidationException("Only a team has a default vehicle");
        }
        Vehicle vehicle = lookups.vehicle(vehicleId);
        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            throw new FleetRuleException("Vehicle " + vehicle.getCode() + " is retired: it is nobody's default vehicle");
        }
        return vehicle;
    }

    private static void requireTeam(OrganizationUnit unit) {
        if (!unit.isTeam()) {
            throw new ValidationException(unit.getCode() + " is a " + label(unit.getKind()) + ": only a team has linemen");
        }
    }

    private OrganizationUnit activeTeam(UUID teamId) {
        OrganizationUnit team = lookups.lockedUnit(teamId);
        requireTeam(team);
        if (!Boolean.TRUE.equals(team.getActive())) {
            throw new OrganizationRuleException("Team " + team.getCode() + " is deactivated: reactivate it with its linemen");
        }
        return team;
    }

    private static String label(OrganizationUnitKind kind) {
        return kind.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // ------------------------------------------------------------------------ respuestas

    private OrganizationUnitResponse toResponse(OrganizationUnit unit) {
        List<TeamMemberResponse> members = null;
        Integer linemen = null;
        Boolean complete = null;
        if (unit.isTeam()) {
            members = memberRepository.findOpenByTeam(unit.getId()).stream().map(mapper::toMemberResponse).toList();
            linemen = members.size();
            complete = Boolean.TRUE.equals(unit.getActive())
                    && CrewComposition.completeViolation(unit.getCode(), unit.getHead() != null, linemen).isEmpty();
        }
        Person supervisor = OrganizationLookups.nearest(unit, OrganizationUnitKind.SUPERVISION).map(OrganizationUnit::getHead).orElse(null);
        Person chief = OrganizationLookups.nearest(unit, OrganizationUnitKind.CHIEF_SUPERVISION).map(OrganizationUnit::getHead).orElse(null);
        return response(unit, members, linemen, complete, supervisor, chief);
    }

    /** Una revision cuenta la unidad como era; sus linemen tienen su propio historial ({@code /members?includeFormer}). */
    private OrganizationUnitResponse toRevisionResponse(OrganizationUnit unit) {
        return response(unit, null, null, null, null, null);
    }

    private OrganizationUnitResponse response(OrganizationUnit unit, List<TeamMemberResponse> members, Integer linemen, Boolean complete,
                                              Person supervisor, Person chief) {
        return new OrganizationUnitResponse(
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.getKind(),
                unit.getDiscipline(),
                unit.getParent() == null ? null : mapper.toSummary(unit.getParent()),
                unit.getHead() == null ? null : personMapper.toSummary(unit.getHead()),
                new TreeSet<>(unit.getExecutionPackageIds()),
                FleetResponses.summary(unit.getDefaultVehicle()),
                members,
                linemen,
                complete,
                supervisor == null ? null : personMapper.toSummary(supervisor),
                chief == null ? null : personMapper.toSummary(chief),
                unit.getActive(),
                unit.getVersion(),
                auditableMapper.toAuditMetadata(unit)
        );
    }
}
