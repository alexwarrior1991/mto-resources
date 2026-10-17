package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.messaging.DomainEvent;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleType;

import java.util.ArrayList;
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
 */
public final class ResourcesEvents {

    public static final String PERSON = "person";
    public static final String TEAM = "team";
    public static final String VEHICLE = "vehicle";

    public static final String CREATED = "created";
    public static final String UPDATED = "updated";
    public static final String DEACTIVATED = "deactivated";

    private ResourcesEvents() {
    }

    // ------------------------------------------------------------------------------- people

    public static DomainEvent person(Person person, String eventName) {
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
        return new DomainEvent(PERSON, id(person.getId()), eventName, values);
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

    public static DomainEvent vehicle(Vehicle vehicle, String eventName) {
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
        values.put("type", typeValues);
        values.put("registrationPlate", vehicle.getRegistrationPlate());
        values.put("railVehicleNumber", vehicle.getRailVehicleNumber());
        values.put("operationalStatus", name(vehicle.getOperationalStatus()));
        values.put("available", vehicle.isAvailable());
        values.put("active", vehicle.getActive());
        values.put("version", vehicle.getVersion());
        return new DomainEvent(VEHICLE, id(vehicle.getId()), eventName, values);
    }

    private static String id(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
