package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.person.MeResponse;
import com.alejandro.mtoresources.application.dto.person.PersonRequest;
import com.alejandro.mtoresources.application.dto.person.PersonResponse;
import com.alejandro.mtoresources.application.dto.person.PersonUpdateRequest;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.OrganizationRuleException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.PageMapper;
import com.alejandro.mtoresources.application.mapper.PersonMapper;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.EntityAuditService;
import com.alejandro.mtoresources.application.service.PersonService;
import com.alejandro.mtoresources.configuration.security.CurrentUserService;
import com.alejandro.mtoresources.domain.model.CrewComposition;
import com.alejandro.mtoresources.domain.model.OrganizationRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonEmployer;
import com.alejandro.mtoresources.infrastructure.persistence.entity.TeamMember;
import com.alejandro.mtoresources.infrastructure.persistence.repository.OrganizationUnitRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonQualificationRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.TeamMemberRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.PersonSpecification;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PersonServiceImpl implements PersonService {

    private static final Set<String> CLEARABLE = Set.of("employeeNumber", "username", "keycloakUserId", "company", "phone");

    private final PersonRepository repository;
    private final OrganizationUnitRepository unitRepository;
    private final TeamMemberRepository memberRepository;
    private final PersonQualificationRepository qualifications;
    private final OrganizationLookups lookups;
    private final PersonMapper mapper;
    private final EntityAuditService auditService;
    private final DomainEventPublisher events;
    private final Unavailability unavailability;
    private final CurrentUserService currentUser;
    private final Clock clock;

    @Override
    @Transactional
    public PersonResponse create(PersonRequest request) {
        String username = username(request.username());
        String employeeNumber = SpecificationUtils.normalize(request.employeeNumber());
        String keycloakUserId = SpecificationUtils.normalize(request.keycloakUserId());
        requireUnique(null, username, employeeNumber, keycloakUserId);

        PersonEmployer employer = request.employer() == null ? PersonEmployer.OWN : request.employer();
        Person person = Person.builder()
                .code("PER-%06d".formatted(repository.nextCodeValue()))
                .firstName(PatchRules.requireText(request.firstName(), "firstName"))
                .lastName(PatchRules.requireText(request.lastName(), "lastName"))
                .category(request.category())
                .employeeNumber(employeeNumber)
                .username(username)
                .keycloakUserId(keycloakUserId)
                .employer(employer)
                .company(SpecificationUtils.normalize(request.company()))
                .phone(SpecificationUtils.normalize(request.phone()))
                .build();
        requireCompany(person);

        Person saved = repository.saveAndFlush(person);
        // Una persona nueva aun no tiene cualificaciones: se dan despues, en /people/{id}/qualifications.
        events.publish(ResourcesEvents.person(saved, List.of(), List.of(), ResourcesEvents.CREATED));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PersonResponse update(UUID id, PersonUpdateRequest request) {
        return patch(id, MergePatch.of(request));
    }

    @Override
    @Transactional
    public PersonResponse patch(UUID id, MergePatch<PersonUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        PersonUpdateRequest values = patch.values();
        Person person = lookups.person(id);
        StaleVersionException.check("Person " + person.getCode(), person.getVersion(), values.version());

        if (values.isDeactivating()) {
            deactivate(person);
            return mapper.toResponse(person);
        }

        // Los equipos en cuyo evento sale: se bloquean antes de cambiar nada (OrganizationLookups).
        List<OrganizationUnit> teams = lookups.lockTeams(lookups.teamsReferencing(id));
        // Y su fila, aunque no cambie nada de ella: su evento cuenta sus cualificaciones, que se cambian con
        // la persona bloqueada (PersonQualificationServiceImpl).
        repository.findByIdForUpdate(id);
        String before = teamFacing(person);

        if (values.firstName() != null) {
            person.setFirstName(PatchRules.requireText(values.firstName(), "firstName"));
        }
        if (values.lastName() != null) {
            person.setLastName(PatchRules.requireText(values.lastName(), "lastName"));
        }
        if (values.category() != null && values.category() != person.getCategory()) {
            requireCategoryFits(person, values.category());
            person.setCategory(values.category());
        }
        PatchRules.set(patch, "employeeNumber", SpecificationUtils.normalize(values.employeeNumber()), person::setEmployeeNumber);
        PatchRules.set(patch, "username", username(values.username()), person::setUsername);
        PatchRules.set(patch, "keycloakUserId", SpecificationUtils.normalize(values.keycloakUserId()), person::setKeycloakUserId);
        PatchRules.set(patch, "employer", values.employer(), person::setEmployer);
        PatchRules.set(patch, "company", SpecificationUtils.normalize(values.company()), person::setCompany);
        PatchRules.set(patch, "phone", SpecificationUtils.normalize(values.phone()), person::setPhone);
        if (Boolean.TRUE.equals(values.active())) {
            person.setActive(true);
        }
        requireCompany(person);
        requireUnique(person.getId(), person.getUsername(), person.getEmployeeNumber(), person.getKeycloakUserId());

        Person saved = repository.saveAndFlush(person);
        events.publish(ResourcesEvents.person(saved, qualifications.findAllOfPerson(saved.getId()),
                unavailability.ofPerson(saved.getId()), ResourcesEvents.UPDATED));
        if (!before.equals(teamFacing(saved))) {
            lookups.publishActiveTeamsUpdated(teams);
        }
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PersonResponse findById(UUID id) {
        return mapper.toResponse(lookups.person(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PersonResponse> search(String search, Collection<PersonCategory> categories, UUID unitId, Boolean active, Pageable pageable) {
        return PageMapper.toPageResponse(
                repository.findAll(PersonSpecification.filter(search, categories, unitId, active), pageable), mapper::toResponse);
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        deactivate(lookups.person(id));
    }

    /**
     * Una persona que dirige una unidad no se desactiva: primero se le da otro jefe a la unidad. Una que
     * va de lineman sale de su equipo hoy, si el equipo no se queda con menos de 2.
     */
    private void deactivate(Person person) {
        if (!Boolean.TRUE.equals(person.getActive())) {
            return;
        }
        List<OrganizationUnit> headed = unitRepository.findByHeadIdAndActiveTrue(person.getId());
        if (!headed.isEmpty()) {
            throw new OrganizationRuleException(person.fullName() + " leads " + codes(headed)
                    + ": give it another head before deactivating them");
        }
        List<OrganizationUnit> teams = List.of();
        Optional<TeamMember> membership = memberRepository.findOpenByPerson(person.getId());
        if (membership.isPresent()) {
            teams = lookups.lockTeams(List.of(membership.get().getTeam().getId()));
            OrganizationUnit team = membership.get().getTeam();
            CrewComposition.removeViolation("Team " + team.getCode(), memberRepository.countOpenByTeam(team.getId()))
                    .ifPresent(reason -> {
                        throw new OrganizationRuleException(reason);
                    });
            TeamMember member = membership.get();
            LocalDate today = LocalDate.now(clock);
            member.setLeftOn(today.isBefore(member.getJoinedOn()) ? member.getJoinedOn() : today);
        }
        person.setActive(false);
        Person saved = repository.saveAndFlush(person);
        events.publish(ResourcesEvents.person(saved, qualifications.findAllOfPerson(saved.getId()),
                unavailability.ofPerson(saved.getId()), ResourcesEvents.DEACTIVATED));
        lookups.publishActiveTeamsUpdated(teams);
    }

    @Override
    @Transactional(readOnly = true)
    public MeResponse me() {
        String username = currentUser.getUsername()
                .orElseThrow(() -> new NotFoundException("Person", "The token carries no username"));
        Person person = repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("Person", "No person has the username " + username
                        + ": add it to their record in /people"));

        List<MeResponse.Unit> units = new ArrayList<>();
        List<UUID> unitIds = new ArrayList<>();
        for (OrganizationUnit unit : unitRepository.findByHeadIdAndActiveTrue(person.getId())) {
            units.add(unit(unit, MeResponse.Role.HEAD));
            unitIds.add(unit.getId());
        }
        memberRepository.findOpenByPerson(person.getId()).ifPresent(member -> {
            OrganizationUnit team = member.getTeam();
            units.add(unit(team, MeResponse.Role.LINEMAN));
            unitIds.add(team.getId());
        });

        // Los EP de lo que dirige y de todo lo que cuelga de ello: el ambito de un jefe de supervisores
        // es el de sus supervisiones aunque su jefatura no diga ninguno.
        Set<Long> executionPackages = new TreeSet<>();
        for (OrganizationUnit unit : unitRepository.findAllById(lookups.unitsUnder(unitIds))) {
            if (Boolean.TRUE.equals(unit.getActive())) {
                executionPackages.addAll(unit.getExecutionPackageIds());
            }
        }
        return new MeResponse(mapper.toResponse(person), units, executionPackages);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<PersonResponse>> findRevisions(UUID id, Pageable pageable) {
        return auditService.findRevisions(Person.class, id, mapper::toResponse, pageable);
    }

    /** Una categoria nueva tiene que seguir valiendo para lo que dirige y para el equipo en el que va. */
    private void requireCategoryFits(Person person, PersonCategory category) {
        for (OrganizationUnit unit : unitRepository.findByHeadIdAndActiveTrue(person.getId())) {
            OrganizationRules.headViolation(unit.getKind(), unit.getDiscipline(), category).ifPresent(reason -> {
                throw new OrganizationRuleException(person.fullName() + " leads " + unit.getCode() + ". " + reason);
            });
        }
        if (memberRepository.findOpenByPerson(person.getId()).isPresent() && !OrganizationRules.canBeLineman(category)) {
            throw new OrganizationRuleException(person.fullName() + " is a lineman, and a lineman is a WORKER or a TEAM_LEADER, not a "
                    + category + ": take them out of their team first");
        }
    }

    private void requireUnique(UUID id, String username, String employeeNumber, String keycloakUserId) {
        if (username != null && repository.existsUsername(username, id)) {
            throw new DuplicateCodeException("Person", "username", username);
        }
        if (employeeNumber != null && repository.existsEmployeeNumber(employeeNumber, id)) {
            throw new DuplicateCodeException("Person", "employee number", employeeNumber);
        }
        if (keycloakUserId != null && repository.existsKeycloakUserId(keycloakUserId, id)) {
            throw new DuplicateCodeException("Person", "Keycloak user", keycloakUserId);
        }
    }

    private static void requireCompany(Person person) {
        if (person.getEmployer() == PersonEmployer.SUBCONTRACTOR && person.getCompany() == null) {
            throw new ValidationException("A subcontractor says their company");
        }
    }

    /** Keycloak guarda los nombres de usuario en minusculas. */
    private static String username(String value) {
        String normalized = SpecificationUtils.normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    /** Lo que sale de una persona en el evento de su equipo. */
    private static String teamFacing(Person person) {
        return String.join("|", person.fullName(), Objects.toString(person.getUsername(), ""), person.getCategory().name(),
                person.getCode());
    }

    private static MeResponse.Unit unit(OrganizationUnit unit, MeResponse.Role role) {
        return new MeResponse.Unit(unit.getId(), unit.getCode(), unit.getName(), unit.getKind(), unit.getDiscipline(), role);
    }

    private static String codes(List<OrganizationUnit> units) {
        return String.join(", ", units.stream().map(OrganizationUnit::getCode).sorted().toList());
    }
}
