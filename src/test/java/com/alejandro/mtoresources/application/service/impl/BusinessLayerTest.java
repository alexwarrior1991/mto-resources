package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.expiry.ExpiryWatchResult;
import com.alejandro.mtoresources.application.dto.fleet.FleetPositionResponse;
import com.alejandro.mtoresources.application.dto.fleet.TrackAccessPointResponse;
import com.alejandro.mtoresources.application.dto.fleet.TrackAccessPointSummary;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentUpdateRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeUpdateRequest;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentRequest;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentResponse;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentUpdateRequest;
import com.alejandro.mtoresources.application.dto.messaging.DomainEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtoresources.application.dto.messaging.MasterDataChangedMessage;
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
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationRequest;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationResponse;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationUpdateRequest;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeRequest;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeResponse;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeSummary;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeUpdateRequest;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.FleetRuleException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.OrganizationRuleException;
import com.alejandro.mtoresources.application.exception.QualificationRuleException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.TooManyOperationsException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.service.BudgetedOperations;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.ExpiryWatchService;
import com.alejandro.mtoresources.application.service.InstrumentService;
import com.alejandro.mtoresources.application.service.OrganizationUnitService;
import com.alejandro.mtoresources.application.service.PersonQualificationService;
import com.alejandro.mtoresources.application.service.PersonService;
import com.alejandro.mtoresources.application.service.QualificationTypeService;
import com.alejandro.mtoresources.application.service.RepublishService;
import com.alejandro.mtoresources.application.service.TrackAccessPointService;
import com.alejandro.mtoresources.application.service.VehicleDocumentService;
import com.alejandro.mtoresources.application.service.VehicleLocationService;
import com.alejandro.mtoresources.application.service.VehicleService;
import com.alejandro.mtoresources.application.service.VehicleTypeService;
import com.alejandro.mtoresources.configuration.AuditActorResolver;
import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.InstrumentKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonEmployer;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RailGearType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleAttachment;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocumentKind;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
    /** Los tipos de cualificacion sembrados por V8. */
    private static final UUID LICENCE_B = UUID.fromString("0195b000-0000-7000-8000-000000000101");
    private static final UUID LICENCE_CE = UUID.fromString("0195b000-0000-7000-8000-000000000103");
    private static final UUID RAIL_DRIVER = UUID.fromString("0195b000-0000-7000-8000-000000000104");
    private static final UUID RR_OPERATOR = UUID.fromString("0195b000-0000-7000-8000-000000000105");

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
    private QualificationTypeService qualificationTypes;
    @Autowired
    private PersonQualificationService personQualifications;
    @Autowired
    private VehicleDocumentService vehicleDocuments;
    @Autowired
    private InstrumentService instruments;
    @Autowired
    private ExpiryWatchService expiryWatch;
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
    private TrackAccessPointMasterDataHandler accessPointHandler;
    @Autowired
    private TrackAccessPointService accessPoints;
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
                null, null, null, null, null, null, null, null)));
        DomainEvent event = events.only("vehicle.updated");
        assertEquals(vehicle.id().toString(), event.entityId());
        assertEquals(9, ((Map<?, ?>) event.values().get("type")).get("seats"));
        assertEquals(false, event.values().get("availabilityChanged"), "Its model changed, not whether it can work");
        assertEquals(false, event.values().get("republished"));
    }

    // ------------------------------------------------- documentos y habilitacion del conductor

    @Test
    void aNewModelAsksForTheDriverQualificationOfItsMobilityUnlessItSaysAnother() {
        assertEquals("RAIL-DRIVER", types.create(driverTypeRequest(VehicleMobility.RAIL_ONLY, VehicleCategory.CATENARY_TOWER_CAR, true,
                null, null, null)).requiredDriverQualification().code());
        assertNull(types.create(driverTypeRequest(VehicleMobility.RAIL_ONLY, VehicleCategory.RAIL_WAGON, false, null, null, null))
                .requiredDriverQualification(), "A wagon is not driven");
        assertEquals("RR-OPERATOR", types.create(driverTypeRequest(VehicleMobility.ROAD_RAIL, VehicleCategory.ROAD_RAIL_TRUCK, true,
                RailGearType.RAIL_WHEEL_DRIVE, null, null)).requiredDriverQualification().code());
        assertEquals("LIC-C", types.create(driverTypeRequest(VehicleMobility.ROAD_ONLY, VehicleCategory.CRANE_TRUCK, true, null,
                DrivingLicence.C, null)).requiredDriverQualification().code());
        assertNull(types.create(driverTypeRequest(VehicleMobility.ROAD_ONLY, VehicleCategory.VAN, true, null, null, null))
                .requiredDriverQualification(), "A road model that asks for no licence");

        QualificationTypeResponse mewp = qualificationType(QualificationKind.VEHICLE_OPERATION, null, null);
        VehicleTypeResponse platform = types.create(driverTypeRequest(VehicleMobility.ROAD_RAIL, VehicleCategory.ROAD_RAIL_MEWP, true,
                RailGearType.FRICTION_DRIVE, null, mewp.id()));
        assertEquals(new QualificationTypeSummary(mewp.id(), mewp.code(), mewp.name(), QualificationKind.VEHICLE_OPERATION),
                platform.requiredDriverQualification(), "What it says wins");
        assertEquals("QTY-404", new GlobalExceptionHandler().handleNotFound(assertThrows(NotFoundException.class, () -> types.create(
                driverTypeRequest(VehicleMobility.ROAD_ONLY, VehicleCategory.VAN, true, null, null, UUID.randomUUID()))),
                new MockHttpServletRequest()).getBody().errorCode());
        QualificationTypeResponse retired = qualificationType(QualificationKind.VEHICLE_OPERATION, null, null);
        qualificationTypes.deactivate(retired.id());
        assertThrows(QualificationRuleException.class, () -> types.create(driverTypeRequest(VehicleMobility.ROAD_ONLY, VehicleCategory.VAN,
                true, null, null, retired.id())), "Nobody can get a retired one any more");

        // Cambiarla republica sus vehiculos con el codigo nuevo; vaciarla, sin ninguno.
        VehicleResponse vehicle = vehicles.create(vehicleRequest(platform.id(), "RR-" + random(), null));
        events.clear();
        types.patch(platform.id(), MergePatch.of(typeUpdate(RR_OPERATOR, null)));
        assertEquals("RR-OPERATOR", ((Map<?, ?>) events.only("vehicle.updated").values().get("type")).get("requiredDriverQualification"));
        events.clear();
        VehicleTypeResponse none = types.patch(platform.id(), new MergePatch<>(typeUpdate(null, null),
                Set.of("requiredDriverQualificationTypeId")));
        assertNull(none.requiredDriverQualification());
        DomainEvent cleared = events.only("vehicle.updated");
        assertEquals(vehicle.id().toString(), cleared.entityId());
        assertTrue(((Map<?, ?>) cleared.values().get("type")).containsKey("requiredDriverQualification"));
        assertNull(((Map<?, ?>) cleared.values().get("type")).get("requiredDriverQualification"));
    }

    @Test
    void aVehicleHasOneDocumentOfEachKindAndEveryChangePublishesTheVehicleWithAllOfThem() {
        VehicleResponse vehicle = railVehicle(Set.of());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        events.clear();

        VehicleDocumentResponse inspection = vehicleDocuments.create(vehicle.id(), new VehicleDocumentRequest(
                VehicleDocumentKind.RAIL_INSPECTION, " RI-118 ", today.minusYears(1), today.plusDays(60), null));
        assertEquals("RI-118", inspection.reference());
        assertEquals(vehicle.id(), inspection.vehicleId());
        assertFalse(inspection.expired());
        DomainEvent first = events.only("vehicle.updated");
        assertEquals(false, first.values().get("availabilityChanged"), "A document does not change whether it can work");
        assertEquals(List.of(documentValues("RAIL_INSPECTION", "RI-118", today.plusDays(60))), first.values().get("documents"));

        events.clear();
        VehicleDocumentResponse insurance = vehicleDocuments.create(vehicle.id(), new VehicleDocumentRequest(VehicleDocumentKind.INSURANCE,
                "POL-7", null, today.minusDays(1), null));
        assertTrue(insurance.expired());
        assertEquals(List.of("INSURANCE", "RAIL_INSPECTION"), ((List<?>) events.only("vehicle.updated").values().get("documents")).stream()
                .map(item -> ((Map<?, ?>) item).get("kind")).toList(), "By kind, the expired one too");
        assertEquals(List.of(VehicleDocumentKind.INSURANCE, VehicleDocumentKind.RAIL_INSPECTION),
                vehicleDocuments.findAll(vehicle.id()).stream().map(VehicleDocumentResponse::kind).toList());

        DuplicateCodeException twice = assertThrows(DuplicateCodeException.class, () -> vehicleDocuments.create(vehicle.id(),
                new VehicleDocumentRequest(VehicleDocumentKind.INSURANCE, null, null, today.plusYears(1), null)));
        assertEquals("VDO-409", new GlobalExceptionHandler().handleDuplicateCode(twice, new MockHttpServletRequest()).getBody().errorCode());
        assertThrows(ValidationException.class, () -> vehicleDocuments.create(vehicle.id(), new VehicleDocumentRequest(
                VehicleDocumentKind.CRANE_CERTIFICATE, null, today, today.minusDays(1), null)), "It expires before it was issued");
        assertThrows(ValidationException.class, () -> vehicleDocuments.patch(vehicle.id(), insurance.id(), new MergePatch<>(
                new VehicleDocumentUpdateRequest(null, null, null, null, null), Set.of("expiresOn"))), "A document always expires");

        // Renovarlo es cambiar su caducidad.
        events.clear();
        VehicleDocumentResponse renewed = vehicleDocuments.patch(vehicle.id(), insurance.id(), new MergePatch<>(
                new VehicleDocumentUpdateRequest("POL-8", today, today.plusYears(1), null, insurance.version()), Set.of("notes")));
        assertFalse(renewed.expired());
        assertEquals(1, events.names().size());
        assertThrows(StaleVersionException.class, () -> vehicleDocuments.patch(vehicle.id(), insurance.id(), MergePatch.of(
                new VehicleDocumentUpdateRequest("X", null, null, null, insurance.version()))));
        VehicleResponse other = railVehicle(Set.of());
        assertEquals("VDO-404", new GlobalExceptionHandler().handleNotFound(assertThrows(NotFoundException.class,
                () -> vehicleDocuments.delete(other.id(), insurance.id())), new MockHttpServletRequest()).getBody().errorCode());

        // Cambiar el vehiculo tambien cuenta sus documentos.
        events.clear();
        vehicles.patch(vehicle.id(), MergePatch.of(vehicleUpdate(VehicleOperationalStatus.LIMITED, "Crane out of order")));
        assertEquals(2, ((List<?>) events.only("vehicle.updated").values().get("documents")).size());

        events.clear();
        vehicleDocuments.delete(vehicle.id(), inspection.id());
        assertEquals(List.of("INSURANCE"), ((List<?>) events.only("vehicle.updated").values().get("documents")).stream()
                .map(item -> ((Map<?, ?>) item).get("kind")).toList());
        assertEquals(List.of(0, 2), jdbc.queryForList("select revtype from vehicle_document_aud where id = ? order by rev", Integer.class,
                inspection.id()), "Created and deleted, with what it was");
        assertEquals(2, jdbc.queryForObject("select count(*) from vehicle_document_aud where id = ?", Integer.class, insurance.id()));
    }

    // --------------------------------------------------------------------------- instrumentos

    @Test
    void anInstrumentIsRetiredNeverDeletedAndKeepsItsHistory() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String tag = random();
        String code = "pt-" + random();
        events.clear();

        InstrumentResponse pole = instruments.create(new InstrumentRequest(code, " Pole " + tag + " ", InstrumentKind.EARTHING_POLE,
                " EP-1 ", today.minusYears(1), today.plusDays(200), null));
        assertEquals(code.toUpperCase(Locale.ROOT), pole.code());
        assertEquals("Pole " + tag, pole.name());
        assertEquals("EP-1", pole.serialNumber());
        assertFalse(pole.calibrationExpired());
        DomainEvent created = events.only("instrument.created");
        assertEquals(List.of("code", "name", "kind", "serialNumber", "calibratedOn", "calibrationExpiresOn", "active", "version",
                "republished"), List.copyOf(created.values().keySet()));
        assertEquals(today.plusDays(200).toString(), created.values().get("calibrationExpiresOn"));
        assertEquals("EARTHING_POLE", created.values().get("kind"));

        DuplicateCodeException duplicate = assertThrows(DuplicateCodeException.class, () -> instruments.create(new InstrumentRequest(code,
                "Again", InstrumentKind.OTHER, null, null, null, null)));
        assertEquals("INT-409", new GlobalExceptionHandler().handleDuplicateCode(duplicate, new MockHttpServletRequest()).getBody().errorCode());
        assertThrows(ValidationException.class, () -> instruments.create(new InstrumentRequest("I" + random(), "Late", InstrumentKind.OTHER,
                null, today, today.minusDays(1), null)), "Its calibration expires before it was made");

        InstrumentResponse detector = instruments.create(new InstrumentRequest("DT-" + random(), "Detector " + tag,
                InstrumentKind.VOLTAGE_DETECTOR, null, today.minusYears(1), today.minusDays(1), null));
        assertTrue(detector.calibrationExpired());
        InstrumentResponse uncalibrated = instruments.create(new InstrumentRequest("M-" + random(), "Gauge " + tag, InstrumentKind.MEASURING,
                null, null, null, null));
        assertFalse(uncalibrated.calibrationExpired(), "Without a calibration nothing expires");
        assertEquals(Set.of(detector.id()), instrumentIds(instruments.search(tag, null, null, today, PageRequest.of(0, 10)).content()),
                "Expiring before today: never one without a calibration");
        assertEquals(Set.of(pole.id(), detector.id()), instrumentIds(instruments.search(tag, null, null, today.plusDays(201),
                PageRequest.of(0, 10)).content()));
        assertEquals(Set.of(pole.id()), instrumentIds(instruments.search(tag, InstrumentKind.EARTHING_POLE, true, null,
                PageRequest.of(0, 10)).content()));

        // Recalibrarlo es cambiar sus fechas.
        events.clear();
        InstrumentResponse recalibrated = instruments.patch(detector.id(), MergePatch.of(new InstrumentUpdateRequest(null, null, null, today,
                today.plusYears(1), null, null, detector.version())));
        assertFalse(recalibrated.calibrationExpired());
        assertEquals(today.plusYears(1).toString(), events.only("instrument.updated").values().get("calibrationExpiresOn"));
        assertThrows(StaleVersionException.class, () -> instruments.patch(detector.id(), MergePatch.of(new InstrumentUpdateRequest("Late",
                null, null, null, null, null, null, detector.version()))));
        assertNull(instruments.patch(detector.id(), new MergePatch<>(new InstrumentUpdateRequest(null, null, null, null, null, null, null,
                null), Set.of("calibratedOn", "calibrationExpiresOn"))).calibrationExpiresOn());
        assertThrows(ValidationException.class, () -> instruments.patch(detector.id(), new MergePatch<>(new InstrumentUpdateRequest(null,
                null, null, null, null, null, null, null), Set.of("kind"))));

        // Se retira una vez; lo que se cambia de uno retirado es un updated, y retirarlo otra vez no dice nada.
        events.clear();
        instruments.patch(pole.id(), MergePatch.of(new InstrumentUpdateRequest(null, null, null, null, null, null, false, null)));
        assertEquals(false, events.only("instrument.deactivated").values().get("active"));
        events.clear();
        instruments.patch(pole.id(), MergePatch.of(new InstrumentUpdateRequest(null, null, null, null, null, "In the workshop", null, null)));
        events.only("instrument.updated");
        events.clear();
        instruments.deactivate(pole.id());
        assertEquals(List.of(), events.names());
        assertTrue(instruments.patch(pole.id(), MergePatch.of(new InstrumentUpdateRequest(null, null, null, null, null, null, true, null)))
                .active());
        assertEquals(true, events.only("instrument.updated").values().get("active"));

        var revisions = instruments.findRevisions(pole.id(), PageRequest.of(0, 10)).content();
        assertEquals(4, revisions.size(), "Created, retired, its notes and back");
        assertTrue(revisions.getFirst().entity().active(), "The most recent first");
        assertEquals("recursos.gestor", revisions.getLast().revision().author());
        assertEquals("INT-404", new GlobalExceptionHandler().handleNotFound(assertThrows(NotFoundException.class,
                () -> instruments.findRevisions(UUID.randomUUID(), PageRequest.of(0, 10))), new MockHttpServletRequest()).getBody().errorCode());
    }

    // ------------------------------------------------------------------------ ubicacion

    @Test
    void aRailOnlyVehicleIsNeverOnTheRoadAndALateLocationDoesNotMoveIt() {
        VehicleResponse vehicle = railVehicle(Set.of());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertThrows(FleetRuleException.class, () -> locations.recordManual(vehicle.id(), location(VehicleLocationKind.ON_ROAD, null, now)));
        assertThrows(FleetRuleException.class, () -> locations.recordManual(vehicle.id(),
                new VehicleLocationRequest(VehicleLocationKind.PARKED, null, null, null, null, null, null, null, null, "A car park", null, null, now)));
        assertThrows(ValidationException.class, () -> locations.recordManual(vehicle.id(), location(VehicleLocationKind.ON_TRACK, 14L,
                now.plus(Duration.ofHours(1)))), "In the future");

        locations.recordManual(vehicle.id(), location(VehicleLocationKind.ON_TRACK, 14L, now));
        locations.recordManual(vehicle.id(), new VehicleLocationRequest(VehicleLocationKind.PARKED, null, null, 15L, new BigDecimal("12.400"),
                null, null, null, null, null, null, null, now.minus(Duration.ofHours(2))));

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
                null, null, false, null, null, null, null, null, Set.of(), null, null, null, null, null, null, null, null));
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
                null, null, "Workshop", null, null, start.plus(Duration.ofHours(1))));
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

    // ------------------------------------------------------- puntos de acceso a la via

    /**
     * La instantanea de los puntos de acceso de mto-configuration: un upsert con la marca de agua de
     * {@code sequenceNumber} dentro de la sentencia. Lo anterior a lo guardado no cambia nada, lo que llega sin
     * secuencia se aplica y conserva la guardada, y un {@code DELETED} desactiva y avanza la marca, tambien de
     * un acceso que no se conocia (para que un alta tardia no lo resucite).
     */
    @Test
    void aTrackAccessPointIsKeptWithItsWatermarkAndADeletionDisablesIt() {
        long id = randomId();
        long track = randomId();

        accessPointHandler.handle(accessPointEvent("created", id, accessPointValues(id, "Access A", track, 14L, "1200.000", "PREPARED_ACCESS",
                true, "12.500", "40.000", true)), masterDataContext(100L));
        assertEquals("Access A", onlyAccessPoint(track, id).name());

        accessPointHandler.handle(accessPointEvent("updated", id, accessPointValues(id, "Access B", track, 14L, "1200.000", "PREPARED_ACCESS",
                true, "12.500", "40.000", true)), masterDataContext(90L));
        assertEquals("Access A", onlyAccessPoint(track, id).name(), "Older than what is kept");

        accessPointHandler.handle(accessPointEvent("updated", id, accessPointValues(id, "Access C", track, 14L, "1300.500", "LEVEL_CROSSING",
                false, null, null, true)), masterDataContext(110L));
        TrackAccessPointResponse updated = onlyAccessPoint(track, id);
        assertEquals("Access C", updated.name());
        assertEquals("LEVEL_CROSSING", updated.kind());
        assertFalse(updated.roadRailAccess());
        assertNull(updated.maxGrossWeightT(), "A limit that is no longer known");
        assertEquals(0, new BigDecimal("1300.500").compareTo(updated.kp()));

        accessPointHandler.handle(accessPointEvent("updated", id, accessPointValues(id, "Access D", track, 14L, "1300.500", "LEVEL_CROSSING",
                false, null, null, true)), masterDataContext(null));
        assertEquals("Access D", onlyAccessPoint(track, id).name(), "Without a sequence it applies");
        assertEquals(110L, jdbc.queryForObject("select source_sequence_number from track_access_point where id = ?", Long.class, id),
                "and keeps the stored watermark");
        accessPointHandler.handle(accessPointEvent("updated", id, accessPointValues(id, "Access E", track, 14L, "1300.500", "LEVEL_CROSSING",
                false, null, null, true)), masterDataContext(105L));
        assertEquals("Access D", onlyAccessPoint(track, id).name());

        accessPointHandler.handle(accessPointEvent("deleted", id, accessPointValues(id, "Access D", track, 14L, "1300.500", "LEVEL_CROSSING",
                false, null, null, true)), masterDataContext(120L));
        assertFalse(onlyAccessPoint(track, id).enabled(), "A deletion disables it");
        assertTrue(accessPoints.list(track, null, true).isEmpty());
        assertEquals(1, accessPoints.list(track, null, false).size());
        accessPointHandler.handle(accessPointEvent("updated", id, accessPointValues(id, "Access D", track, 14L, "1300.500", "LEVEL_CROSSING",
                false, null, null, true)), masterDataContext(115L));
        assertFalse(onlyAccessPoint(track, id).enabled(), "A late update does not bring it back");
        assertEquals(120L, jdbc.queryForObject("select source_sequence_number from track_access_point where id = ?", Long.class, id));

        // Un borrado de uno que no se conocia: queda dado de alta desactivado, y su alta tardia no lo resucita.
        long unknown = randomId();
        accessPointHandler.handle(accessPointEvent("deleted", unknown, accessPointValues(unknown, "Gone", track, 14L, null, "DEPOT", true,
                null, null, true)), masterDataContext(500L));
        accessPointHandler.handle(accessPointEvent("created", unknown, accessPointValues(unknown, "Gone", track, 14L, null, "DEPOT", true,
                null, null, true)), masterDataContext(400L));
        assertFalse(onlyAccessPoint(track, unknown).enabled());
        // Sin sus values, un borrado solo desactiva lo que ya habia: de uno desconocido no queda nada.
        long withoutValues = randomId();
        accessPointHandler.handle(new IncomingEvent("track-access-point", String.valueOf(withoutValues), "deleted", Map.of()),
                masterDataContext(600L));
        assertEquals(0, jdbc.queryForObject("select count(*) from track_access_point where id = ?", Integer.class, withoutValues));
        assertThrows(IllegalArgumentException.class, () -> accessPointHandler.handle(new IncomingEvent("track-access-point",
                String.valueOf(withoutValues), "updated", Map.of("id", withoutValues)), masterDataContext(601L)), "An update without its values");
    }

    @Test
    void theAccessPointsOfATrackAreListedByKpAndFilteredByPackageAndState() {
        long track = randomId();
        long executionPackage = randomId();
        long far = randomId();
        long near = randomId();
        long withoutKp = randomId();
        long disabled = randomId();
        accessPointHandler.handle(accessPointEvent("created", far, accessPointValues(far, "Far", track, executionPackage, "2000.000",
                "PREPARED_ACCESS", true, null, null, true)), masterDataContext(null));
        accessPointHandler.handle(accessPointEvent("created", withoutKp, accessPointValues(withoutKp, "Somewhere", track, executionPackage, null,
                "DEPOT", true, null, null, true)), masterDataContext(null));
        accessPointHandler.handle(accessPointEvent("created", near, accessPointValues(near, "Near", track, executionPackage, "100.000",
                "LEVEL_CROSSING", true, null, null, true)), masterDataContext(null));
        accessPointHandler.handle(accessPointEvent("created", disabled, accessPointValues(disabled, "Closed", track, executionPackage, "50.000",
                "LEVEL_CROSSING", true, null, null, false)), masterDataContext(null));

        assertEquals(List.of(disabled, near, far, withoutKp), accessPoints.list(track, null, null).stream().map(TrackAccessPointResponse::id).toList(),
                "By kp, the one without kp last");
        assertEquals(List.of(near, far, withoutKp), accessPoints.list(track, null, true).stream().map(TrackAccessPointResponse::id).toList());
        assertEquals(List.of(disabled, near, far, withoutKp), accessPoints.list(null, executionPackage, null).stream()
                .map(TrackAccessPointResponse::id).toList());
        assertTrue(accessPoints.list(track, randomId(), null).isEmpty());
    }

    /**
     * El ejemplo que versiona mto-configuration ({@code docs/messaging/examples/master-data-track-access-point-updated.json}
     * de alli, copiado en {@code contracts/mto-configuration/}) pasa, leido como lo lee el consumidor, por el
     * manejador real con un id de este test: una clave renombrada alli falla aqui.
     */
    @Test
    void theTrackAccessPointAsMtoConfigurationPublishesItIsKeptAsItCame() {
        long id = randomId();
        accessPointHandler.handle(configurationContractEvent("master-data-track-access-point-updated", id), masterDataContext(randomId()));

        TrackAccessPointResponse kept = onlyAccessPoint(3L, id);
        assertEquals("Acceso km 110+176", kept.name());
        assertEquals(3L, kept.trackId());
        assertEquals(14L, kept.executionPackageId());
        assertEquals(12L, kept.stationId());
        assertEquals(0, new BigDecimal("110176.000").compareTo(kept.kp()));
        assertEquals("PREPARED_ACCESS", kept.kind());
        assertEquals("LEFT", kept.side());
        assertTrue(kept.roadRailAccess());
        assertEquals(0, new BigDecimal("12.500").compareTo(kept.maxAxleLoadT()));
        assertEquals(0, new BigDecimal("40.000").compareTo(kept.maxGrossWeightT()));
        assertEquals(0, new BigDecimal("31.669342").compareTo(kept.latitude()));
        assertEquals(0, new BigDecimal("34.574119").compareTo(kept.longitude()));
        assertTrue(kept.enabled());
    }

    /**
     * Con {@code accessPointId}, las reglas del acceso: que exista (404 {@code TAP-404}), que este habilitado, en
     * la via de la ubicacion, que admita biviales, que sea un acceso preparado si el modelo lo exige y que aguante
     * su peso. Cada rechazo es un 409 {@code VEH-001} con su motivo, y no escribe nada.
     */
    @Test
    void aLocationWithAnAccessPointFollowsItsRulesAndShowsIt() {
        VehicleResponse roadRail = roadRailVehicle(true, "32.00", "8.00");
        long track = randomId();
        long executionPackage = randomId();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long prepared = accessPoint(track, executionPackage, "PREPARED_ACCESS", true, "12.500", "40.000", true);

        NotFoundException unknown = assertThrows(NotFoundException.class, () -> locations.recordManual(roadRail.id(),
                onTrackAt(track, randomId(), now)));
        assertEquals("TAP-404", new GlobalExceptionHandler().handleNotFound(unknown, new MockHttpServletRequest()).getBody().errorCode());

        long disabled = accessPoint(track, executionPackage, "PREPARED_ACCESS", true, null, null, false);
        assertEquals("Access point AP-" + disabled + " is disabled: no vehicle on-tracks or off-tracks there",
                assertThrows(FleetRuleException.class, () -> locations.recordManual(roadRail.id(), onTrackAt(track, disabled, now))).getMessage());
        assertEquals("Access point AP-" + prepared + " is on track " + track + ", not on track " + (track + 1) + " of the location",
                assertThrows(FleetRuleException.class, () -> locations.recordManual(roadRail.id(), onTrackAt(track + 1, prepared, now)))
                        .getMessage());
        long noRoadRail = accessPoint(track, executionPackage, "PREPARED_ACCESS", false, null, null, true);
        assertEquals("Access point AP-" + noRoadRail + " does not admit road-rail vehicles: Vehicle " + roadRail.code()
                        + " cannot on-track or off-track there",
                assertThrows(FleetRuleException.class, () -> locations.recordManual(roadRail.id(), onTrackAt(track, noRoadRail, now))).getMessage());
        long crossing = accessPoint(track, executionPackage, "LEVEL_CROSSING", true, null, null, true);
        assertEquals("Vehicle " + roadRail.code() + " needs a prepared access, and access point AP-" + crossing + " is a LEVEL_CROSSING",
                assertThrows(FleetRuleException.class, () -> locations.recordManual(roadRail.id(), onTrackAt(track, crossing, now))).getMessage());
        long light = accessPoint(track, executionPackage, "PREPARED_ACCESS", true, null, "30.000", true);
        assertEquals("Vehicle " + roadRail.code() + " weighs up to 32 t, more than the 30 t that access point AP-" + light + " takes",
                assertThrows(FleetRuleException.class, () -> locations.recordManual(roadRail.id(), onTrackAt(track, light, now))).getMessage());
        long narrow = accessPoint(track, executionPackage, "PREPARED_ACCESS", true, "7.500", null, true);
        assertEquals("Vehicle " + roadRail.code() + " has an axle load of up to 8 t, more than the 7.5 t that access point AP-" + narrow + " takes",
                assertThrows(FleetRuleException.class, () -> locations.recordManual(roadRail.id(), onTrackAt(track, narrow, now))).getMessage());
        assertTrue(locations.history(roadRail.id(), null, null, PageRequest.of(0, 10)).content().isEmpty(), "A refusal writes nothing");

        // Un modelo que no exige acceso preparado encarrila en un paso a nivel; y sin pesos conocidos, no se comparan.
        VehicleResponse plain = roadRailVehicle(false, null, null);
        assertNotNull(locations.recordManual(plain.id(), onTrackAt(track, crossing, now)));

        VehicleLocationResponse onTrack = locations.recordManual(roadRail.id(), new VehicleLocationRequest(VehicleLocationKind.ON_TRACK, null, null,
                track, new BigDecimal("1200.000"), null, null, "Next to the gate", prepared, null, null, null, now.minusSeconds(60)));
        assertEquals(new TrackAccessPointSummary(prepared, "AP-" + prepared, "PREPARED_ACCESS"), onTrack.trackAccessPoint());
        assertEquals("Next to the gate", onTrack.accessPoint(), "The free text keeps its key, as it was");
        assertEquals(executionPackage, onTrack.executionPackageId(), "The package of the access point, for the board");
        assertEquals(new TrackAccessPointSummary(prepared, "AP-" + prepared, "PREPARED_ACCESS"),
                vehicles.findById(roadRail.id()).position().trackAccessPoint());
        assertEquals(prepared, locations.history(roadRail.id(), null, null, PageRequest.of(0, 10)).content().getFirst().trackAccessPoint().id());
        FleetPositionResponse row = locations.positions(null, track, null, PageRequest.of(0, 10)).content().stream()
                .filter(position -> position.vehicle().id().equals(roadRail.id())).findFirst().orElseThrow();
        assertEquals(prepared, row.position().trackAccessPoint().id());

        // Desencarrila en el mismo acceso y se va por carretera: sin via en la ubicacion, la via no se compara.
        VehicleLocationResponse offTrack = locations.recordManual(roadRail.id(), new VehicleLocationRequest(VehicleLocationKind.ON_ROAD, null, null,
                null, null, null, null, null, prepared, null, null, null, now));
        assertEquals(prepared, offTrack.trackAccessPoint().id());
        assertNull(offTrack.accessPoint());
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

    // ------------------------------------------------------------------- cualificaciones

    @Test
    void theCatalogueOfQualificationsBringsWhatTheModelsAskForAndALicenceSaysWhichOne() {
        QualificationTypeResponse railDriver = qualificationTypes.findById(RAIL_DRIVER);
        assertEquals("RAIL-DRIVER", railDriver.code());
        assertEquals(QualificationKind.VEHICLE_OPERATION, railDriver.kind());
        assertEquals(Set.of("LIC-B", "LIC-C", "LIC-CE"), qualificationTypes.search("LIC-", QualificationKind.DRIVING_LICENCE, true,
                PageRequest.of(0, 50)).content().stream().map(QualificationTypeResponse::code)
                .filter(code -> code.matches("LIC-(B|C|CE)")).collect(Collectors.toSet()));
        assertEquals(DrivingLicence.C_E, qualificationTypes.findById(LICENCE_CE).drivingLicence());

        assertThrows(ValidationException.class, () -> qualificationTypes.create(new QualificationTypeRequest("Q" + random(), "Licence",
                QualificationKind.DRIVING_LICENCE, null, null, null)), "A licence says which one");
        assertThrows(ValidationException.class, () -> qualificationTypes.create(new QualificationTypeRequest("Q" + random(), "First aid",
                QualificationKind.SAFETY, DrivingLicence.B, null, null)), "Only a licence carries one");

        String code = "q" + random();
        QualificationTypeResponse licence = qualificationTypes.create(new QualificationTypeRequest(code, " Tractor licence ",
                QualificationKind.DRIVING_LICENCE, DrivingLicence.C_E, 120, "Agricultural tractors"));
        assertEquals(code.toUpperCase(Locale.ROOT), licence.code());
        assertEquals("Tractor licence", licence.name());
        DuplicateCodeException duplicate = assertThrows(DuplicateCodeException.class, () -> qualificationTypes.create(
                new QualificationTypeRequest(code, "Again", QualificationKind.OTHER, null, null, null)));
        assertEquals("QTY-409", new GlobalExceptionHandler().handleDuplicateCode(duplicate, new MockHttpServletRequest()).getBody().errorCode());

        QualificationTypeResponse cleared = qualificationTypes.patch(licence.id(), new MergePatch<>(
                new QualificationTypeUpdateRequest(null, null, null, null, null, null, licence.version()), Set.of("validityMonths", "description")));
        assertNull(cleared.validityMonths());
        assertNull(cleared.description());
        assertThrows(ValidationException.class, () -> qualificationTypes.patch(licence.id(), new MergePatch<>(
                new QualificationTypeUpdateRequest(null, null, null, null, null, null, null), Set.of("name"))));
        // Un PUT no sabe vaciar: dejar de ser un permiso quita el permiso sin decirlo.
        QualificationTypeResponse other = qualificationTypes.update(licence.id(), new QualificationTypeUpdateRequest(null,
                QualificationKind.OTHER, null, null, null, null, cleared.version()));
        assertEquals(QualificationKind.OTHER, other.kind());
        assertNull(other.drivingLicence());
        assertThrows(StaleVersionException.class, () -> qualificationTypes.update(licence.id(), new QualificationTypeUpdateRequest("Late",
                null, null, null, null, null, cleared.version())));

        qualificationTypes.deactivate(licence.id());
        assertFalse(qualificationTypes.findById(licence.id()).active());
        assertEquals("QTY-404", new GlobalExceptionHandler().handleNotFound(assertThrows(NotFoundException.class,
                () -> qualificationTypes.findById(UUID.randomUUID())), new MockHttpServletRequest()).getBody().errorCode());
    }

    @Test
    void aPersonHasOneQualificationOfEachTypeAndEveryChangePublishesThePersonWithAllOfThem() {
        PersonResponse person = person(PersonCategory.TEAM_LEADER);
        QualificationTypeResponse firstAid = qualificationType(QualificationKind.SAFETY, null, null);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        events.clear();

        PersonQualificationResponse driver = personQualifications.create(person.id(), new PersonQualificationRequest(RAIL_DRIVER,
                " RD-001 ", today.minusYears(1), today.plusDays(100), null));
        assertEquals("RAIL-DRIVER", driver.qualificationType().code());
        assertEquals("RD-001", driver.reference());
        assertEquals(person.id(), driver.personId());
        assertFalse(driver.expired());
        DomainEvent created = events.only("person.updated");
        assertEquals(person.id().toString(), created.entityId());
        assertEquals(List.of(qualificationValues("RAIL-DRIVER", "VEHICLE_OPERATION", null, today.plusDays(100))),
                created.values().get("qualifications"));

        events.clear();
        PersonQualificationResponse aid = personQualifications.create(person.id(), new PersonQualificationRequest(firstAid.id(), null,
                today.minusYears(2), today.minusDays(1), "Renewal booked"));
        assertTrue(aid.expired(), "Expired from the day after its expiry");
        List<?> both = (List<?>) events.only("person.updated").values().get("qualifications");
        assertEquals(List.of(firstAid.code(), "RAIL-DRIVER").stream().sorted().toList(),
                both.stream().map(item -> ((Map<?, ?>) item).get("code")).toList(), "By code, the expired one too");
        assertEquals(List.of(firstAid.code(), "RAIL-DRIVER").stream().sorted().toList(),
                personQualifications.findAll(person.id()).stream().map(item -> item.qualificationType().code()).toList());

        QualificationRuleException twice = assertThrows(QualificationRuleException.class, () -> personQualifications.create(person.id(),
                new PersonQualificationRequest(RAIL_DRIVER, null, null, null, null)));
        assertEquals("QUA-001", new GlobalExceptionHandler().handleStateConflict(twice, new MockHttpServletRequest()).getBody().errorCode());
        QualificationTypeResponse retired = qualificationType(QualificationKind.OTHER, null, null);
        qualificationTypes.deactivate(retired.id());
        assertThrows(QualificationRuleException.class, () -> personQualifications.create(person.id(),
                new PersonQualificationRequest(retired.id(), null, null, null, null)), "No new one of a retired type");
        assertThrows(ValidationException.class, () -> personQualifications.create(person.id(),
                new PersonQualificationRequest(LICENCE_B, null, today, today.minusDays(1), null)), "It expires before it was issued");
        assertThrows(NotFoundException.class, () -> personQualifications.create(UUID.randomUUID(),
                new PersonQualificationRequest(LICENCE_B, null, null, null, null)));

        // Renovarla es cambiar su caducidad; sin caducidad, no caduca.
        events.clear();
        PersonQualificationResponse renewed = personQualifications.patch(person.id(), aid.id(), MergePatch.of(
                new PersonQualificationUpdateRequest(null, today, today.plusYears(3), null, aid.version())));
        assertFalse(renewed.expired());
        assertEquals(1, events.names().size());
        assertThrows(StaleVersionException.class, () -> personQualifications.patch(person.id(), aid.id(), MergePatch.of(
                new PersonQualificationUpdateRequest("X", null, null, null, aid.version()))));
        PersonQualificationResponse forever = personQualifications.patch(person.id(), aid.id(), new MergePatch<>(
                new PersonQualificationUpdateRequest(null, null, null, null, renewed.version()), Set.of("expiresOn", "notes")));
        assertNull(forever.expiresOn());
        assertNull(forever.notes());
        assertTrue(((List<?>) events.published.getLast().values().get("qualifications")).stream()
                .anyMatch(item -> ((Map<?, ?>) item).get("code").equals(firstAid.code()) && ((Map<?, ?>) item).get("expiresOn") == null));

        PersonResponse other = person(PersonCategory.WORKER);
        NotFoundException elsewhere = assertThrows(NotFoundException.class, () -> personQualifications.patch(other.id(), aid.id(),
                MergePatch.of(new PersonQualificationUpdateRequest("X", null, null, null, null))));
        assertEquals("QUA-404", new GlobalExceptionHandler().handleNotFound(elsewhere, new MockHttpServletRequest()).getBody().errorCode());

        // Cambiar la persona tambien cuenta sus cualificaciones.
        events.clear();
        people.patch(person.id(), MergePatch.of(new PersonUpdateRequest("Renamed", null, null, null, null, null, null, null, null, null,
                null)));
        assertEquals(2, ((List<?>) events.only("person.updated").values().get("qualifications")).size());

        // Quitar la que se dio por error: se borra, el historial la guarda y el evento ya no la lleva.
        events.clear();
        personQualifications.delete(person.id(), driver.id());
        assertEquals(List.of(firstAid.code()), ((List<?>) events.only("person.updated").values().get("qualifications")).stream()
                .map(item -> ((Map<?, ?>) item).get("code")).toList());
        assertEquals(1, personQualifications.findAll(person.id()).size());
        assertEquals(List.of(0, 2), jdbc.queryForList("select revtype from person_qualification_aud where id = ? order by rev",
                Integer.class, driver.id()), "Created and deleted");
        assertEquals(today.plusDays(100), jdbc.queryForObject("select expires_on from person_qualification_aud where id = ? and revtype = 2",
                LocalDate.class, driver.id()), "The deletion keeps what it deleted");
        assertEquals(3, jdbc.queryForObject("select count(*) from person_qualification_aud where id = ?", Integer.class, aid.id()),
                "Created and changed twice");
    }

    @Test
    void theValidityOfATypeGivesTheExpiryOfAQualificationThatDoesNotSayIt() {
        PersonResponse person = person(PersonCategory.WORKER);
        QualificationTypeResponse crane = qualificationType(QualificationKind.VEHICLE_OPERATION, null, 24);
        QualificationTypeResponse mewp = qualificationType(QualificationKind.VEHICLE_OPERATION, null, 36);
        LocalDate issued = LocalDate.parse("2026-02-10");

        assertEquals(LocalDate.parse("2028-02-10"), personQualifications.create(person.id(),
                new PersonQualificationRequest(crane.id(), null, issued, null, null)).expiresOn());
        assertNull(personQualifications.create(person.id(), new PersonQualificationRequest(mewp.id(), null, null, null, null)).expiresOn(),
                "Without its issue there is nothing to count from");
    }

    @Test
    void changingTheKindOfAQualificationTypeRepublishesWhoHoldsIt() {
        QualificationTypeResponse type = qualificationType(QualificationKind.SAFETY, null, null);
        PersonResponse first = person(PersonCategory.WORKER);
        PersonResponse second = person(PersonCategory.WORKER);
        personQualifications.create(first.id(), new PersonQualificationRequest(type.id(), null, null, null, null));
        personQualifications.create(second.id(), new PersonQualificationRequest(type.id(), null, null, null, null));
        events.clear();

        QualificationTypeResponse renamed = qualificationTypes.patch(type.id(), MergePatch.of(new QualificationTypeUpdateRequest("Renamed",
                null, null, null, null, null, null)));
        assertEquals(List.of(), events.names(), "The name does not travel in their events");

        qualificationTypes.patch(type.id(), MergePatch.of(new QualificationTypeUpdateRequest(null, QualificationKind.OTHER, null, null,
                null, null, renamed.version())));
        assertEquals(Set.of(first.id().toString(), second.id().toString()), events.published.stream()
                .filter(event -> event.entityName().equals("person")).map(DomainEvent::entityId).collect(Collectors.toSet()));
        assertTrue(events.published.stream().allMatch(event -> event.eventName().equals("updated")
                && ((List<?>) event.values().get("qualifications")).equals(List.of(qualificationValues(type.code(), "OTHER", null, null)))));
    }

    // ----------------------------------------------------------------------- caducidades

    @Test
    void theExpiryWatcherWarnsOnceForEachExpiryDateAndAgainAfterARenewal() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        PersonResponse leader = person(PersonCategory.TEAM_LEADER);
        List<UUID> linemen = linemen(2);
        OrganizationUnitResponse team = team(supervision(Set.of()).id(), leader.id(), linemen, Set.of());
        PersonResponse lineman = people.findById(linemen.getFirst());
        PersonQualificationResponse driver = personQualifications.create(lineman.id(), new PersonQualificationRequest(RAIL_DRIVER, "RD-9",
                null, today.plusDays(10), null));
        events.clear();

        ExpiryWatchResult first = expiryWatch.check();

        assertEquals(today, first.date());
        assertEquals(30, first.warningDays());
        assertTrue(first.qualifications() >= 1);
        DomainEvent expiring = events.onlyAbout(driver.id());
        assertEquals("qualification", expiring.entityName());
        assertEquals("expiring", expiring.eventName());
        Map<String, Object> leaderValues = new LinkedHashMap<>();
        leaderValues.put("personId", leader.id().toString());
        leaderValues.put("code", leader.code());
        leaderValues.put("fullName", leader.fullName());
        leaderValues.put("username", leader.username());
        leaderValues.put("category", "TEAM_LEADER");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("personId", lineman.id().toString());
        expected.put("personCode", lineman.code());
        expected.put("fullName", lineman.fullName());
        expected.put("username", lineman.username());
        expected.put("qualificationCode", "RAIL-DRIVER");
        expected.put("qualificationName", "Rail auxiliary vehicle driver");
        expected.put("kind", "VEHICLE_OPERATION");
        expected.put("expiresOn", today.plusDays(10).toString());
        expected.put("daysLeft", 10);
        expected.put("teamCode", team.code());
        expected.put("leader", leaderValues);
        assertEquals(expected, expiring.values());
        assertEquals(UUID.nameUUIDFromBytes(("qualification:expiring:" + driver.id() + ":" + today.plusDays(10))
                .getBytes(StandardCharsets.UTF_8)), events.operationIdOf(expiring), "Derived from what it tells");

        events.clear();
        expiryWatch.check();
        assertEquals(List.of(), events.about(driver.id()), "Once for each expiry date");

        // Renovada, pero aun dentro del aviso: otra fecha, otro aviso.
        personQualifications.patch(lineman.id(), driver.id(), MergePatch.of(new PersonQualificationUpdateRequest(null, null,
                today.plusDays(20), null, null)));
        events.clear();
        expiryWatch.check();
        assertEquals(20, events.onlyAbout(driver.id()).values().get("daysLeft"));

        // Caducada desde ayer: una vez, con los dias en negativo.
        personQualifications.patch(lineman.id(), driver.id(), MergePatch.of(new PersonQualificationUpdateRequest(null, null,
                today.minusDays(1), null, null)));
        events.clear();
        expiryWatch.check();
        DomainEvent expired = events.onlyAbout(driver.id());
        assertEquals("expired", expired.eventName());
        assertEquals(-1, expired.values().get("daysLeft"));
        assertEquals(UUID.nameUUIDFromBytes(("qualification:expired:" + driver.id() + ":" + today.minusDays(1))
                .getBytes(StandardCharsets.UTF_8)), events.operationIdOf(expired));
        events.clear();
        expiryWatch.check();
        assertEquals(List.of(), events.about(driver.id()));

        // Renovada lejos: nada que avisar.
        personQualifications.patch(lineman.id(), driver.id(), MergePatch.of(new PersonQualificationUpdateRequest(null, null,
                today.plusDays(400), null, null)));
        events.clear();
        expiryWatch.check();
        assertEquals(List.of(), events.about(driver.id()));
    }

    @Test
    void theLeaderOfATeamIsWarnedWithoutHimselfAsLeaderAndWhoeverHasNoTeamWithoutAny() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        PersonResponse leader = person(PersonCategory.TEAM_LEADER);
        OrganizationUnitResponse team = team(supervision(Set.of()).id(), leader.id(), linemen(2), Set.of());
        PersonResponse loner = person(PersonCategory.WORKER);
        PersonResponse gone = person(PersonCategory.WORKER);
        QualificationTypeResponse type = qualificationType(QualificationKind.SAFETY, null, null);
        PersonQualificationResponse ofLeader = personQualifications.create(leader.id(), new PersonQualificationRequest(type.id(), null, null,
                today.plusDays(5), null));
        PersonQualificationResponse ofLoner = personQualifications.create(loner.id(), new PersonQualificationRequest(type.id(), null, null,
                today, null));
        PersonQualificationResponse ofGone = personQualifications.create(gone.id(), new PersonQualificationRequest(type.id(), null, null,
                today.minusDays(3), null));
        people.deactivate(gone.id());
        events.clear();

        expiryWatch.check();

        DomainEvent leaderWarning = events.onlyAbout(ofLeader.id());
        assertEquals(team.code(), leaderWarning.values().get("teamCode"));
        assertTrue(leaderWarning.values().containsKey("leader"));
        assertNull(leaderWarning.values().get("leader"), "Never the person themselves");
        assertEquals(5, leaderWarning.values().get("daysLeft"));
        DomainEvent lonerWarning = events.onlyAbout(ofLoner.id());
        assertEquals("expiring", lonerWarning.eventName(), "Still valid on its expiry date");
        assertEquals(0, lonerWarning.values().get("daysLeft"));
        assertNull(lonerWarning.values().get("teamCode"));
        assertNull(lonerWarning.values().get("leader"));
        assertEquals(List.of(), events.about(ofGone.id()), "Whoever no longer works is not warned about");
    }

    @Test
    void theExpiryWatcherWarnsAboutTheDocumentsOfAVehicleAndTheCalibrationOfAnInstrument() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        VehicleResponse vehicle = railVehicle(Set.of());
        VehicleResponse retired = railVehicle(Set.of());
        VehicleDocumentResponse inspection = vehicleDocuments.create(vehicle.id(), new VehicleDocumentRequest(
                VehicleDocumentKind.RAIL_INSPECTION, "RI-7", null, today.plusDays(30), null));
        VehicleDocumentResponse insurance = vehicleDocuments.create(vehicle.id(), new VehicleDocumentRequest(VehicleDocumentKind.INSURANCE,
                "POL-1", null, today.minusDays(2), null));
        VehicleDocumentResponse far = vehicleDocuments.create(vehicle.id(), new VehicleDocumentRequest(VehicleDocumentKind.CRANE_CERTIFICATE,
                null, null, today.plusDays(31), null));
        VehicleDocumentResponse ofRetired = vehicleDocuments.create(retired.id(), new VehicleDocumentRequest(VehicleDocumentKind.INSURANCE,
                null, null, today.minusDays(1), null));
        vehicles.deactivate(retired.id());
        InstrumentResponse pole = instruments.create(new InstrumentRequest("PT-" + random(), "Earthing pole", InstrumentKind.EARTHING_POLE,
                "EP-77", null, today.plusDays(1), null));
        InstrumentResponse detector = instruments.create(new InstrumentRequest("DT-" + random(), "Detector", InstrumentKind.VOLTAGE_DETECTOR,
                null, null, today.minusDays(10), null));
        InstrumentResponse gauge = instruments.create(new InstrumentRequest("M-" + random(), "Gauge", InstrumentKind.MEASURING, null, null,
                null, null));
        InstrumentResponse retiredPole = instruments.create(new InstrumentRequest("PT-" + random(), "Old pole", InstrumentKind.EARTHING_POLE,
                null, null, today.minusDays(1), null));
        instruments.deactivate(retiredPole.id());
        events.clear();

        ExpiryWatchResult result = expiryWatch.check();

        assertTrue(result.vehicleDocuments() >= 2);
        assertTrue(result.calibrations() >= 2);
        DomainEvent inspectionWarning = events.onlyAbout(inspection.id());
        assertEquals("vehicle-document.expiring", inspectionWarning.entityName() + "." + inspectionWarning.eventName());
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("vehicleId", vehicle.id().toString());
        document.put("vehicleCode", vehicle.code());
        document.put("vehicleName", "Vehicle");
        document.put("kind", "RAIL_INSPECTION");
        document.put("reference", "RI-7");
        document.put("expiresOn", today.plusDays(30).toString());
        document.put("daysLeft", 30);
        assertEquals(document, inspectionWarning.values());
        DomainEvent insuranceWarning = events.onlyAbout(insurance.id());
        assertEquals("expired", insuranceWarning.eventName());
        assertEquals(-2, insuranceWarning.values().get("daysLeft"));
        assertEquals(List.of(), events.about(far.id()), "31 days is beyond the warning");
        assertEquals(List.of(), events.about(ofRetired.id()), "A retired vehicle is not warned about");

        DomainEvent poleWarning = events.onlyAbout(pole.id());
        assertEquals("calibration.expiring", poleWarning.entityName() + "." + poleWarning.eventName());
        Map<String, Object> calibration = new LinkedHashMap<>();
        calibration.put("instrumentId", pole.id().toString());
        calibration.put("code", pole.code());
        calibration.put("name", "Earthing pole");
        calibration.put("kind", "EARTHING_POLE");
        calibration.put("serialNumber", "EP-77");
        calibration.put("calibrationExpiresOn", today.plusDays(1).toString());
        calibration.put("daysLeft", 1);
        assertEquals(calibration, poleWarning.values());
        assertEquals(UUID.nameUUIDFromBytes(("calibration:expiring:" + pole.id() + ":" + today.plusDays(1)).getBytes(StandardCharsets.UTF_8)),
                events.operationIdOf(poleWarning));
        assertEquals("expired", events.onlyAbout(detector.id()).eventName());
        assertEquals(List.of(), events.about(gauge.id()), "Without a calibration nothing expires");
        assertEquals(List.of(), events.about(retiredPole.id()));

        events.clear();
        expiryWatch.check();
        for (UUID id : List.of(inspection.id(), insurance.id(), pole.id(), detector.id())) {
            assertEquals(List.of(), events.about(id), "Once for each expiry date");
        }
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

        assertEquals(new RepublishResult(executionPackage, 5, 1, 1, 0), result, "Leader, three linemen and the supervisor; no instrument is "
                + "of an execution package");
        assertEquals(5, events.names().stream().filter("person.updated"::equals).count());
        assertEquals(1, events.names().stream().filter("team.updated"::equals).count());
        assertEquals(1, events.names().stream().filter("vehicle.updated"::equals).count());
        assertEquals(events.operationIds.size(), Set.copyOf(events.operationIds).size(), "A new operationId for every event");
        assertTrue(events.published.stream().allMatch(event -> Boolean.TRUE.equals(event.values().get("republished"))),
                "Everything it tells again says so, so that nobody is notified of it");
        assertTrue(events.published.stream().filter(event -> event.entityName().equals("vehicle"))
                .allMatch(event -> Boolean.FALSE.equals(event.values().get("availabilityChanged"))));
    }

    @Test
    void republishingEverythingAlsoPublishesTheInstruments() {
        InstrumentResponse instrument = instruments.create(new InstrumentRequest("I" + random(), "Detector", InstrumentKind.VOLTAGE_DETECTOR,
                null, null, null, null));
        events.clear();

        RepublishResult result = republish.republish(null);

        assertTrue(result.instruments() >= 1);
        DomainEvent event = events.published.stream()
                .filter(published -> published.entityName().equals("instrument") && published.entityId().equals(instrument.id().toString()))
                .findFirst().orElseThrow();
        assertEquals("updated", event.eventName());
        assertEquals(true, event.values().get("republished"));
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

    @Test
    void anOperationWaitsForTheOneOfAnotherReplicaInItsScopeAndStartsWhenItEnds() throws Exception {
        authenticateAs("ops.someone");
        String scope = "test-" + random();
        UUID elsewhere = UUID.randomUUID();
        // La de otra replica: en marcha en el mismo ambito y con el latido al dia. El cerrojo local no la ve.
        jdbc.update("""
                insert into background_operation (id, kind, scope, status, requested_by, created_at, started_at, heartbeat_at)
                values (?, 'TEST', ?, 'RUNNING', 'other.replica', now(), now(), now())
                """, elsewhere, scope);
        AtomicBoolean ran = new AtomicBoolean();

        UUID id = operations.run("TEST", scope, 200, Duration.ZERO, () -> {
            ran.set(true);
            return "ok";
        }).operation().id();

        Thread.sleep(500);
        assertEquals(OperationStatus.QUEUED, operations.find(id).status(), "Not while the other replica's one runs");
        assertFalse(ran.get());
        jdbc.update("update background_operation set status = 'SUCCEEDED', finished_at = now() where id = ?", elsewhere);
        assertEquals(OperationStatus.SUCCEEDED, waitFor(id).status(), "It looks again and starts once that one ends");
        assertTrue(ran.get());
    }

    @Test
    void operationScopeLocksQueueTheWorkOfOneScopeAndLetAnotherScopeThrough() throws Exception {
        OperationScopeLocks locks = new OperationScopeLocks();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean secondRan = new AtomicBoolean();
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = threads.submit(() -> locks.withLock("republish", () -> {
                holding.countDown();
                await(release);
                return "first";
            }));
            assertTrue(holding.await(5, TimeUnit.SECONDS));
            Future<String> second = threads.submit(() -> locks.withLock("republish", () -> {
                secondRan.set(true);
                return "second";
            }));

            assertEquals("other", locks.withLock("EP-7", () -> "other"), "Another scope does not wait");
            assertFalse(secondRan.get(), "The same scope waits for the one that has the turn");
            assertFalse(second.isDone());

            release.countDown();
            assertEquals("first", first.get(5, TimeUnit.SECONDS));
            assertEquals("second", second.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void anOperationInterruptedWhileWaitingForItsScopeFailsAndKeepsItsInterrupt() throws Exception {
        OperationScopeLocks locks = new OperationScopeLocks();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread owner = Thread.ofVirtual().start(() -> locks.withLock("republish", () -> {
            holding.countDown();
            await(release);
            return null;
        }));
        assertTrue(holding.await(5, TimeUnit.SECONDS));
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        AtomicBoolean interruptKept = new AtomicBoolean();
        Thread waiting = Thread.ofVirtual().start(() -> {
            try {
                locks.withLock("republish", () -> fail("It never gets the turn"));
            } catch (RuntimeException expected) {
                failure.set(expected);
                interruptKept.set(Thread.currentThread().isInterrupted());
            }
        });

        waiting.interrupt();
        assertTrue(waiting.join(Duration.ofSeconds(5)));

        assertTrue(failure.get() instanceof IllegalStateException, () -> "Failed with " + failure.get());
        assertTrue(failure.get().getCause() instanceof InterruptedException);
        assertTrue(interruptKept.get(), "Whoever called it still sees the interrupt");
        release.countDown();
        assertTrue(owner.join(Duration.ofSeconds(5)), "The one with the turn is not disturbed");
        assertEquals("free", locks.withLock("republish", () -> "free"));
    }

    // ------------------------------------------------------------------------ fixtures

    private static Set<UUID> instrumentIds(List<InstrumentResponse> instruments) {
        return instruments.stream().map(InstrumentResponse::id).collect(Collectors.toSet());
    }

    private static Set<UUID> ids(List<PersonResponse> people) {
        return people.stream().map(PersonResponse::id).collect(Collectors.toSet());
    }

    private PersonResponse person(PersonCategory category) {
        String suffix = random();
        return people.create(new PersonRequest("P" + suffix, "Test", category, null, "user." + suffix, null, null, null, null));
    }

    private QualificationTypeResponse qualificationType(QualificationKind kind, DrivingLicence licence, Integer validityMonths) {
        return qualificationTypes.create(new QualificationTypeRequest("Q" + random(), "Qualification", kind, licence, validityMonths, null));
    }

    /** Una cualificacion como viaja en el evento de su persona. */
    private static Map<String, Object> qualificationValues(String code, String kind, String drivingLicence, LocalDate expiresOn) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", code);
        values.put("kind", kind);
        values.put("drivingLicence", drivingLicence);
        values.put("expiresOn", expiresOn == null ? null : expiresOn.toString());
        return values;
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
                capabilities, null, null, null, null, null, null, null, null);
    }

    private static VehicleTypeRequest driverTypeRequest(VehicleMobility mobility, VehicleCategory category, boolean selfPropelled,
                                                        RailGearType railGear, DrivingLicence licence, UUID driver) {
        return new VehicleTypeRequest("VT" + random(), "Model", null, null, mobility, category, selfPropelled, false, null, null, null, null,
                null, null, null, 8, null, null, null, null, null, null, null, null, false, railGear, null, null, null, licence, Set.of(), null,
                null, null, null, null, null, null, driver);
    }

    private static VehicleTypeUpdateRequest typeUpdate(UUID driver, Long version) {
        return new VehicleTypeUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, driver,
                null, version);
    }

    /** Un documento como viaja en el evento de su vehiculo. */
    private static Map<String, Object> documentValues(String kind, String reference, LocalDate expiresOn) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("kind", kind);
        values.put("reference", reference);
        values.put("expiresOn", expiresOn.toString());
        return values;
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

    /** Una bivial de un modelo que exige acceso preparado o no, con sus pesos (o sin ellos). */
    private VehicleResponse roadRailVehicle(boolean needsPreparedAccess, String maxGrossT, String maxAxleLoadT) {
        VehicleTypeResponse type = types.create(new VehicleTypeRequest("VT" + random(), "Road-rail truck", null, null, VehicleMobility.ROAD_RAIL,
                VehicleCategory.ROAD_RAIL_TRUCK, true, false, null, null, null, null, maxGrossT == null ? null : new BigDecimal(maxGrossT),
                maxAxleLoadT == null ? null : new BigDecimal(maxAxleLoadT), null, 3, null, null, null, null, null, null, null, null, false,
                RailGearType.RAIL_WHEEL_DRIVE, null, needsPreparedAccess, null, null, Set.of(), null, null, null, null, null, null, null, null));
        return vehicles.create(vehicleRequest(type.id(), "RR-" + random(), null));
    }

    private static VehicleLocationRequest onTrackAt(long trackId, long accessPointId, Instant observedAt) {
        return new VehicleLocationRequest(VehicleLocationKind.ON_TRACK, null, null, trackId, null, null, null, null, accessPointId, null, null,
                null, observedAt);
    }

    /** Un punto de acceso dado de alta por el manejador real, llamado {@code AP-<id>}. */
    private long accessPoint(long trackId, long executionPackageId, String kind, boolean roadRailAccess, String maxAxleLoadT,
                             String maxGrossWeightT, boolean enabled) {
        long id = randomId();
        accessPointHandler.handle(accessPointEvent("created", id, accessPointValues(id, "AP-" + id, trackId, executionPackageId, "1200.000", kind,
                roadRailAccess, maxAxleLoadT, maxGrossWeightT, enabled)), masterDataContext(null));
        return id;
    }

    /** Los {@code values} de un punto de acceso como los publica mto-configuration. */
    private static Map<String, Object> accessPointValues(long id, String name, long trackId, Long executionPackageId, String kp, String kind,
                                                         boolean roadRailAccess, String maxAxleLoadT, String maxGrossWeightT, boolean enabled) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("name", name);
        values.put("enabled", enabled);
        values.put("kp", kp == null ? null : new BigDecimal(kp));
        values.put("kind", kind);
        values.put("side", "LEFT");
        values.put("roadRailAccess", roadRailAccess);
        values.put("maxAxleLoadT", maxAxleLoadT == null ? null : new BigDecimal(maxAxleLoadT));
        values.put("maxGrossWeightT", maxGrossWeightT == null ? null : new BigDecimal(maxGrossWeightT));
        values.put("latitude", null);
        values.put("longitude", null);
        values.put("station", null);
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("id", trackId);
        track.put("name", "TRACK " + trackId);
        track.put("executionPackageId", executionPackageId);
        values.put("track", track);
        return values;
    }

    private static IncomingEvent accessPointEvent(String operation, long id, Map<String, Object> values) {
        return new IncomingEvent("track-access-point", String.valueOf(id), operation, values);
    }

    private static IncomingEventContext masterDataContext(Long sequenceNumber) {
        return new IncomingEventContext("mto-configuration", sequenceNumber, UUID.randomUUID().toString());
    }

    private TrackAccessPointResponse onlyAccessPoint(long trackId, long id) {
        List<TrackAccessPointResponse> matching = accessPoints.list(trackId, null, null).stream().filter(point -> point.id() == id).toList();
        assertEquals(1, matching.size(), "Access point " + id + " on track " + trackId);
        return matching.getFirst();
    }

    /** El ejemplo de mto-configuration con su id cambiado por el de este test, leido como lo lee el consumidor. */
    private static IncomingEvent configurationContractEvent(String name, long id) {
        try (java.io.InputStream json = BusinessLayerTest.class.getResourceAsStream("/contracts/mto-configuration/" + name + ".json")) {
            String text = new String(java.util.Objects.requireNonNull(json, name).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\"id\": 77,", "\"id\": " + id + ",")
                    .replace("\"entityId\": \"77\"", "\"entityId\": \"" + id + "\"");
            MasterDataChangedMessage message = tools.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(text, MasterDataChangedMessage.class);
            return message.toIncomingEvent().data();
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    /** Un id de mto-configuration que no choca con los de otras pasadas: la base se comparte. */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000_000L);
    }

    private static VehicleLocationRequest location(VehicleLocationKind kind, Long trackId, Instant observedAt) {
        return new VehicleLocationRequest(kind, null, null, trackId, null, null, null, null, null, null, null, null, observedAt);
    }

    private static VehicleLocationRequest attached(UUID carrier, VehicleAttachment attachment, Instant observedAt) {
        return new VehicleLocationRequest(VehicleLocationKind.ATTACHED, null, null, null, null, null, null, null, null, null, carrier, attachment,
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

        /** Lo publicado sobre esa entidad (su {@code entityId}). */
        List<DomainEvent> about(UUID entityId) {
            return published.stream().filter(event -> event.entityId().equals(entityId.toString())).toList();
        }

        DomainEvent onlyAbout(UUID entityId) {
            List<DomainEvent> matching = about(entityId);
            assertEquals(1, matching.size(), "Published about " + entityId + ": " + matching);
            return matching.getFirst();
        }

        /** El {@code operationId} con el que se publico ese evento. */
        UUID operationIdOf(DomainEvent event) {
            synchronized (published) {
                for (int index = 0; index < published.size(); index++) {
                    if (published.get(index) == event) {
                        return operationIds.get(index);
                    }
                }
            }
            throw new AssertionError("Not published: " + event);
        }

        DomainEvent only(String name) {
            List<DomainEvent> matching = published.stream().filter(event -> (event.entityName() + "." + event.eventName()).equals(name)).toList();
            assertEquals(1, matching.size(), "Published: " + names());
            return matching.getFirst();
        }
    }
}
