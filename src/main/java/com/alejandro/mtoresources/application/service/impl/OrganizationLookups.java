package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.messaging.DomainEvent;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.TeamMember;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.repository.OrganizationUnitRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.OrganizationUnitRepository.UnitLink;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.TeamMemberRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que comparten los servicios de personas, unidades y vehiculos al tocar el organigrama: cargar
 * con su 404, subir por el arbol hasta el supervisor de un equipo, bajar hasta los equipos de una
 * unidad, y republicar los equipos afectados.
 *
 * <p><b>Republicar en orden.</b> Un consumidor descarta lo que llega con una marca de agua (la cabecera
 * {@code sequenceNumber} del outbox) menor que la que ya aplico. Para que el orden de esas marcas sea el
 * de los commits, toda transaccion que vaya a publicar un {@code team.updated} bloquea antes las filas
 * de esos equipos en orden de id ({@link #lockTeams}), y construye la vista despues: la segunda
 * transaccion espera a la primera y lee lo que esta dejo.</p>
 */
@Component
@RequiredArgsConstructor
class OrganizationLookups {

    private final OrganizationUnitRepository unitRepository;
    private final PersonRepository personRepository;
    private final TeamMemberRepository memberRepository;
    private final VehicleRepository vehicleRepository;
    private final DomainEventPublisher events;
    private final EntityManager entityManager;

    OrganizationUnit unit(UUID id) {
        return unitRepository.findById(id).orElseThrow(() -> new NotFoundException("Organization unit", id));
    }

    OrganizationUnit lockedUnit(UUID id) {
        return unitRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Organization unit", id));
    }

    Person person(UUID id) {
        return personRepository.findById(id).orElseThrow(() -> new NotFoundException("Person", id));
    }

    Vehicle vehicle(UUID id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new NotFoundException("Vehicle", id));
    }

    /** La unidad de arriba mas cercana de ese nivel, si la hay. */
    static Optional<OrganizationUnit> nearest(OrganizationUnit unit, OrganizationUnitKind kind) {
        OrganizationUnit current = unit.getParent();
        while (current != null) {
            if (current.getKind() == kind) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    /** La disciplina que hereda una unidad nueva: la de su padre, o la de mas arriba. */
    static Optional<OrganizationUnit> nearestWithDiscipline(OrganizationUnit unit) {
        OrganizationUnit current = unit;
        while (current != null) {
            if (current.getDiscipline() != null) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    ResourcesEvents.TeamView teamView(OrganizationUnit team) {
        List<Person> members = memberRepository.findOpenByTeam(team.getId()).stream().map(TeamMember::getPerson).toList();
        Person supervisor = nearest(team, OrganizationUnitKind.SUPERVISION).map(OrganizationUnit::getHead).orElse(null);
        Person chiefSupervisor = nearest(team, OrganizationUnitKind.CHIEF_SUPERVISION).map(OrganizationUnit::getHead).orElse(null);
        UUID departmentId = nearest(team, OrganizationUnitKind.DEPARTMENT).map(OrganizationUnit::getId).orElse(null);
        return new ResourcesEvents.TeamView(team, team.getHead(), members, supervisor, chiefSupervisor, departmentId);
    }

    /**
     * Los equipos que cuelgan de esas unidades, a cualquier profundidad, incluidas ellas si son
     * equipos. El organigrama es pequeno: se lee entero en una consulta de aristas.
     */
    Set<UUID> teamsUnder(Collection<UUID> unitIds) {
        return descendants(unitIds, true);
    }

    /** Esas unidades y todas las que cuelgan de ellas, a cualquier profundidad. */
    Set<UUID> unitsUnder(Collection<UUID> unitIds) {
        return descendants(unitIds, false);
    }

    private Set<UUID> descendants(Collection<UUID> unitIds, boolean teamsOnly) {
        if (unitIds.isEmpty()) {
            return Set.of();
        }
        List<UnitLink> links = unitRepository.findAllLinks();
        Map<UUID, List<UnitLink>> children = new HashMap<>();
        Map<UUID, UnitLink> byId = new HashMap<>();
        for (UnitLink link : links) {
            byId.put(link.getId(), link);
            if (link.getParentId() != null) {
                children.computeIfAbsent(link.getParentId(), ignored -> new ArrayList<>()).add(link);
            }
        }
        Set<UUID> teams = new LinkedHashSet<>();
        Deque<UUID> pending = new ArrayDeque<>(unitIds);
        Set<UUID> seen = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            UUID id = pending.pop();
            if (!seen.add(id)) {
                continue;
            }
            UnitLink link = byId.get(id);
            if (link != null && (!teamsOnly || link.getKind() == OrganizationUnitKind.TEAM)) {
                teams.add(id);
            }
            for (UnitLink child : children.getOrDefault(id, List.of())) {
                pending.push(child.getId());
            }
        }
        return teams;
    }

    /** Las unidades que dirige una persona y el equipo en el que va de lineman: lo que cambia si cambia ella. */
    Set<UUID> teamsReferencing(UUID personId) {
        Set<UUID> units = new LinkedHashSet<>();
        unitRepository.findByHeadIdAndActiveTrue(personId).forEach(unit -> units.add(unit.getId()));
        memberRepository.findOpenByPerson(personId).ifPresent(member -> units.add(member.getTeam().getId()));
        return teamsUnder(units);
    }

    /** Bloquea las filas de esos equipos en orden de id, antes de cambiar nada que salga en sus eventos. */
    List<OrganizationUnit> lockTeams(Collection<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return List.of();
        }
        return unitRepository.lockAllInIdOrder(teamIds).stream().filter(OrganizationUnit::isTeam).toList();
    }

    /**
     * Publica el evento de cada equipo con su vista de ahora. Vacia antes el contexto de persistencia,
     * para que la version y los linemen que viajan sean los que se van a confirmar.
     */
    void publishTeams(Collection<OrganizationUnit> teams, String eventName) {
        if (teams.isEmpty()) {
            return;
        }
        entityManager.flush();
        for (OrganizationUnit team : teams) {
            DomainEvent event = ResourcesEvents.team(teamView(team), eventName);
            events.publish(event);
        }
    }

    /** Un {@code team.updated} de cada equipo activo de la lista (uno inactivo ya conto su baja). */
    void publishActiveTeamsUpdated(Collection<OrganizationUnit> teams) {
        publishTeams(teams.stream().filter(team -> Boolean.TRUE.equals(team.getActive())).toList(), ResourcesEvents.UPDATED);
    }
}
