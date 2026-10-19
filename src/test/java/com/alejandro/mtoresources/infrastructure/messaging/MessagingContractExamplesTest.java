package com.alejandro.mtoresources.infrastructure.messaging;

import com.alejandro.mtoresources.application.dto.messaging.AsynchronousMessage;
import com.alejandro.mtoresources.application.dto.messaging.DomainEvent;
import com.alejandro.mtoresources.application.dto.messaging.MessageActor;
import com.alejandro.mtoresources.application.service.impl.ResourcesEvents;
import com.alejandro.mtoresources.domain.model.UnavailablePeriod;
import com.alejandro.mtoresources.infrastructure.messaging.outbox.AsynchronousMessageFactory;
import com.alejandro.mtoresources.infrastructure.messaging.outbox.AsynchronousMessageHashService;
import com.alejandro.mtoresources.infrastructure.messaging.outbox.MessageContextResolver;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.ResourcesRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Instrument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.InstrumentKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonQualification;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RailGearType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocumentKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Los ejemplos de {@code docs/messaging/examples} son el contrato tal como lo ven los consumidores:
 * {@code mto-maintenance} los copia como fixtures (y {@code mto-construction} lo hara). Un ejemplo
 * escrito a mano se desalinea del codigo sin que nadie lo note; este test lo evita construyendo cada
 * mensaje con {@code ResourcesEvents} y la factoria real y comparandolo con el fichero, salvo las dos
 * claves que cambian en cada ejecucion (la fecha y, con ella, la huella), que se comprueban aparte.
 *
 * <p>Un evento nuevo o una clave nueva cambian el ejemplo en el mismo commit. Para regenerarlos:
 * {@code MESSAGING_EXAMPLES_WRITE=true ./mvnw test -Dtest=MessagingContractExamplesTest}, y se
 * revisa el diff como cualquier cambio de contrato.</p>
 */
class MessagingContractExamplesTest {

    private static final Path EXAMPLES = Path.of("docs", "messaging", "examples");

    private static final boolean WRITE = "true".equalsIgnoreCase(System.getenv("MESSAGING_EXAMPLES_WRITE"));

    private static final Instant CREATION_DATE = Instant.parse("2026-10-16T09:00:00Z");

    /** El dia del vigilante en los ejemplos de caducidad: el de {@link #CREATION_DATE}. */
    private static final LocalDate TODAY = LocalDate.parse("2026-10-16");

    private static final MessageActor MANAGER = MessageActor.of("6f1b1c8e-0000-4000-8000-000000000051", "recursos.gestor");
    private static final String REQUEST_CORRELATION_ID = "8c3b8c1a-1111-4222-8333-555555555555";

    private final ObjectMapper objectMapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final AsynchronousMessageHashService hashService = new AsynchronousMessageHashService(objectMapper);

    // ------------------------------------------------------------------------------- people

    @Test
    void personCreated() throws IOException {
        // Una persona nueva aun no tiene cualificaciones: se le dan despues.
        check("person-created.json", "b0000000-0000-4000-8000-000000000001", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.person(leader(), List.of(), List.of(), ResourcesEvents.CREATED));
    }

    @Test
    void personUpdated() throws IOException {
        Person person = leader();
        person.setCategory(PersonCategory.SUPERVISOR);
        ReflectionTestUtils.setField(person, "version", 1L);
        // Llegan en cualquier orden y viajan por codigo; todas, tambien las caducadas.
        List<PersonQualification> qualifications = List.of(
                qualification(person, railDriver(), "a1000000-0000-4000-8000-000000000001", LocalDate.parse("2027-03-31")),
                qualification(person, licenceC(), "a1000000-0000-4000-8000-000000000002", LocalDate.parse("2031-06-14")));
        // Cuando no esta: el periodo de una ausencia, nunca su clase.
        List<UnavailablePeriod> unavailable = List.of(new UnavailablePeriod(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-13")));
        check("person-updated.json", "b0000000-0000-4000-8000-000000000002", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.person(person, qualifications, unavailable, ResourcesEvents.UPDATED));
    }

    @Test
    void personDeactivated() throws IOException {
        Person person = lineman("c0000000-0000-4000-8000-000000000012", "PER-000012", "Avi", "Levi", null);
        person.setActive(false);
        ReflectionTestUtils.setField(person, "version", 2L);
        // Una que no caduca viaja con expiresOn nulo.
        check("person-deactivated.json", "b0000000-0000-4000-8000-000000000003", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.person(person, List.of(qualification(person, licenceB(), "a1000000-0000-4000-8000-000000000003", null)),
                        List.of(), ResourcesEvents.DEACTIVATED));
    }

    // -------------------------------------------------------------------------------- teams

    @Test
    void teamCreated() throws IOException {
        check("team-created.json", "b0000000-0000-4000-8000-000000000004", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.team(teamView(), ResourcesEvents.CREATED));
    }

    @Test
    void teamUpdated() throws IOException {
        ResourcesEvents.TeamView view = teamView();
        ReflectionTestUtils.setField(view.team(), "version", 3L);
        check("team-updated.json", "b0000000-0000-4000-8000-000000000005", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.team(view, ResourcesEvents.UPDATED));
    }

    @Test
    void teamDeactivated() throws IOException {
        ResourcesEvents.TeamView view = teamView();
        view.team().setActive(false);
        ReflectionTestUtils.setField(view.team(), "version", 4L);
        check("team-deactivated.json", "b0000000-0000-4000-8000-000000000006", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.team(new ResourcesEvents.TeamView(view.team(), view.leader(), List.of(), view.supervisor(),
                        view.chiefSupervisor(), view.departmentId()), ResourcesEvents.DEACTIVATED));
    }

    // ----------------------------------------------------------------------------- vehicles

    @Test
    void vehicleCreated() throws IOException {
        // Un vehiculo nuevo aun no tiene documentos: se le dan despues.
        check("vehicle-created.json", "b0000000-0000-4000-8000-000000000007", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.vehicle(roadRail(), List.of(), List.of(), ResourcesEvents.CREATED));
    }

    @Test
    void vehicleUpdated() throws IOException {
        Vehicle vehicle = roadRail();
        vehicle.setOperationalStatus(VehicleOperationalStatus.UNDER_REPAIR);
        vehicle.setStatusReason("Hydraulic leak on the crane");
        ReflectionTestUtils.setField(vehicle, "version", 1L);
        // Pasa a reparacion: deja de estar disponible, y eso es lo que avisa mto-notification. Sus documentos
        // llegan en cualquier orden y viajan por tipo.
        // En el taller con una reparacion abierta sin fecha de salida, y otra planificada mas adelante.
        List<UnavailablePeriod> unavailable = List.of(new UnavailablePeriod(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-03")),
                new UnavailablePeriod(LocalDate.parse("2026-10-16"), null));
        check("vehicle-updated.json", "b0000000-0000-4000-8000-000000000008", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.vehicle(vehicle, documents(vehicle), unavailable, ResourcesEvents.UPDATED, true, false));
    }

    @Test
    void vehicleDeactivated() throws IOException {
        Vehicle vehicle = roadRail();
        vehicle.setActive(false);
        ReflectionTestUtils.setField(vehicle, "version", 2L);
        check("vehicle-deactivated.json", "b0000000-0000-4000-8000-000000000009", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.vehicle(vehicle, documents(vehicle), List.of(), ResourcesEvents.DEACTIVATED, true, false));
    }

    // -------------------------------------------------------------------------- instruments

    @Test
    void instrumentCreated() throws IOException {
        check("instrument-created.json", "b0000000-0000-4000-8000-000000000010", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.instrument(earthingPole(), ResourcesEvents.CREATED));
    }

    @Test
    void instrumentUpdated() throws IOException {
        Instrument instrument = earthingPole();
        instrument.setCalibratedOn(LocalDate.parse("2027-03-25"));
        instrument.setCalibrationExpiresOn(LocalDate.parse("2028-03-24"));
        ReflectionTestUtils.setField(instrument, "version", 1L);
        // Recalibrada: sus fechas nuevas.
        check("instrument-updated.json", "b0000000-0000-4000-8000-000000000011", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.instrument(instrument, ResourcesEvents.UPDATED));
    }

    @Test
    void instrumentDeactivated() throws IOException {
        Instrument instrument = earthingPole();
        instrument.setActive(false);
        ReflectionTestUtils.setField(instrument, "version", 2L);
        check("instrument-deactivated.json", "b0000000-0000-4000-8000-000000000012", MANAGER, REQUEST_CORRELATION_ID,
                ResourcesEvents.instrument(instrument, ResourcesEvents.DEACTIVATED));
    }

    // --------------------------------------------------------------------------- expiries

    @Test
    void qualificationExpiring() throws IOException {
        // Un lineman del equipo A: su jefe tambien se entera.
        Person person = lineman("c0000000-0000-4000-8000-000000000010", "PER-000010", "Eli", "Katz", "equipo.operario");
        PersonQualification qualification = qualification(person, railDriver(), "a1000000-0000-4000-8000-000000000010",
                LocalDate.parse("2026-11-15"));
        checkExpiry("qualification-expiring.json", ResourcesEvents.qualificationExpiry(qualification, ResourcesEvents.EXPIRING, TODAY, "A",
                leader()), qualification.getExpiresOn());
    }

    @Test
    void qualificationExpired() throws IOException {
        // El jefe del equipo A: su aviso no lleva jefe, porque el jefe es el.
        Person person = leader();
        PersonQualification qualification = qualification(person, licenceC(), "a1000000-0000-4000-8000-000000000011",
                LocalDate.parse("2026-10-15"));
        checkExpiry("qualification-expired.json", ResourcesEvents.qualificationExpiry(qualification, ResourcesEvents.EXPIRED, TODAY, "A",
                null), qualification.getExpiresOn());
    }

    @Test
    void vehicleDocumentExpiring() throws IOException {
        VehicleDocument inspection = VehicleDocument.builder().vehicle(roadRail()).kind(VehicleDocumentKind.ROAD_INSPECTION)
                .reference("ITV-2026-118").expiresOn(LocalDate.parse("2026-11-01")).build();
        entity(inspection, "a2000000-0000-4000-8000-000000000002");
        checkExpiry("vehicle-document-expiring.json", ResourcesEvents.vehicleDocumentExpiry(inspection, ResourcesEvents.EXPIRING, TODAY),
                inspection.getExpiresOn());
    }

    @Test
    void vehicleDocumentExpired() throws IOException {
        VehicleDocument insurance = VehicleDocument.builder().vehicle(roadRail()).kind(VehicleDocumentKind.INSURANCE)
                .reference("POL-55120").expiresOn(LocalDate.parse("2026-10-14")).build();
        entity(insurance, "a2000000-0000-4000-8000-000000000001");
        checkExpiry("vehicle-document-expired.json", ResourcesEvents.vehicleDocumentExpiry(insurance, ResourcesEvents.EXPIRED, TODAY),
                insurance.getExpiresOn());
    }

    @Test
    void calibrationExpiring() throws IOException {
        Instrument instrument = earthingPole();
        instrument.setCalibratedOn(LocalDate.parse("2025-10-31"));
        instrument.setCalibrationExpiresOn(LocalDate.parse("2026-10-30"));
        checkExpiry("calibration-expiring.json", ResourcesEvents.calibrationExpiry(instrument, ResourcesEvents.EXPIRING, TODAY),
                instrument.getCalibrationExpiresOn());
    }

    @Test
    void calibrationExpired() throws IOException {
        Instrument instrument = Instrument.builder().code("DT-03").name("Voltage detector 25 kV").kind(InstrumentKind.VOLTAGE_DETECTOR)
                .serialNumber("VD-7731").calibratedOn(LocalDate.parse("2025-10-13")).calibrationExpiresOn(LocalDate.parse("2026-10-12"))
                .build();
        entity(instrument, "a3000000-0000-4000-8000-000000000003");
        checkExpiry("calibration-expired.json", ResourcesEvents.calibrationExpiry(instrument, ResourcesEvents.EXPIRED, TODAY),
                instrument.getCalibrationExpiresOn());
    }

    /**
     * Un aviso de caducidad lo publica el vigilante diario: sin token ({@code SYSTEM}), sin correlacion, y con
     * el {@code operationId} derivado de lo que cuenta.
     */
    private void checkExpiry(String fileName, DomainEvent event, LocalDate expiresOn) throws IOException {
        check(fileName, ResourcesEvents.expiryOperationId(event, expiresOn).toString(), MessageActor.system(), null, event);
    }

    // ----------------------------------------------------------------------------- fixtures

    private static Instrument earthingPole() {
        Instrument instrument = Instrument.builder().code("PT-012").name("Earthing pole 25 kV").kind(InstrumentKind.EARTHING_POLE)
                .serialNumber("EP25-88412").calibratedOn(LocalDate.parse("2026-04-01")).calibrationExpiresOn(LocalDate.parse("2027-03-31"))
                .build();
        entity(instrument, "a3000000-0000-4000-8000-000000000012");
        return instrument;
    }

    private static Person leader() {
        Person person = Person.builder().code("PER-000003").firstName("Yossi").lastName("Cohen").username("equipo.jefe")
                .employeeNumber("E-1042").category(PersonCategory.TEAM_LEADER).build();
        entity(person, "c0000000-0000-4000-8000-000000000003");
        return person;
    }

    private static Person lineman(String id, String code, String firstName, String lastName, String username) {
        Person person = Person.builder().code(code).firstName(firstName).lastName(lastName).username(username)
                .category(PersonCategory.WORKER).build();
        entity(person, id);
        return person;
    }

    private static ResourcesEvents.TeamView teamView() {
        Person supervisor = Person.builder().code("PER-000002").firstName("Dana").lastName("Mizrahi").username("equipo.supervisor")
                .category(PersonCategory.SUPERVISOR).build();
        entity(supervisor, "c0000000-0000-4000-8000-000000000002");
        Person chief = Person.builder().code("PER-000001").firstName("Moshe").lastName("Peretz").username("equipo.jefe-supervisores")
                .category(PersonCategory.CHIEF_SUPERVISOR).build();
        entity(chief, "c0000000-0000-4000-8000-000000000001");
        OrganizationUnit supervision = OrganizationUnit.builder().code("SUP-N").name("North supervision")
                .kind(OrganizationUnitKind.SUPERVISION).discipline(WorkDiscipline.MAINTENANCE).head(supervisor).build();
        entity(supervision, "d0000000-0000-4000-8000-000000000002");
        OrganizationUnit team = OrganizationUnit.builder().code("A").name("Team A").kind(OrganizationUnitKind.TEAM)
                .discipline(WorkDiscipline.MAINTENANCE).parent(supervision).head(leader()).defaultVehicle(roadRail())
                .executionPackageIds(new LinkedHashSet<>(List.of(6L, 4L, 5L))).build();
        entity(team, "d0000000-0000-4000-8000-000000000003");
        List<Person> members = List.of(
                lineman("c0000000-0000-4000-8000-000000000010", "PER-000010", "Eli", "Katz", "equipo.operario"),
                lineman("c0000000-0000-4000-8000-000000000011", "PER-000011", "Noam", "Biton", null),
                lineman("c0000000-0000-4000-8000-000000000012", "PER-000012", "Avi", "Levi", null),
                lineman("c0000000-0000-4000-8000-000000000013", "PER-000013", "Omer", "Dahan", null));
        return new ResourcesEvents.TeamView(team, team.getHead(), members, supervisor, chief,
                UUID.fromString("0195b000-0000-7000-8000-000000000002"));
    }

    private static Vehicle roadRail() {
        VehicleType type = VehicleType.builder().code("UNIMOG-RR").name("Unimog road-rail").mobility(VehicleMobility.ROAD_RAIL)
                .category(VehicleCategory.ROAD_RAIL_TRUCK).seats(7).railGearType(RailGearType.RAIL_WHEEL_DRIVE)
                .capabilities(EnumSet.of(VehicleCapability.TOWING, VehicleCapability.CRANE, VehicleCapability.CREW_CABIN))
                .requiredDriverQualification(roadRailOperator()).build();
        entity(type, "e0000000-0000-4000-8000-000000000001");
        Vehicle vehicle = Vehicle.builder().code("RR-07").name("Unimog 7").type(type).registrationPlate("12-345-67").build();
        entity(vehicle, "f0000000-0000-4000-8000-000000000007");
        return vehicle;
    }

    private static List<VehicleDocument> documents(Vehicle vehicle) {
        VehicleDocument insurance = VehicleDocument.builder().vehicle(vehicle).kind(VehicleDocumentKind.INSURANCE).reference("POL-55120")
                .expiresOn(LocalDate.parse("2026-12-31")).build();
        entity(insurance, "a2000000-0000-4000-8000-000000000001");
        VehicleDocument inspection = VehicleDocument.builder().vehicle(vehicle).kind(VehicleDocumentKind.ROAD_INSPECTION)
                .reference("ITV-2026-118").expiresOn(LocalDate.parse("2027-02-28")).build();
        entity(inspection, "a2000000-0000-4000-8000-000000000002");
        return List.of(inspection, insurance);
    }

    private static QualificationType roadRailOperator() {
        QualificationType type = QualificationType.builder().code("RR-OPERATOR").name("Road-rail machine operator")
                .kind(QualificationKind.VEHICLE_OPERATION).build();
        entity(type, "0195b000-0000-7000-8000-000000000105");
        return type;
    }

    private static QualificationType railDriver() {
        QualificationType type = QualificationType.builder().code("RAIL-DRIVER").name("Rail auxiliary vehicle driver")
                .kind(QualificationKind.VEHICLE_OPERATION).build();
        entity(type, "0195b000-0000-7000-8000-000000000104");
        return type;
    }

    private static QualificationType licenceC() {
        QualificationType type = QualificationType.builder().code("LIC-C").name("Driving licence C").kind(QualificationKind.DRIVING_LICENCE)
                .drivingLicence(DrivingLicence.C).build();
        entity(type, "0195b000-0000-7000-8000-000000000102");
        return type;
    }

    private static QualificationType licenceB() {
        QualificationType type = QualificationType.builder().code("LIC-B").name("Driving licence B").kind(QualificationKind.DRIVING_LICENCE)
                .drivingLicence(DrivingLicence.B).build();
        entity(type, "0195b000-0000-7000-8000-000000000101");
        return type;
    }

    private static PersonQualification qualification(Person person, QualificationType type, String id, LocalDate expiresOn) {
        PersonQualification qualification = PersonQualification.builder().person(person).qualificationType(type).expiresOn(expiresOn).build();
        entity(qualification, id);
        return qualification;
    }

    private static void entity(Object entity, String id) {
        ReflectionTestUtils.setField(entity, "id", UUID.fromString(id));
        ReflectionTestUtils.setField(entity, "version", 0L);
    }

    // ------------------------------------------------------------------------------ helpers

    private void check(String fileName, String operationId, MessageActor actor, String correlationId, DomainEvent event) throws IOException {
        MessageContextResolver contextResolver = mock(MessageContextResolver.class);
        when(contextResolver.currentActor()).thenReturn(actor);
        when(contextResolver.currentCorrelationId()).thenReturn(correlationId);
        AsynchronousMessageFactory factory = new AsynchronousMessageFactory(hashService, contextResolver, "mto-resources");

        AsynchronousMessage<DomainEvent> message = factory.create(UUID.fromString(operationId),
                event.entityName() + "-" + event.entityId(),
                ResourcesRabbitMqNames.eventType(event.entityName(), event.eventName()), event);

        Path file = EXAMPLES.resolve(fileName);
        if (WRITE) {
            Files.createDirectories(EXAMPLES);
            Files.writeString(file, pretty(tree(withCreationDate(message, CREATION_DATE)), 0) + "\n");
        }

        assertThat(file).as("el ejemplo %s tiene que estar versionado", fileName).exists();
        assertSameAsExample(message, objectMapper.readTree(Files.readString(file)));
    }

    /**
     * Compara el mensaje con el ejemplo por el texto que viaja de verdad (releido como arbol para
     * que el orden de las claves no cuente), y la huella del ejemplo con la que se obtiene de sus
     * propias siete claves, que es la unica forma de que el ejemplo lleve una huella cierta.
     */
    private void assertSameAsExample(AsynchronousMessage<DomainEvent> message, JsonNode example) {
        ObjectNode produced = tree(message);

        assertThat(Instant.parse(produced.get("creationDate").asText())).isNotNull();
        assertThat(produced.get("messageHash").asText()).matches("[0-9a-f]{64}");

        // La fecha es la de ahora y la huella la incluye: se toman las del ejemplo para comparar lo
        // demas, que es lo que el ejemplo fija.
        produced.put("creationDate", example.get("creationDate").asText());
        produced.put("messageHash", example.get("messageHash").asText());

        assertThat(produced).isEqualTo(example);

        AsynchronousMessage<DomainEvent> asInExample = withCreationDate(message, Instant.parse(example.get("creationDate").asText()));
        assertThat(example.get("messageHash").asText())
                .as("la huella del ejemplo es la de sus siete claves originales, con la fecha del ejemplo")
                .isEqualTo(asInExample.messageHash());
    }

    private AsynchronousMessage<DomainEvent> withCreationDate(AsynchronousMessage<DomainEvent> message, Instant creationDate) {
        AsynchronousMessage<DomainEvent> dated = new AsynchronousMessage<>(message.operationId(), message.referenceId(), message.origin(),
                creationDate, message.eventType(), message.data(), "PENDING", message.actor(), message.correlationId());
        return dated.withMessageHash(hashService.calculate(dated));
    }

    private ObjectNode tree(AsynchronousMessage<DomainEvent> message) {
        return (ObjectNode) objectMapper.readTree(objectMapper.writeValueAsString(message));
    }

    /** Dos espacios, una clave por linea y los elementos de una lista tambien: un diff legible en una revision. */
    private static String pretty(JsonNode node, int depth) {
        String pad = "  ".repeat(depth + 1);
        if (node.isObject()) {
            if (node.isEmpty()) {
                return "{}";
            }
            List<String> fields = new ArrayList<>();
            node.properties().forEach(field -> fields.add(pad + "\"" + field.getKey() + "\": " + pretty(field.getValue(), depth + 1)));
            return "{\n" + String.join(",\n", fields) + "\n" + "  ".repeat(depth) + "}";
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                return "[]";
            }
            List<String> items = new ArrayList<>();
            node.forEach(item -> items.add(pad + pretty(item, depth + 1)));
            return "[\n" + String.join(",\n", items) + "\n" + "  ".repeat(depth) + "]";
        }
        return node.toString();
    }
}
