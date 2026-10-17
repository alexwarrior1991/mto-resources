package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.fleet.FleetPositionResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeUpdateRequest;
import com.alejandro.mtoresources.application.dto.messaging.DomainEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtoresources.application.dto.operation.OperationResponse;
import com.alejandro.mtoresources.application.dto.operation.OperationStatus;
import com.alejandro.mtoresources.application.dto.operation.RepublishResult;
import com.alejandro.mtoresources.application.dto.organization.OrganizationTreeNode;
import com.alejandro.mtoresources.application.dto.organization.OrganizationUnitRequest;
import com.alejandro.mtoresources.application.dto.organization.OrganizationUnitResponse;
import com.alejandro.mtoresources.application.dto.organization.OrganizationUnitUpdateRequest;
import com.alejandro.mtoresources.application.dto.organization.TeamMemberLeaveRequest;
import com.alejandro.mtoresources.application.dto.organization.TeamMemberRequest;
import com.alejandro.mtoresources.application.dto.organization.TeamMembersRequest;
import com.alejandro.mtoresources.application.dto.person.MeResponse;
import com.alejandro.mtoresources.application.dto.person.PersonRequest;
import com.alejandro.mtoresources.application.dto.person.PersonResponse;
import com.alejandro.mtoresources.application.dto.person.PersonUpdateRequest;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.FleetRuleException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.OrganizationRuleException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.TooManyOperationsException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.service.BudgetedOperations;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.OrganizationUnitService;
import com.alejandro.mtoresources.application.service.PersonService;
import com.alejandro.mtoresources.application.service.RepublishService;
import com.alejandro.mtoresources.application.service.VehicleLocationService;
import com.alejandro.mtoresources.application.service.VehicleService;
import com.alejandro.mtoresources.application.service.VehicleTypeService;
import com.alejandro.mtoresources.configuration.AuditActorResolver;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonEmployer;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RailGearType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleAttachment;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationSource;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehiclePositionRepository;
import com.alejandro.mtoresources.infrastructure.web.exception.GlobalExceptionHandler;
import com.alejandro.mtoresources.support.PostgreSQLTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los servicios contra un PostgreSQL real, con el contexto entero y los eventos capturados por
 * {@link RecordingEventPublisher}. Aqui no se simulan los repositorios a proposito: las reglas de
 * un equipo y de la flota son reglas de filas (bloqueos, indices parciales, la marca de agua de la
 * posicion), y un doble las daria por buenas sin probarlas. La base se comparte entre casos: cada uno
 * crea lo suyo con codigos aleatorios.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "app.operations.max-active=4")
class BusinessLayerTest extends PostgreSQLTestContainer {

    private static final UUID MAINTENANCE_DEPARTMENT = UUID.fromString("0195b000-0000-7000-8000-000000000002");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerPostgreSQLProperties(registry);
    }

    @TestConfiguration
    static class Probes {
        @Bean
        @Primary
        RecordingEventPublisher recordingEventPublisher() {
            return new RecordingEventPublisher();
        }
    }

    @Autowired
    private RecordingEventPublisher events;
    @Autowired
    private PersonService people;
    @Autowired
    private OrganizationUnitService units;
    @Autowired
    private VehicleTypeService types;
    @Autowired
    private VehicleService vehicles;
    @Autowired
    private VehicleLocationService locations;
    @Autowired
    private VehiclePositionRepository positions;
    @Autowired
    private MaintenanceShiftEventHandler shiftHandler;
    @Autowired
    private FieldPositionHandler fieldHandler;
    @Autowired
    private RepublishService republish;
    @Autowired
    private BudgetedOperations operations;
    @Autowired
    private JdbcTemplate jdbc;

    /** Una persona autenticada, como en una peticion: lo que se escribe lleva su nombre y no {@code unknown}. */
    @BeforeEach
    void authenticateAndClearEvents() {
        authenticateAs("recursos.gestor");
        events.clear();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------------- personas

    @Test
    void aPersonGetsASequenceCodeALowercaseUsernameAndAnEventWithoutTheirPhone() {
        String username = "Equipo." + random();
        PersonResponse person = people.create(new PersonRequest("Yossi", "Cohen", PersonCategory.TEAM_LEADER, null, username, null,
                null, null, "+972 50 000 0000"));

        assertTrue(person.code().matches("PER-\\d{6}"), person.code());
        assertEquals(username.toLowerCase(Locale.ROOT), person.username());
        assertEquals(PersonEmployer.OWN, person.employer());
        DomainEvent event = events.only("person.created");
        assertEquals(person.code(), event.values().get("code"));
        assertEquals("Yossi Cohen", event.values().get("fullName"));
        assertEquals("TEAM_LEADER", event.values().get("category"));
        assertFalse(event.values().containsKey("phone"), "The phone never travels");
    }

    @Test
    void aUsernameIsUniqueASubcontractorSaysTheirCompanyAndAStaleVersionIsRefused() {
        PersonResponse person = person(PersonCategory.WORKER);
        assertThrows(DuplicateCodeException.class, () -> people.create(new PersonRequest("Other", "One", PersonCategory.WORKER, null,
                person.username().toUpperCase(Locale.ROOT), null, null, null, null)));
        assertThrows(ValidationException.class, () -> people.create(new PersonRequest("Sub", "Contractor", PersonCategory.WORKER, null,
                null, null, PersonEmployer.SUBCONTRACTOR, null, null)));
        assertThrows(StaleVersionException.class, () -> people.patch(person.id(), MergePatch.of(new PersonUpdateRequest(
                "New", null, null, null, null, null, null, null, null, null, person.version() + 1))));
        assertThrows(ValidationException.class, () -> people.patch(person.id(),
                new MergePatch<>(new PersonUpdateRequest(null, null, null, null, null, null, null, null, null, null, null), Set.of("firstName"))));
    }

    /**
     * Los selectores de las pantallas piden varias categorias a la vez (los linemen son operarios o
     * jefes de equipo; el jefe de un turno, cualquiera menos un operario): {@code category} se repite.
     */
    @Test
    void theSearchTakesSeveralCategoriesAtOnceAndNoneMeansAll() {
        String surname = "Cat" + random();
        PersonResponse worker = people.create(new PersonRequest("W", surname, PersonCategory.WORKER, null, null, null, null, null, null));
        PersonResponse leader = people.create(new PersonRequest("L", surname, PersonCategory.TEAM_LEADER, null, null, null, null, null, null));
        PersonResponse supervisor = people.create(new PersonRequest("S", surname, PersonCategory.SUPERVISOR, null, null, null, null, null, null));

        assertEquals(Set.of(worker.id(), leader.id()), ids(people.search(surname,
                List.of(PersonCategory.WORKER, PersonCategory.TEAM_LEADER), null, null, PageRequest.of(0, 10)).content()));
        assertEquals(Set.of(supervisor.id()), ids(people.search(surname,
                List.of(PersonCategory.SUPERVISOR), null, null, PageRequest.of(0, 10)).content()));
        assertEquals(Set.of(worker.id(), leader.id(), supervisor.id()), ids(people.search(surname,
                List.of(), null, null, PageRequest.of(0, 10)).content()));
        assertEquals(3, people.search(surname, null, null, null, PageRequest.of(0, 10)).content().size());
    }

    // -------------------------------------------------------------------------- equipos

    @Test
    void aTeamIsBornCompleteAndItsEventCarriesItsLeaderLinemenAndSupervisor() {
        OrganizationUnitResponse supervision = supervision(Set.of(4L, 5L));
        PersonResponse leader = person(PersonCategory.TEAM_LEADER);
        List<UUID> linemen = linemen(3);
        events.clear();

        OrganizationUnitResponse team = team(supervision.id(), leader.id(), linemen, Set.of(4L, 5L));

        assertEquals(OrganizationUnitKind.TEAM, team.kind());
        assertEquals(WorkDiscipline.MAINTENANCE, team.discipline(), "Inherited from the department");
        assertEquals(3, team.linemen());
        assertTrue(team.complete());
        assertEquals(supervision.head().id(), team.supervisor().id());
        DomainEvent event = events.only("team.created");
        assertEquals(team.code(), event.values().get("code"));
        assertEquals(leader.id().toString(), ((Map<?, ?>) event.values().get("leader")).get("personId"));
        assertEquals(supervision.head().id().toString(), ((Map<?, ?>) event.values().get("supervisor")).get("personId"));
        assertEquals(3, ((List<?>) event.values().get("members")).size());
        assertEquals(List.of(4L, 5L), event.values().get("executionPackageIds"));
        assertEquals(MAINTENANCE_DEPARTMENT.toString(), event.values().get("departmentId"));
    }

    @Test
    void aTeamWithoutItsLeaderOrWithOneOrSevenLinemenIsNotCreated() {
        OrganizationUnitResponse supervision = supervision(Set.of());
        PersonResponse leader = person(PersonCategory.TEAM_LEADER);

        assertThrows(ValidationException.class, () -> units.create(teamRequest(supervision.id(), null, linemen(3), Set.of())));
        assertThrows(ValidationException.class, () -> units.create(teamRequest(supervision.id(), leader.id(), linemen(1), Set.of())));
        assertThrows(ValidationException.class, () -> units.create(teamRequest(supervision.id(), leader.id(), linemen(7), Set.of())));
        List<UUID> withLeader = new ArrayList<>(linemen(2));
        withLeader.add(leader.id());
        assertThrows(ValidationException.class, () -> units.create(teamRequest(supervision.id(), leader.id(), withLeader, Set.of())),
                "The leader is not one of the linemen");
        PersonResponse worker = person(PersonCategory.WORKER);
        assertThrows(OrganizationRuleException.class, () -> units.create(teamRequest(supervision.id(), worker.id(), linemen(3), Set.of())),
                "A worker does not lead a team");
        PersonResponse supervisor = person(PersonCategory.SUPERVISOR);
        List<UUID> withSupervisor = new ArrayList<>(linemen(2));
        withSupervisor.add(supervisor.id());
        assertThrows(OrganizationRuleException.class, () -> units.create(teamRequest(supervision.id(), leader.id(), withSupervisor, Set.of())),
                "A supervisor is no lineman");
    }

    @Test
    void sixIsTheMostTwoTheFewestAndTheWholeListChangesInOneGo() {
        OrganizationUnitResponse team = team(supervision(Set.of()).id(), person(PersonCategory.TEAM_LEADER).id(), linemen(6), Set.of());
        assertThrows(OrganizationRuleException.class, () -> units.addMember(team.id(), new TeamMemberRequest(person(PersonCategory.WORKER).id(), null)));

        List<UUID> two = linemen(2);
        events.clear();
        OrganizationUnitResponse replaced = units.replaceMembers(team.id(), new TeamMembersRequest(two, null));
        assertEquals(2, replaced.linemen());
        assertEquals(Set.copyOf(two), Set.copyOf(replaced.members().stream().map(member -> member.person().id()).toList()));
        assertEquals(2, ((List<?>) events.only("team.updated").values().get("members")).size());

        assertThrows(OrganizationRuleException.class, () -> units.removeMember(team.id(), two.getFirst(), new TeamMemberLeaveRequest(null)));
        units.addMember(team.id(), new TeamMemberRequest(person(PersonCategory.WORKER).id(), null));
        OrganizationUnitResponse afterLeave = units.removeMember(team.id(), two.getFirst(), new TeamMemberLeaveRequest(null));
        assertEquals(2, afterLeave.linemen());
        assertEquals(9, units.members(team.id(), true).size(), "Six who left, two plus one who joined: the ones who left keep their row");
    }

    @Test
    void aPersonWorksInOneTeamAtATime() {
        UUID supervision = supervision(Set.of()).id();
        PersonResponse leaderA = person(PersonCategory.TEAM_LEADER);
        List<UUID> crewA = linemen(2);
        team(supervision, leaderA.id(), crewA, Set.of());

        PersonResponse leaderB = person(PersonCategory.TEAM_LEADER);
        assertThrows(OrganizationRuleException.class, () -> units.create(teamRequest(supervision, leaderB.id(),
                List.of(crewA.getFirst(), person(PersonCategory.WORKER).id()), Set.of())), "Already a lineman of A");
        assertThrows(OrganizationRuleException.class, () -> units.create(teamRequest(supervision, leaderB.id(),
                List.of(leaderA.id(), person(PersonCategory.WORKER).id()), Set.of())), "Leads A");
        assertThrows(OrganizationRuleException.class, () -> units.create(teamRequest(supervision, leaderA.id(), linemen(2), Set.of())),
                "A team leader leads one team");
    }

    @Test
    void changingTheHeadOfASupervisionRepublishesItsTeams() {
        OrganizationUnitResponse supervision = supervision(Set.of());
        OrganizationUnitResponse team = team(supervision.id(), person(PersonCategory.TEAM_LEADER).id(), linemen(2), Set.of());
        PersonResponse newSupervisor = person(PersonCategory.SUPERVISOR);
        events.clear();

        units.patch(supervision.id(), MergePatch.of(updateRequest(null, newSupervisor.id(), null)));

        DomainEvent event = events.only("team.updated");
        assertEquals(team.id().toString(), event.entityId());
        assertEquals(newSupervisor.id().toString(), ((Map<?, ?>) event.values().get("supervisor")).get("personId"));
        assertEquals(newSupervisor.id(), units.findById(team.id()).supervisor().id());
    }

    @Test
    void aTeamLeaderIsReplacedNeverEmptied() {
        OrganizationUnitResponse team = team(supervision(Set.of()).id(), person(PersonCategory.TEAM_LEADER).id(), linemen(2), Set.of());
        assertThrows(ValidationException.class, () -> units.patch(team.id(),
                new MergePatch<>(updateRequest(null, null, null), Set.of("headPersonId"))));
        PersonResponse other = person(PersonCategory.TEAM_LEADER);
        OrganizationUnitResponse patched = units.patch(team.id(), MergePatch.of(updateRequest(null, other.id(), null)));
        assertEquals(other.id(), patched.head().id());
    }

    @Test
    void aUnitWithActiveUnitsUnderItStaysAndATeamComesBackComplete() {
        OrganizationUnitResponse supervision = supervision(Set.of());
        OrganizationUnitResponse team = team(supervision.id(), person(PersonCategory.TEAM_LEADER).id(), linemen(3), Set.of());
        assertThrows(OrganizationRuleException.class, () -> units.deactivate(supervision.id()));

        events.clear();
        units.deactivate(team.id());
        assertEquals(0, units.members(team.id(), false).size(), "Its linemen are released");
        assertEquals(Boolean.FALSE, events.only("team.deactivated").values().get("active"));

        assertThrows(ValidationException.class, () -> units.patch(team.id(), MergePatch.of(new OrganizationUnitUpdateRequest(
                null, null, null, null, null, true, null, null, null))));
        OrganizationUnitResponse back = units.patch(team.id(), MergePatch.of(new OrganizationUnitUpdateRequest(
                null, null, null, null, null, true, linemen(2), null, null)));
        assertTrue(back.active());
        assertTrue(back.complete());
        units.deactivate(team.id());
        units.deactivate(supervision.id());
    }

    @Test
    void someoneWhoLeadsIsNotDeactivatedAndALinemanLeavesTheirTeam() {
        PersonResponse leader = person(PersonCategory.TEAM_LEADER);
        List<UUID> crew = linemen(3);
        OrganizationUnitResponse team = team(supervision(Set.of()).id(), leader.id(), crew, Set.of());
        assertThrows(OrganizationRuleException.class, () -> people.deactivate(leader.id()));

        events.clear();
        people.deactivate(crew.getFirst());
        assertEquals(List.of("person.deactivated", "team.updated"), events.names());
        assertEquals(2, units.findById(team.id()).linemen());
        assertThrows(OrganizationRuleException.class, () -> people.patch(crew.get(1), MergePatch.of(new PersonUpdateRequest(
                null, null, PersonCategory.SUPERVISOR, null, null, null, null, null, null, null, null))), "A supervisor is no lineman");
    }

    @Test
    void meIsThePersonOfTheTokenWithTheUnitsAndTheExecutionPackagesUnderThem() {
        OrganizationUnitResponse supervision = supervision(Set.of());
        PersonResponse leader = person(PersonCategory.TEAM_LEADER);
        OrganizationUnitResponse team = team(supervision.id(), leader.id(), linemen(2), Set.of(9L, 11L));
        authenticateAs(supervision.head().username());

        MeResponse me = people.me();

        assertEquals(supervision.head().id(), me.person().id());
        assertEquals(MeResponse.Role.HEAD, me.units().getFirst().role());
        assertEquals(Set.of(9L, 11L), me.executionPackageIds(), "The packages of the teams under the supervision");
        authenticateAs(leader.username());
        assertEquals(team.id(), people.me().units().getFirst().id());
    }

    @Test
    void theTreeHangsFromTheProjectWithTheLinemenOfEveryTeam() {
        OrganizationUnitResponse supervision = supervision(Set.of());
        OrganizationUnitResponse team = team(supervision.id(), person(PersonCategory.TEAM_LEADER).id(), linemen(4), Set.of());

        OrganizationTreeNode project = units.tree(false).stream().filter(node -> node.code().equals("MTO")).findFirst().orElseThrow();
        OrganizationTreeNode department = project.children().stream().filter(node -> node.code().equals("MNT")).findFirst().orElseThrow();
        OrganizationTreeNode supervisionNode = department.children().stream().filter(node -> node.id().equals(supervision.id()))
                .findFirst().orElseThrow();
        assertEquals(4, supervisionNode.children().stream().filter(node -> node.id().equals(team.id())).findFirst().orElseThrow().linemen());
    }

    // ---------------------------------------------------------------------------- flota

    @Test
    void aModelIsCoherentWithItsMobility() {
        assertThrows(ValidationException.class, () -> types.create(typeRequest(VehicleMobility.ROAD_ONLY, VehicleCategory.CATENARY_TOWER_CAR,
                null, false, Set.of())), "A tower car is rail-only");
        assertThrows(ValidationException.class, () -> types.create(typeRequest(VehicleMobility.ROAD_RAIL, VehicleCategory.ROAD_RAIL_TRUCK,
                null, false, Set.of())), "A road-rail truck says its rail gear");
        VehicleTypeResponse roadRail = types.create(typeRequest(VehicleMobility.ROAD_RAIL, VehicleCategory.ROAD_RAIL_TRUCK,
                RailGearType.RAIL_WHEEL_DRIVE, false, EnumSet.of(VehicleCapability.CRANE)));
        assertEquals(Set.of(VehicleCapability.CRANE), roadRail.capabilities());
    }

    @Test
    void aVehicleCarriesWhatItsModelNeeds() {
        VehicleTypeResponse towerCar = types.create(typeRequest(VehicleMobility.RAIL_ONLY, VehicleCategory.CATENARY_TOWER_CAR, null, true,
                EnumSet.of(VehicleCapability.TOWING)));
        assertThrows(ValidationException.class, () -> vehicles.create(vehicleRequest(towerCar.id(), null, null)),
                "It may run on open track: it has its rail vehicle number");
        assertThrows(ValidationException.class, () -> vehicles.create(vehicleRequest(towerCar.id(), "12-345-67", "99 " + random())),
                "A rail-only vehicle has no plate");
        VehicleTypeResponse van = types.create(typeRequest(VehicleMobility.ROAD_ONLY, VehicleCategory.VAN, null, false, Set.of()));
        assertThrows(ValidationException.class, () -> vehicles.create(vehicleRequest(van.id(), null, null)), "A van has its plate");

        events.clear();
        VehicleResponse vehicle = vehicles.create(vehicleRequest(towerCar.id(), null, "99 " + random()));
        assertTrue(vehicle.available());
        DomainEvent event = events.only("vehicle.created");
        assertEquals("RAIL_ONLY", ((Map<?, ?>) event.values().get("type")).get("mobility"));
        assertEquals(true, event.values().get("available"));
        assertEquals(false, event.values().get("availabilityChanged"), "An arrival changes nobody's availability");
        assertEquals(false, event.values().get("republished"));
    }

    @Test
    void aVehicleUnderRepairSaysWhyAndIsNotAvailable() {
        VehicleResponse vehicle = railVehicle(Set.of());
        assertThrows(ValidationException.class, () -> vehicles.patch(vehicle.id(), MergePatch.of(vehicleUpdate(VehicleOperationalStatus.UNDER_REPAIR, null))));
        events.clear();
        VehicleResponse repaired = vehicles.patch(vehicle.id(), MergePatch.of(vehicleUpdate(VehicleOperationalStatus.UNDER_REPAIR, "Hydraulic leak")));
        assertFalse(repaired.available());
        DomainEvent outOfService = events.only("vehicle.updated");
        assertEquals(false, outOfService.values().get("available"));
        assertEquals(true, outOfService.values().get("availabilityChanged"), "Out of service: a notice says so once");

        events.clear();
        vehicles.patch(vehicle.id(), MergePatch.of(vehicleUpdate(VehicleOperationalStatus.UNDER_REPAIR, "Hydraulic leak, parts ordered")));
        assertEquals(false, events.only("vehicle.updated").values().get("availabilityChanged"), "Still out of service: nothing new to tell");

        events.clear();
        VehicleResponse back = vehicles.patch(vehicle.id(), MergePatch.of(vehicleUpdate(VehicleOperationalStatus.OPERATIONAL, null)));
        assertNull(back.statusReason(), "Back to work, the old reason goes");
        assertEquals(true, events.only("vehicle.updated").values().get("availabilityChanged"), "Back in service");
    }

    @Test
    void retiringAnAvailableVehicleChangesItsAvailability() {
        VehicleResponse vehicle = railVehicle(Set.of());
        events.clear();
        vehicles.deactivate(vehicle.id());
        DomainEvent retired = events.only("vehicle.deactivated");
        assertEquals(true, retired.values().get("availabilityChanged"));
        assertEquals(false, retired.values().get("available"));
    }

    @Test
    void theDefaultVehicleOfAnActiveTeamIsNotRetired() {
        VehicleResponse vehicle = railVehicle(Set.of());
        OrganizationUnitResponse team = units.create(new OrganizationUnitRequest("T" + random(), "Team", OrganizationUnitKind.TEAM, null,
                supervision(Set.of()).id(), person(PersonCategory.TEAM_LEADER).id(), Set.of(), vehicle.id(), linemen(2), null));
        assertEquals(vehicle.code(), team.defaultVehicle().code());
        assertThrows(FleetRuleException.class, () -> vehicles.deactivate(vehicle.id()));
    }

    @Test
    void changingAModelRepublishesItsVehicles() {
        VehicleResponse vehicle = railVehicle(Set.of());
        events.clear();
        types.patch(vehicle.type().id(), MergePatch.of(new VehicleTypeUpdateRequest(null, null, null, null, null, null, null, null, null, null,
                null, null, null, 9, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null)));
        DomainEvent event = events.only("vehicle.updated");
        assertEquals(vehicle.id().toString(), event.entityId());
        assertEquals(9, ((Map<?, ?>) event.values().get("type")).get("seats"));
        assertEquals(false, event.values().get("availabilityChanged"), "Its model changed, not whether it can work");
        assertEquals(false, event.values().get("republished"));
    }

    // ------------------------------------------------------------------------ ubicacion

    @Test
    void aRailOnlyVehicleIsNeverOnTheRoadAndALateLocationDoesNotMoveIt() {
        VehicleResponse vehicle = railVehicle(Set.of());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertThrows(FleetRuleException.class, () -> locations.recordManual(vehicle.id(), location(VehicleLocationKind.ON_ROAD, null, now)));
        assertThrows(FleetRuleException.class, () -> locations.recordManual(vehicle.id(),
                new VehicleLocationRequest(VehicleLocationKind.PARKED, null, null, null, null, null, null, null, "A car park", null, null, now)));
        assertThrows(ValidationException.class, () -> locations.recordManual(vehicle.id(), location(VehicleLocationKind.ON_TRACK, 14L,
                now.plus(Duration.ofHours(1)))), "In the future");

        locations.recordManual(vehicle.id(), location(VehicleLocationKind.ON_TRACK, 14L, now));
        locations.recordManual(vehicle.id(), new VehicleLocationRequest(VehicleLocationKind.PARKED, null, null, 15L, new BigDecimal("12.400"),
                null, null, null, null, null, null, now.minus(Duration.ofHours(2))));

        assertEquals(2, locations.history(vehicle.id(), null, null, PageRequest.of(0, 10)).content().size());
        VehicleResponse read = vehicles.findById(vehicle.id());
        assertEquals(VehicleLocationKind.ON_TRACK, read.position().kind(), "The late one stays in the history only");
        assertEquals(14L, read.position().trackId());
    }

    @Test
    void anAttachedWagonIsWhereItsCarrierIs() {
        VehicleResponse towerCar = railVehicle(EnumSet.of(VehicleCapability.TOWING));
        VehicleTypeResponse wagonType = types.create(new VehicleTypeRequest("WG" + random(), "Flat wagon", null, null, VehicleMobility.RAIL_ONLY,
                VehicleCategory.RAIL_WAGON, false, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, false, null, null, null, null, null, Set.of(), null, null, null, null, null, null, null));
        VehicleResponse wagon = vehicles.create(vehicleRequest(wagonType.id(), null, null));
        VehicleResponse van = vehicles.create(vehicleRequest(types.create(typeRequest(VehicleMobility.ROAD_ONLY, VehicleCategory.VAN, null,
                false, EnumSet.of(VehicleCapability.TOWING))).id(), "VN-" + random(), null));
        long track = ThreadLocalRandom.current().nextLong(100_000, 1_000_000);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        locations.recordManual(towerCar.id(), location(VehicleLocationKind.ON_TRACK, track, now.minusSeconds(60)));
        assertThrows(FleetRuleException.class, () -> locations.recordManual(wagon.id(), attached(van.id(), VehicleAttachment.TOWED, now)),
                "A van does not tow a wagon");
        locations.recordManual(wagon.id(), attached(towerCar.id(), VehicleAttachment.TOWED, now));
        assertThrows(FleetRuleException.class, () -> locations.recordManual(towerCar.id(), attached(van.id(), VehicleAttachment.TOWED, now)),
                "It carries another");

        List<FleetPositionResponse> board = locations.positions(null, track, null, PageRequest.of(0, 10)).content();
        assertEquals(Set.of(towerCar.code(), wagon.code()), Set.copyOf(board.stream().map(row -> row.vehicle().code()).toList()));
        FleetPositionResponse wagonRow = board.stream().filter(row -> row.vehicle().id().equals(wagon.id())).findFirst().orElseThrow();
        assertEquals(VehicleLocationKind.ATTACHED, wagonRow.position().kind());
        assertEquals(track, wagonRow.effective().trackId());
    }

    // ------------------------------------------------------------- turnos de mantenimiento

    @Test
    void aShiftPutsItsVehiclesOnTheTrackAndParksThemWhereItSays() {
        VehicleResponse towerCar = railVehicle(Set.of());
        VehicleResponse other = railVehicle(Set.of());
        UUID shiftId = UUID.randomUUID();
        Instant start = Instant.now().minus(Duration.ofHours(6)).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(Duration.ofHours(5));
        Map<String, Object> started = shiftValues(start, null, List.of(towerCar.id(), other.id()), List.of());

        shiftHandler.handle(new IncomingEvent("shift", shiftId.toString(), "started", started), context());
        shiftHandler.handle(new IncomingEvent("shift", shiftId.toString(), "started", started), context());

        assertEquals(VehicleLocationKind.ON_TRACK, vehicles.findById(towerCar.id()).position().kind());
        assertEquals(new BigDecimal("12.300"), vehicles.findById(towerCar.id()).position().kp());
        assertEquals(1, locations.history(towerCar.id(), null, null, PageRequest.of(0, 10)).content().size(), "Republished, not repeated");

        // El otro lo mueve alguien a mano despues de empezar: el cierre ya no lo toca.
        locations.recordManual(other.id(), new VehicleLocationRequest(VehicleLocationKind.IN_WORKSHOP, null, null, null, null, null, null,
                null, "Workshop", null, null, start.plus(Duration.ofHours(1))));
        Map<String, Object> parking = new LinkedHashMap<>();
        parking.put("trackId", 31);
        parking.put("kp", "4.250");
        Map<String, Object> closed = shiftValues(start, end, List.of(towerCar.id(), other.id()),
                List.of(Map.of("id", towerCar.id().toString(), "parking", parking)));
        shiftHandler.handle(new IncomingEvent("shift", shiftId.toString(), "closed", closed), context());

        VehicleResponse parked = vehicles.findById(towerCar.id());
        assertEquals(VehicleLocationKind.PARKED, parked.position().kind());
        assertEquals(31L, parked.position().trackId());
        assertEquals("mto-maintenance:shift:" + shiftId, parked.position().sourceReference());
        assertEquals(VehicleLocationKind.IN_WORKSHOP, vehicles.findById(other.id()).position().kind());
    }

    @Test
    void aShiftWithoutVehiclesOrCancelledBeforeStartingMovesNothing() {
        VehicleResponse vehicle = railVehicle(Set.of());
        shiftHandler.handle(new IncomingEvent("shift", UUID.randomUUID().toString(), "started",
                shiftValues(Instant.now().minusSeconds(60), null, List.of(), List.of())), context());
        shiftHandler.handle(new IncomingEvent("shift", UUID.randomUUID().toString(), "cancelled",
                shiftValues(null, null, List.of(vehicle.id()), List.of())), context());
        assertNull(vehicles.findById(vehicle.id()).position());
    }

    @Test
    void aRailOnlyVehicleParkedOnlyByDescriptionAfterAShiftStaysOnItsFirstTrack() {
        VehicleResponse vehicle = railVehicle(Set.of());
        UUID shiftId = UUID.randomUUID();
        Instant start = Instant.now().minus(Duration.ofHours(4)).truncatedTo(ChronoUnit.SECONDS);
        shiftHandler.handle(new IncomingEvent("shift", shiftId.toString(), "started",
                shiftValues(start, null, List.of(vehicle.id()), List.of())), context());
        Map<String, Object> closed = shiftValues(start, start.plusSeconds(3600), List.of(vehicle.id()), List.of());
        closed.put("parkingPlace", "Siding north of Herzliya");
        shiftHandler.handle(new IncomingEvent("shift", shiftId.toString(), "closed", closed), context());

        VehicleResponse parked = vehicles.findById(vehicle.id());
        assertEquals(VehicleLocationKind.PARKED, parked.position().kind());
        assertEquals("Siding north of Herzliya", parked.position().description());
        assertEquals(14L, parked.position().trackId());
    }

    /**
     * Los ejemplos que publica mto-maintenance ({@code docs/messaging/examples/shift-*.json} de alli,
     * copiados en {@code contracts/mto-maintenance/}) pasan por el manejador real: si mantenimiento
     * renombra una clave que se lee aqui, falla esto y no la posicion de un vehiculo en produccion. Un
     * cambio de esos ejemplos se copia aqui en el mismo cambio, como en mto-notification.
     */
    @Test
    void theShiftEventsAsMtoMaintenancePublishesThemMoveItsVehicles() {
        VehicleResponse towerCar = railVehicle(Set.of());
        UUID shiftId = UUID.randomUUID();

        shiftHandler.handle(contractEvent("shift-started", shiftId, towerCar.id()), context());
        VehicleResponse working = vehicles.findById(towerCar.id());
        assertEquals(VehicleLocationKind.ON_TRACK, working.position().kind());
        assertEquals(2L, working.position().trackId());
        assertEquals(0, new BigDecimal("12847.990").compareTo(working.position().kp()));
        assertEquals(6L, working.position().executionPackageId());

        shiftHandler.handle(contractEvent("shift-closed", shiftId, towerCar.id()), context());
        VehicleResponse parked = vehicles.findById(towerCar.id());
        assertEquals(VehicleLocationKind.PARKED, parked.position().kind());
        assertEquals(31L, parked.position().trackId(), "Where the shift says it was left");
        assertEquals(0, new BigDecimal("4.250").compareTo(parked.position().kp()));
        assertEquals("mto-maintenance:shift:" + shiftId, parked.position().sourceReference());

        // Un turno cancelado que llego a empezar deja el vehiculo en el aparcamiento del turno, en su via.
        VehicleResponse other = railVehicle(Set.of());
        UUID cancelledShift = UUID.randomUUID();
        shiftHandler.handle(contractEvent("shift-started", cancelledShift, other.id()), context());
        shiftHandler.handle(contractEvent("shift-cancelled", cancelledShift, other.id()), context());
        VehicleResponse back = vehicles.findById(other.id());
        assertEquals(VehicleLocationKind.PARKED, back.position().kind());
        assertEquals("Herzliya siding", back.position().description());
        assertEquals(2L, back.position().trackId());
    }

    /**
     * El dispositivo de campo de un turno dice el kp de su vehiculo ({@code mto.field.position.reported}):
     * queda en la via con origen FIELD y la referencia del turno, y el cierre lo sigue aparcando aunque la
     * hora de fin escrita sea anterior a lo ultimo que dijo el dispositivo. Lo que llega despues de salir de
     * la via, de un vehiculo desconocido o de uno que no puede estar en la via se ignora sin fallar.
     */
    @Test
    void aFieldDevicePutsItsVehicleAtItsKpAndTheCloseOfTheShiftStillParksIt() {
        VehicleResponse towerCar = railVehicle(Set.of());
        UUID shiftId = UUID.randomUUID();
        Instant start = Instant.now().minus(Duration.ofHours(6)).truncatedTo(ChronoUnit.SECONDS);
        shiftHandler.handle(new IncomingEvent("shift", shiftId.toString(), "started",
                shiftValues(start, null, List.of(towerCar.id()), List.of())), context());

        fieldHandler.handle(position(towerCar.id(), shiftId, 15, "13.100", start.plus(Duration.ofHours(2))), fieldContext());
        fieldHandler.handle(position(towerCar.id(), shiftId, 15, "12.900", start.plus(Duration.ofHours(1))), fieldContext());

        VehicleResponse moving = vehicles.findById(towerCar.id());
        assertEquals(VehicleLocationKind.ON_TRACK, moving.position().kind());
        assertEquals(15L, moving.position().trackId());
        assertEquals(0, new BigDecimal("13.100").compareTo(moving.position().kp()), "the late one stays in the history only");
        assertEquals(VehicleLocationSource.FIELD, moving.position().source());
        assertEquals("mto-maintenance:shift:" + shiftId, moving.position().sourceReference());
        assertEquals(3, locations.history(towerCar.id(), null, null, PageRequest.of(0, 10)).content().size());

        // Cerrado con una hora de fin anterior a la ultima posicion: el cierre va justo detras de ella.
        Map<String, Object> parking = new LinkedHashMap<>();
        parking.put("trackId", 31);
        parking.put("kp", "4.250");
        Map<String, Object> closed = shiftValues(start, start.plus(Duration.ofHours(1)), List.of(towerCar.id()),
                List.of(Map.of("id", towerCar.id().toString(), "parking", parking)));
        shiftHandler.handle(new IncomingEvent("shift", shiftId.toString(), "closed", closed), context());
        shiftHandler.handle(new IncomingEvent("shift", shiftId.toString(), "closed", closed), context());
        VehicleResponse parked = vehicles.findById(towerCar.id());
        assertEquals(VehicleLocationKind.PARKED, parked.position().kind());
        assertEquals(31L, parked.position().trackId());
        assertEquals(start.plus(Duration.ofHours(2)).plusMillis(1), parked.position().observedAt());
        assertEquals(4, locations.history(towerCar.id(), null, null, PageRequest.of(0, 10)).content().size(), "republished, not repeated");

        // Lo que el dispositivo diga despues no lo devuelve a la via.
        fieldHandler.handle(position(towerCar.id(), shiftId, 15, "13.500", start.plus(Duration.ofHours(3))), fieldContext());
        assertEquals(VehicleLocationKind.PARKED, vehicles.findById(towerCar.id()).position().kind());

        VehicleResponse van = vehicles.create(vehicleRequest(types.create(typeRequest(VehicleMobility.ROAD_ONLY, VehicleCategory.VAN, null,
                false, Set.of())).id(), "P-" + random(), null));
        fieldHandler.handle(position(van.id(), UUID.randomUUID(), 15, "1.000", start), fieldContext());
        assertNull(vehicles.findById(van.id()).position(), "a road vehicle is never on the track");
        fieldHandler.handle(position(UUID.randomUUID(), shiftId, 15, "1.000", start), fieldContext());
        Map<String, Object> withoutTrack = new LinkedHashMap<>(position(towerCar.id(), shiftId, 15, "1.000", start).values());
        withoutTrack.remove("trackId");
        fieldHandler.handle(new IncomingEvent("position", UUID.randomUUID().toString(), "reported", withoutTrack), fieldContext());
        assertEquals(VehicleLocationKind.PARKED, vehicles.findById(towerCar.id()).position().kind());
    }

    /**
     * El ejemplo que publica mto-field ({@code docs/messaging/examples/position-reported.json} de alli,
     * copiado en {@code contracts/mto-field/}) pasa por el manejador real con un vehiculo y un turno de
     * este test: una clave renombrada alli falla aqui.
     */
    @Test
    void thePositionAsMtoFieldPublishesItPutsTheVehicleAtItsKp() {
        VehicleResponse towerCar = railVehicle(Set.of());
        UUID shiftId = UUID.randomUUID();

        fieldHandler.handle(fieldContractEvent("position-reported", shiftId, towerCar.id()), fieldContext());

        VehicleResponse moving = vehicles.findById(towerCar.id());
        assertEquals(VehicleLocationKind.ON_TRACK, moving.position().kind());
        assertEquals(7L, moving.position().trackId());
        assertEquals(0, new BigDecimal("34.271").compareTo(moving.position().kp()));
        assertEquals(6L, moving.position().executionPackageId());
        assertEquals(Instant.parse("2026-10-10T00:12:00Z"), moving.position().observedAt());
        assertEquals(VehicleLocationSource.FIELD, moving.position().source());
        assertEquals("mto-maintenance:shift:" + shiftId, moving.position().sourceReference());
    }

    @Test
    void theDispatcherRoutesBySourceAndEntityRefusesTwoHandlersAndIgnoresTheUnknown() {
        java.util.List<String> handled = new ArrayList<>();
        com.alejandro.mtoresources.application.service.IncomingEventHandler shift = handler("mto-maintenance", "shift", handled);
        DispatchingIncomingEventHandler dispatcher = new DispatchingIncomingEventHandler(List.of(shift));

        dispatcher.handle(new IncomingEvent("shift", "1", "started", Map.of()), context());
        dispatcher.handle(new IncomingEvent("order", "2", "created", Map.of()), context());

        assertEquals(List.of("shift.started"), handled);
        assertThrows(IllegalStateException.class, () -> new DispatchingIncomingEventHandler(List.of(shift,
                handler("MTO-MAINTENANCE", "Shift", handled))));
    }

    // ------------------------------------------------------------------------ historial

    @Test
    void aPersonAUnitAndAVehicleKeepTheirHistoryWithWhoChangedThem() {
        PersonResponse person = person(PersonCategory.WORKER);
        people.patch(person.id(), MergePatch.of(new PersonUpdateRequest(null, null, PersonCategory.TEAM_LEADER, null, null, null, null,
                null, null, null, null)));

        var revisions = people.findRevisions(person.id(), PageRequest.of(0, 10)).content();
        assertEquals(2, revisions.size());
        assertEquals(PersonCategory.TEAM_LEADER, revisions.getFirst().entity().category(), "The most recent first");
        assertEquals("recursos.gestor", revisions.getFirst().revision().author());

        OrganizationUnitResponse supervision = supervision(Set.of(3L));
        assertEquals(1, units.findRevisions(supervision.id(), PageRequest.of(0, 10)).content().size());
        VehicleResponse vehicle = railVehicle(Set.of());
        vehicles.patch(vehicle.id(), MergePatch.of(vehicleUpdate(VehicleOperationalStatus.LIMITED, "Crane out of order")));
        assertEquals(2, vehicles.findRevisions(vehicle.id(), PageRequest.of(0, 10)).content().size());
    }

    /**
     * Un historial vacio es el 404 del agregado, con el nombre que dan los demas 404, que es de donde
     * sale el prefijo del codigo: una unidad sin revisiones es UNI-404, como la que no existe, y no
     * APP-404 por llamarse la clase OrganizationUnit.
     */
    @Test
    void anEmptyHistoryIsTheNotFoundOfItsAggregate() {
        NotFoundException unit = assertThrows(NotFoundException.class, () -> units.findRevisions(UUID.randomUUID(), PageRequest.of(0, 10)));
        assertEquals("Organization unit", unit.getAggregate());
        assertEquals("Person", assertThrows(NotFoundException.class,
                () -> people.findRevisions(UUID.randomUUID(), PageRequest.of(0, 10))).getAggregate());
        assertEquals("Vehicle", assertThrows(NotFoundException.class,
                () -> vehicles.findRevisions(UUID.randomUUID(), PageRequest.of(0, 10))).getAggregate());
        assertEquals("UNI-404", new GlobalExceptionHandler().handleNotFound(unit, new MockHttpServletRequest()).getBody().errorCode());
    }

    // -------------------------------------------------------------------- republicacion

    @Test
    void republishingAnExecutionPackagePublishesItsTeamsPeopleAndVehicles() {
        long executionPackage = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        VehicleResponse vehicle = railVehicle(Set.of());
        OrganizationUnitResponse supervision = supervision(Set.of());
        units.create(new OrganizationUnitRequest("T" + random(), "Team", OrganizationUnitKind.TEAM, null, supervision.id(),
                person(PersonCategory.TEAM_LEADER).id(), Set.of(executionPackage), vehicle.id(), linemen(3), null));
        events.clear();

        RepublishResult result = republish.republish(executionPackage);

        assertEquals(new RepublishResult(executionPackage, 5, 1, 1), result, "Leader, three linemen and the supervisor");
        assertEquals(5, events.names().stream().filter("person.updated"::equals).count());
        assertEquals(1, events.names().stream().filter("team.updated"::equals).count());
        assertEquals(1, events.names().stream().filter("vehicle.updated"::equals).count());
        assertEquals(events.operationIds.size(), Set.copyOf(events.operationIds).size(), "A new operationId for every event");
        assertTrue(events.published.stream().allMatch(event -> Boolean.TRUE.equals(event.values().get("republished"))),
                "Everything it tells again says so, so that nobody is notified of it");
        assertTrue(events.published.stream().filter(event -> event.entityName().equals("vehicle"))
                .allMatch(event -> Boolean.FALSE.equals(event.values().get("availabilityChanged"))));
    }

    // ---------------------------------------------------------------- operaciones de fondo

    @Test
    void aSlowOperationAnswersPendingAndFinishesInTheBackgroundWithTheCallersIdentity() throws Exception {
        authenticateAs("ops.someone");
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> actor = new AtomicReference<>();

        BudgetedOperations.Outcome<Map<String, Object>> outcome = operations.run("TEST", "test-" + random(), 200, Duration.ofMillis(50), () -> {
            await(release);
            actor.set(AuditActorResolver.currentActor());
            return Map.of("done", true);
        });

        assertFalse(outcome.completed());
        assertNotNull(outcome.operation().id());
        release.countDown();
        OperationResponse finished = waitFor(outcome.operation().id());
        assertEquals(OperationStatus.SUCCEEDED, finished.status());
        assertEquals(200, finished.resultStatus());
        assertEquals(Map.of("done", true), finished.result());
        assertEquals("ops.someone", actor.get(), "The SecurityContext reaches the virtual thread");
        assertEquals("ops.someone", finished.requestedBy());
        authenticateAs("someone.else");
        assertThrows(NotFoundException.class, () -> operations.find(outcome.operation().id()), "Only to whom asked for it");
    }

    @Test
    void aQuickOperationAnswersWithItsResultAndAFailureIsTheSameAsTheSynchronousCall() {
        authenticateAs("ops.someone");
        BudgetedOperations.Outcome<String> quick = operations.run("TEST", null, 201, Duration.ofSeconds(5), () -> "ok");
        assertTrue(quick.completed());
        assertEquals("ok", quick.result());
        assertThrows(OrganizationRuleException.class, () -> operations.run("TEST", null, 200, Duration.ofSeconds(5), () -> {
            throw new OrganizationRuleException("no");
        }));
    }

    @Test
    void twoOperationsOfTheSameScopeDoNotRunAtOnceAndTheCapSaysTooMany() throws Exception {
        authenticateAs("ops.someone");
        String scope = "test-" + random();
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ids.add(operations.run("TEST", scope, 200, Duration.ZERO, () -> {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                await(release);
                running.decrementAndGet();
                return "ok";
            }).operation().id());
        }
        // El tope del contexto de este test es 4: dos mas llenan, la quinta sobra.
        List<UUID> fillers = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            fillers.add(operations.run("TEST", null, 200, Duration.ZERO, () -> {
                await(release);
                return "ok";
            }).operation().id());
        }
        assertThrows(TooManyOperationsException.class, () -> operations.run("TEST", null, 200, Duration.ZERO, () -> "late"));

        Thread.sleep(300);
        release.countDown();
        for (UUID id : ids) {
            assertEquals(OperationStatus.SUCCEEDED, waitFor(id).status());
        }
        fillers.forEach(this::waitFor);
        assertEquals(1, maxRunning.get(), "One at a time in a scope");
    }

    @Test
    void anOperationWhoseHeartbeatStoppedIsFailed() throws Exception {
        authenticateAs("ops.someone");
        CountDownLatch release = new CountDownLatch(1);
        UUID id = operations.run("TEST", "test-" + random(), 200, Duration.ZERO, () -> {
            await(release);
            return "late";
        }).operation().id();
        waitUntil(() -> operations.find(id).status() == OperationStatus.RUNNING);
        jdbc.update("update background_operation set heartbeat_at = now() - interval '1 hour' where id = ?", id);

        assertTrue(operations.failAbandoned() >= 1);
        OperationResponse failed = operations.find(id);
        assertEquals(OperationStatus.FAILED, failed.status());
        assertEquals("OPS-410", failed.error().errorCode());
        release.countDown();
    }

    // ------------------------------------------------------------------------ fixtures

    private static Set<UUID> ids(List<PersonResponse> people) {
        return people.stream().map(PersonResponse::id).collect(Collectors.toSet());
    }

    private PersonResponse person(PersonCategory category) {
        String suffix = random();
        return people.create(new PersonRequest("P" + suffix, "Test", category, null, "user." + suffix, null, null, null, null));
    }

    private List<UUID> linemen(int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(person(PersonCategory.WORKER).id());
        }
        return ids;
    }

    private OrganizationUnitResponse supervision(Set<Long> executionPackages) {
        PersonResponse supervisor = person(PersonCategory.SUPERVISOR);
        return units.create(new OrganizationUnitRequest("S" + random(), "Supervision", OrganizationUnitKind.SUPERVISION, null,
                MAINTENANCE_DEPARTMENT, supervisor.id(), executionPackages, null, null, null));
    }

    private OrganizationUnitResponse team(UUID parent, UUID leader, List<UUID> linemen, Set<Long> executionPackages) {
        return units.create(teamRequest(parent, leader, linemen, executionPackages));
    }

    private static OrganizationUnitRequest teamRequest(UUID parent, UUID leader, List<UUID> linemen, Set<Long> executionPackages) {
        return new OrganizationUnitRequest("T" + random(), "Team", OrganizationUnitKind.TEAM, null, parent, leader, executionPackages,
                null, linemen, null);
    }

    private static OrganizationUnitUpdateRequest updateRequest(String name, UUID head, Set<Long> executionPackages) {
        return new OrganizationUnitUpdateRequest(name, null, head, executionPackages, null, null, null, null, null);
    }

    private static VehicleTypeRequest typeRequest(VehicleMobility mobility, VehicleCategory category, RailGearType railGear,
                                                  boolean transportMode, Set<VehicleCapability> capabilities) {
        return new VehicleTypeRequest("VT" + random(), "Model", null, null, mobility, category, true, false, null, null, null, null, null,
                null, null, 8, null, null, null, null, null, null, null, null, transportMode, railGear, null, null, null, null,
                capabilities, null, null, null, null, null, null, null);
    }

    private VehicleResponse railVehicle(Set<VehicleCapability> capabilities) {
        VehicleTypeResponse type = types.create(typeRequest(VehicleMobility.RAIL_ONLY, VehicleCategory.CATENARY_TOWER_CAR, null, false,
                capabilities));
        return vehicles.create(vehicleRequest(type.id(), null, null));
    }

    private static VehicleRequest vehicleRequest(UUID typeId, String plate, String railNumber) {
        return new VehicleRequest("V" + random(), "Vehicle", typeId, plate, railNumber, null, null, null, null, null, null, null, null, null);
    }

    private static com.alejandro.mtoresources.application.dto.fleet.VehicleUpdateRequest vehicleUpdate(VehicleOperationalStatus status,
                                                                                                         String reason) {
        return new com.alejandro.mtoresources.application.dto.fleet.VehicleUpdateRequest(null, null, null, null, null, null, null, null,
                status, reason, null, null, null, null, null);
    }

    private static VehicleLocationRequest location(VehicleLocationKind kind, Long trackId, Instant observedAt) {
        return new VehicleLocationRequest(kind, null, null, trackId, null, null, null, null, null, null, null, observedAt);
    }

    private static VehicleLocationRequest attached(UUID carrier, VehicleAttachment attachment, Instant observedAt) {
        return new VehicleLocationRequest(VehicleLocationKind.ATTACHED, null, null, null, null, null, null, null, null, carrier, attachment,
                observedAt);
    }

    private static Map<String, Object> shiftValues(Instant start, Instant end, List<UUID> vehicleIds, List<Map<String, Object>> vehicles) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", "SH-" + random());
        values.put("trackIds", List.of(14, 15));
        values.put("executionPackageId", 6);
        values.put("startKp", 12.300);
        values.put("actualStart", start == null ? null : start.toString());
        values.put("actualEnd", end == null ? null : end.toString());
        values.put("vehicleIds", vehicleIds.stream().map(UUID::toString).toList());
        values.put("vehicles", vehicles);
        return values;
    }

    private static com.alejandro.mtoresources.application.service.IncomingEventHandler handler(String source, String entity, List<String> handled) {
        return new com.alejandro.mtoresources.application.service.IncomingEventHandler() {
            @Override
            public String sourceService() {
                return source;
            }

            @Override
            public String entityName() {
                return entity;
            }

            @Override
            public void handle(IncomingEvent event, IncomingEventContext context) {
                handled.add(event.entityName() + "." + event.eventName());
            }
        };
    }

    /** Un ejemplo de mto-maintenance con su turno y su vehiculo cambiados por los de este test. */
    private static IncomingEvent contractEvent(String name, UUID shiftId, UUID vehicleId) {
        try (java.io.InputStream json = BusinessLayerTest.class.getResourceAsStream("/contracts/mto-maintenance/" + name + ".json")) {
            String text = new String(java.util.Objects.requireNonNull(json, name).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("f0000000-0000-4000-8000-000000000002", vehicleId.toString());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) tools.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(text, Map.class).get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) data.get("values");
            return new IncomingEvent((String) data.get("entityName"), shiftId.toString(), (String) data.get("eventName"), values);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static IncomingEventContext context() {
        return new IncomingEventContext("mto-maintenance", null, UUID.randomUUID().toString());
    }

    /** El ejemplo de mto-field con su turno y su vehiculo cambiados por los de este test. */
    private static IncomingEvent fieldContractEvent(String name, UUID shiftId, UUID vehicleId) {
        try (java.io.InputStream json = BusinessLayerTest.class.getResourceAsStream("/contracts/mto-field/" + name + ".json")) {
            String text = new String(java.util.Objects.requireNonNull(json, name).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("f0000000-0000-4000-8000-000000000007", vehicleId.toString())
                    .replace("90000000-0000-4000-8000-000000000001", shiftId.toString());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) tools.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(text, Map.class).get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) data.get("values");
            return new IncomingEvent((String) data.get("entityName"), (String) data.get("entityId"), (String) data.get("eventName"), values);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static IncomingEventContext fieldContext() {
        return new IncomingEventContext("mto-field", null, UUID.randomUUID().toString());
    }

    /** Una posicion como la publica mto-field: el vehiculo del dispositivo, su turno, su via y su kp. */
    private static IncomingEvent position(UUID vehicleId, UUID shiftId, long trackId, String kp, Instant observedAt) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", "PO-000012");
        values.put("shiftId", shiftId.toString());
        values.put("deviceId", "dev-1");
        values.put("vehicleId", vehicleId.toString());
        values.put("trackId", trackId);
        values.put("kp", kp);
        values.put("executionPackageId", 6);
        values.put("observedAt", observedAt.toString());
        return new IncomingEvent("position", UUID.randomUUID().toString(), "reported", values);
    }

    private static void authenticateAs(String username) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(UUID.randomUUID().toString())
                .claim("preferred_username", username).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), username));
    }

    private OperationResponse waitFor(UUID id) {
        waitUntil(() -> operations.find(id).status().isFinished());
        return operations.find(id);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met in 15 s");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Never released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    /** Lo que se publica, en el orden en que se publica. */
    static final class RecordingEventPublisher implements DomainEventPublisher {
        final List<DomainEvent> published = Collections.synchronizedList(new ArrayList<>());
        final List<UUID> operationIds = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(DomainEvent event) {
            publish(UUID.randomUUID(), event);
        }

        @Override
        public void publish(UUID operationId, DomainEvent event) {
            operationIds.add(operationId);
            published.add(event);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        void clear() {
            published.clear();
            operationIds.clear();
        }

        List<String> names() {
            return published.stream().map(event -> event.entityName() + "." + event.eventName()).toList();
        }

        DomainEvent only(String name) {
            List<DomainEvent> matching = published.stream().filter(event -> (event.entityName() + "." + event.eventName()).equals(name)).toList();
            assertEquals(1, matching.size(), "Published: " + names());
            return matching.getFirst();
        }
    }
}
