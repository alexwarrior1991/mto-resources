package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.messaging.DomainEvent;
import com.alejandro.mtoresources.domain.model.ExpiryRules;
import com.alejandro.mtoresources.domain.model.UnavailablePeriod;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Instrument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonQualification;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los eventos que este servicio cuenta de si mismo: su entidad, su nombre y lo que viaja en
 * {@code values}. Es la definicion del contrato con {@code mto-maintenance} (y manana con
 * {@code mto-construction}), y por eso vive en un solo sitio y es publica:
 * {@code MessagingContractExamplesTest} la usa para comprobar que cada ejemplo versionado en
 * {@code docs/messaging/examples} sigue siendo lo que se publica.
 *
 * <p>En el contrato solo se anaden claves. Viaja lo que un consumidor necesita para su instantanea
 * (codigos, nombres, usuarios, categorias, estados), nunca el telefono ni nada que huela a secreto
 * ({@link DomainEvent} lo rechaza). Un valor nulo viaja como nulo: «sin supervisor» es informacion.
 * La marca de agua de un consumidor es la cabecera {@code sequenceNumber} del outbox, no
 * {@code version}: cambiar el jefe de una supervision cambia sus equipos sin tocar su version.</p>
 *
 * <p>Los de personas, equipos, vehiculos e instrumentos llevan {@code republished}: {@code true} solo en
 * lo que publica {@code republish}, que repite lo que ya se conto para poner al dia a un consumidor (una
 * instantanea lo aplica igual; quien avisa de cambios, como {@code mto-notification}, no avisa de nada).
 * Los de vehiculo llevan ademas {@code availabilityChanged}: si ese cambio hizo que el vehiculo pasara de
 * disponible a no disponible o al reves, para que un aviso de «fuera de servicio» salga una vez y no con
 * cada cambio de notas.</p>
 *
 * <p>Una lista (las cualificaciones de una persona, los documentos de un vehiculo, cuando no esta
 * disponible) viaja siempre entera: quien guarda una instantanea sustituye la que tenia por la que llega.
 * Las fechas viajan como texto ISO ({@code 2027-03-31}).</p>
 *
 * <p>{@code unavailable} ({@code [{from, to}]}, las dos fechas incluidas y {@code to} nulo sin fin conocido)
 * dice cuando no esta disponible una persona (sus ausencias) o un vehiculo (las reparaciones que lo
 * inmovilizan, planificadas o abiertas), solo lo que termina hoy o despues y nunca el motivo: una baja
 * medica no sale de este servicio ({@code mto-notification} copia {@code values} en su registro). Todo quien
 * publica uno de esos eventos la pide a {@code Unavailability}.</p>
 *
 * <p>Los avisos de caducidad ({@code qualification}, {@code vehicle-document}, {@code calibration}, con
 * {@code expiring} y {@code expired}) no son una instantanea sino un hecho de un dia: no se republican, los
 * publica solo el vigilante diario y su {@code operationId} sale de lo que cuentan
 * ({@link #expiryOperationId}).</p>
 */
public final class ResourcesEvents {

    public static final String PERSON = "person";
    public static final String TEAM = "team";
    public static final String VEHICLE = "vehicle";
    public static final String INSTRUMENT = "instrument";
    public static final String QUALIFICATION = "qualification";
    public static final String VEHICLE_DOCUMENT = "vehicle-document";
    public static final String CALIBRATION = "calibration";

    public static final String CREATED = "created";
    public static final String UPDATED = "updated";
    public static final String DEACTIVATED = "deactivated";
    /** Caduca dentro de los dias de aviso (o hoy mismo) y aun vale. */
    public static final String EXPIRING = "expiring";
    /** Ya no vale: desde el dia siguiente al de su caducidad. */
    public static final String EXPIRED = "expired";

    private ResourcesEvents() {
    }

    // ------------------------------------------------------------------------------- people

    /**
     * @param qualifications todas las de la persona, tambien las caducadas: el consumidor decide por la
     *                       fecha. Viajan siempre enteras, porque quien guarda una instantanea sustituye la
     *                       lista que tenia por la que llega.
     * @param unavailable    sus ausencias que terminan hoy o despues, sin su clase
     */
    public static DomainEvent person(Person person, List<PersonQualification> qualifications, List<UnavailablePeriod> unavailable,
                                     String eventName) {
        return person(person, qualifications, unavailable, eventName, false);
    }

    public static DomainEvent person(Person person, List<PersonQualification> qualifications, List<UnavailablePeriod> unavailable,
                                     String eventName, boolean republished) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", person.getCode());
        values.put("firstName", person.getFirstName());
        values.put("lastName", person.getLastName());
        values.put("fullName", person.fullName());
        values.put("username", person.getUsername());
        values.put("employeeNumber", person.getEmployeeNumber());
        values.put("category", name(person.getCategory()));
        values.put("employer", name(person.getEmployer()));
        values.put("company", person.getCompany());
        values.put("active", person.getActive());
        values.put("version", person.getVersion());
        values.put("republished", republished);
        values.put("qualifications", qualifications.stream()
                .sorted(Comparator.comparing((PersonQualification qualification) -> qualification.getQualificationType().getCode()))
                .map(ResourcesEvents::qualificationRef)
                .toList());
        values.put("unavailable", periods(unavailable));
        return new DomainEvent(PERSON, id(person.getId()), eventName, values);
    }

    /** Lo que un consumidor necesita de una cualificacion para decidir si vale en una fecha: su codigo y hasta cuando. */
    private static Map<String, Object> qualificationRef(PersonQualification qualification) {
        QualificationType type = qualification.getQualificationType();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("code", type.getCode());
        ref.put("kind", name(type.getKind()));
        ref.put("drivingLicence", name(type.getDrivingLicence()));
        ref.put("expiresOn", date(qualification.getExpiresOn()));
        return ref;
    }

    // -------------------------------------------------------------------------------- teams

    /**
     * Lo que un consumidor necesita de un equipo, ya resuelto: su jefe, sus linemen, y el supervisor y
     * el jefe de supervisores que tiene encima (los jefes de las unidades de arriba mas cercanas).
     */
    public record TeamView(OrganizationUnit team, Person leader, List<Person> members, Person supervisor,
                           Person chiefSupervisor, UUID departmentId) {
    }

    public static DomainEvent team(TeamView view, String eventName) {
        return team(view, eventName, false);
    }

    public static DomainEvent team(TeamView view, String eventName, boolean republished) {
        OrganizationUnit team = view.team();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", team.getCode());
        values.put("name", team.getName());
        values.put("discipline", name(team.getDiscipline()));
        values.put("active", team.getActive());
        values.put("parentId", team.getParent() == null ? null : id(team.getParent().getId()));
        values.put("departmentId", id(view.departmentId()));
        values.put("executionPackageIds", team.getExecutionPackageIds().stream().sorted().toList());
        Vehicle vehicle = team.getDefaultVehicle();
        if (vehicle == null) {
            values.put("defaultVehicle", null);
        } else {
            Map<String, Object> defaultVehicle = new LinkedHashMap<>();
            defaultVehicle.put("id", id(vehicle.getId()));
            defaultVehicle.put("code", vehicle.getCode());
            values.put("defaultVehicle", defaultVehicle);
        }
        values.put("leader", personRef(view.leader()));
        values.put("supervisor", personRef(view.supervisor()));
        values.put("chiefSupervisor", personRef(view.chiefSupervisor()));
        List<Map<String, Object>> members = new ArrayList<>();
        for (Person member : view.members()) {
            members.add(personRef(member));
        }
        values.put("members", members);
        values.put("linemen", members.size());
        values.put("version", team.getVersion());
        values.put("republished", republished);
        return new DomainEvent(TEAM, id(team.getId()), eventName, values);
    }

    private static Map<String, Object> personRef(Person person) {
        if (person == null) {
            return null;
        }
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("personId", id(person.getId()));
        ref.put("code", person.getCode());
        ref.put("fullName", person.fullName());
        ref.put("username", person.getUsername());
        ref.put("category", name(person.getCategory()));
        return ref;
    }

    // ----------------------------------------------------------------------------- vehicles

    public static DomainEvent vehicle(Vehicle vehicle, List<VehicleDocument> documents, List<UnavailablePeriod> unavailable,
                                      String eventName) {
        return vehicle(vehicle, documents, unavailable, eventName, false, false);
    }

    /**
     * @param documents           todos los del vehiculo, tambien los caducados; viajan enteros, como las
     *                            cualificaciones de una persona
     * @param unavailable         las reparaciones que lo inmovilizan, planificadas o abiertas, que terminan
     *                            hoy o despues
     * @param availabilityChanged si el cambio que se cuenta paso el vehiculo de disponible a no disponible
     *                            o al reves (un alta, un cambio de su modelo, de sus documentos o una
     *                            republicacion no lo hacen)
     * @param republished         si es una republicacion de lo ya contado
     */
    public static DomainEvent vehicle(Vehicle vehicle, List<VehicleDocument> documents, List<UnavailablePeriod> unavailable,
                                      String eventName, boolean availabilityChanged, boolean republished) {
        VehicleType type = vehicle.getType();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", vehicle.getCode());
        values.put("name", vehicle.getName());
        Map<String, Object> typeValues = new LinkedHashMap<>();
        typeValues.put("id", id(type.getId()));
        typeValues.put("code", type.getCode());
        typeValues.put("name", type.getName());
        typeValues.put("mobility", name(type.getMobility()));
        typeValues.put("category", name(type.getCategory()));
        typeValues.put("selfPropelled", type.getSelfPropelled());
        typeValues.put("portable", type.getPortable());
        typeValues.put("seats", type.getSeats());
        typeValues.put("railGearType", name(type.getRailGearType()));
        typeValues.put("transportModeAuthorised", type.getTransportModeAuthorised());
        typeValues.put("capabilities", type.getCapabilities().stream().map(Enum::name).sorted().toList());
        QualificationType driver = type.getRequiredDriverQualification();
        typeValues.put("requiredDriverQualification", driver == null ? null : driver.getCode());
        values.put("type", typeValues);
        values.put("registrationPlate", vehicle.getRegistrationPlate());
        values.put("railVehicleNumber", vehicle.getRailVehicleNumber());
        values.put("operationalStatus", name(vehicle.getOperationalStatus()));
        values.put("available", vehicle.isAvailable());
        values.put("active", vehicle.getActive());
        values.put("version", vehicle.getVersion());
        values.put("availabilityChanged", availabilityChanged);
        values.put("republished", republished);
        values.put("documents", documents.stream()
                .sorted(Comparator.comparing((VehicleDocument document) -> document.getKind().name()))
                .map(ResourcesEvents::documentRef)
                .toList());
        values.put("unavailable", periods(unavailable));
        return new DomainEvent(VEHICLE, id(vehicle.getId()), eventName, values);
    }

    /** Lo que un consumidor necesita de un documento para decidir si vale en una fecha. */
    private static Map<String, Object> documentRef(VehicleDocument document) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("kind", name(document.getKind()));
        ref.put("reference", document.getReference());
        ref.put("expiresOn", date(document.getExpiresOn()));
        return ref;
    }

    // -------------------------------------------------------------------------- instruments

    public static DomainEvent instrument(Instrument instrument, String eventName) {
        return instrument(instrument, eventName, false);
    }

    /** Lo que necesita quien lo elige en un turno: que es, su numero de serie y hasta cuando vale su calibracion. */
    public static DomainEvent instrument(Instrument instrument, String eventName, boolean republished) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", instrument.getCode());
        values.put("name", instrument.getName());
        values.put("kind", name(instrument.getKind()));
        values.put("serialNumber", instrument.getSerialNumber());
        values.put("calibratedOn", date(instrument.getCalibratedOn()));
        values.put("calibrationExpiresOn", date(instrument.getCalibrationExpiresOn()));
        values.put("active", instrument.getActive());
        values.put("version", instrument.getVersion());
        values.put("republished", republished);
        return new DomainEvent(INSTRUMENT, id(instrument.getId()), eventName, values);
    }

    // ---------------------------------------------------------------------------- caducidades

    /**
     * El aviso de que una cualificacion caduca o ha caducado, para la persona, su jefe de equipo y quien
     * gestiona el personal. {@code teamCode} es el equipo abierto de la persona (como lineman o como jefe) y
     * {@code leader}, el jefe de ese equipo; nunca la propia persona: si ella es el jefe, nulo.
     */
    public static DomainEvent qualificationExpiry(PersonQualification qualification, String eventName, LocalDate today, String teamCode,
                                                  Person leader) {
        Person person = qualification.getPerson();
        QualificationType type = qualification.getQualificationType();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("personId", id(person.getId()));
        values.put("personCode", person.getCode());
        values.put("fullName", person.fullName());
        values.put("username", person.getUsername());
        values.put("qualificationCode", type.getCode());
        values.put("qualificationName", type.getName());
        values.put("kind", name(type.getKind()));
        values.put("expiresOn", date(qualification.getExpiresOn()));
        values.put("daysLeft", ExpiryRules.daysLeft(qualification.getExpiresOn(), today));
        values.put("teamCode", teamCode);
        values.put("leader", leader == null || leader.getId().equals(person.getId()) ? null : personRef(leader));
        return new DomainEvent(QUALIFICATION, id(qualification.getId()), eventName, values);
    }

    /** El aviso de que un documento de un vehiculo caduca o ha caducado. */
    public static DomainEvent vehicleDocumentExpiry(VehicleDocument document, String eventName, LocalDate today) {
        Vehicle vehicle = document.getVehicle();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("vehicleId", id(vehicle.getId()));
        values.put("vehicleCode", vehicle.getCode());
        values.put("vehicleName", vehicle.getName());
        values.put("kind", name(document.getKind()));
        values.put("reference", document.getReference());
        values.put("expiresOn", date(document.getExpiresOn()));
        values.put("daysLeft", ExpiryRules.daysLeft(document.getExpiresOn(), today));
        return new DomainEvent(VEHICLE_DOCUMENT, id(document.getId()), eventName, values);
    }

    /** El aviso de que la calibracion de un instrumento caduca o ha caducado; su id es el del instrumento. */
    public static DomainEvent calibrationExpiry(Instrument instrument, String eventName, LocalDate today) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("instrumentId", id(instrument.getId()));
        values.put("code", instrument.getCode());
        values.put("name", instrument.getName());
        values.put("kind", name(instrument.getKind()));
        values.put("serialNumber", instrument.getSerialNumber());
        values.put("calibrationExpiresOn", date(instrument.getCalibrationExpiresOn()));
        values.put("daysLeft", ExpiryRules.daysLeft(instrument.getCalibrationExpiresOn(), today));
        return new DomainEvent(CALIBRATION, id(instrument.getId()), eventName, values);
    }

    /**
     * El {@code operationId} de un aviso de caducidad: {@code UUID.nameUUIDFromBytes("<entidad>:<evento>:<id>:<caducidad>")}.
     * Dos instancias, o una segunda pasada que llegara a publicar, publican el mismo y el inbox del
     * consumidor descarta el duplicado; una renovacion es otra fecha y otro aviso.
     */
    public static UUID expiryOperationId(DomainEvent event, LocalDate expiresOn) {
        String key = event.entityName() + ":" + event.eventName() + ":" + event.entityId() + ":" + expiresOn;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    /** Cada periodo como {@code {from, to}}, en orden; nunca el motivo. */
    private static List<Map<String, Object>> periods(List<UnavailablePeriod> unavailable) {
        return unavailable.stream()
                .sorted(Comparator.comparing(UnavailablePeriod::from))
                .map(period -> {
                    Map<String, Object> ref = new LinkedHashMap<>();
                    ref.put("from", date(period.from()));
                    ref.put("to", date(period.to()));
                    return ref;
                })
                .toList();
    }

    private static String id(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /** Una fecha viaja como texto ISO ({@code 2027-03-31}), igual la serialice quien la serialice. */
    private static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
