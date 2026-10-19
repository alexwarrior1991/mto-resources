package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.operation.RepublishResult;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.RepublishService;
import com.alejandro.mtoresources.domain.model.UnavailablePeriod;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Instrument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonQualification;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocument;
import com.alejandro.mtoresources.infrastructure.persistence.repository.InstrumentRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.OrganizationUnitRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonQualificationRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleDocumentRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Republica en lotes de {@value #BATCH} filas, cada lote en su transaccion y con sus filas bloqueadas
 * en orden de id antes de leerlas: un cambio que se cruce con la republicacion publica despues que ella,
 * y el consumidor se queda con lo ultimo.
 */
@Service
class RepublishServiceImpl implements RepublishService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepublishServiceImpl.class);
    static final int BATCH = 200;

    private final PersonRepository personRepository;
    private final PersonQualificationRepository qualificationRepository;
    private final OrganizationUnitRepository unitRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentRepository documentRepository;
    private final InstrumentRepository instrumentRepository;
    private final OrganizationLookups lookups;
    private final DomainEventPublisher events;
    private final Unavailability unavailability;
    private final TransactionTemplate transactions;

    RepublishServiceImpl(PersonRepository personRepository, PersonQualificationRepository qualificationRepository,
                         OrganizationUnitRepository unitRepository, VehicleRepository vehicleRepository,
                         VehicleDocumentRepository documentRepository, InstrumentRepository instrumentRepository,
                         OrganizationLookups lookups, DomainEventPublisher events, Unavailability unavailability,
                         PlatformTransactionManager transactionManager) {
        this.personRepository = personRepository;
        this.qualificationRepository = qualificationRepository;
        this.unitRepository = unitRepository;
        this.vehicleRepository = vehicleRepository;
        this.documentRepository = documentRepository;
        this.instrumentRepository = instrumentRepository;
        this.lookups = lookups;
        this.events = events;
        this.unavailability = unavailability;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public RepublishResult republish(Long executionPackageId) {
        RepublishResult result = executionPackageId == null ? everything() : ofExecutionPackage(executionPackageId);
        LOGGER.info("Republished {} people, {} teams, {} vehicles and {} instruments{}", result.people(), result.teams(), result.vehicles(),
                result.instruments(), executionPackageId == null ? "" : " of execution package " + executionPackageId);
        return result;
    }

    private RepublishResult everything() {
        int people = pages(personRepository::findAllIds, this::people);
        int teams = pages(unitRepository::findTeamIds, this::teams);
        int vehicles = pages(vehicleRepository::findAllIds, this::vehicles);
        int instruments = pages(instrumentRepository::findAllIds, this::instruments);
        return new RepublishResult(null, people, teams, vehicles, instruments);
    }

    private RepublishResult ofExecutionPackage(Long executionPackageId) {
        List<UUID> teamIds = unitRepository.findTeamIdsOfExecutionPackage(executionPackageId);
        Set<UUID> personIds = new LinkedHashSet<>();
        Set<UUID> vehicleIds = new LinkedHashSet<>();
        transactions.executeWithoutResult(status -> {
            for (OrganizationUnit team : unitRepository.findAllById(teamIds)) {
                ResourcesEvents.TeamView view = lookups.teamView(team);
                add(personIds, view.leader());
                add(personIds, view.supervisor());
                add(personIds, view.chiefSupervisor());
                view.members().forEach(member -> add(personIds, member));
                if (team.getDefaultVehicle() != null) {
                    vehicleIds.add(team.getDefaultVehicle().getId());
                }
            }
        });
        int people = batches(new ArrayList<>(personIds), this::people);
        int teams = batches(teamIds, this::teams);
        int vehicles = batches(new ArrayList<>(vehicleIds), this::vehicles);
        // Un instrumento no es de ningun EP: sale al republicarlo todo.
        return new RepublishResult(executionPackageId, people, teams, vehicles, 0);
    }

    private int people(Collection<UUID> ids) {
        Integer published = transactions.execute(status -> {
            List<Person> people = personRepository.lockAllInIdOrder(ids);
            Map<UUID, List<PersonQualification>> qualifications = qualificationRepository.findAllOfPeople(ids).stream()
                    .collect(Collectors.groupingBy(qualification -> qualification.getPerson().getId()));
            Map<UUID, List<UnavailablePeriod>> unavailable = unavailability.ofPeople(ids);
            people.forEach(person -> events.publish(ResourcesEvents.person(person,
                    qualifications.getOrDefault(person.getId(), List.of()), unavailable.getOrDefault(person.getId(), List.of()),
                    eventName(person.getActive()), true)));
            return people.size();
        });
        return published == null ? 0 : published;
    }

    private int teams(Collection<UUID> ids) {
        Integer published = transactions.execute(status -> {
            List<OrganizationUnit> teams = lookups.lockTeams(ids);
            lookups.republishTeams(teams.stream().filter(team -> Boolean.TRUE.equals(team.getActive())).toList(), ResourcesEvents.UPDATED);
            lookups.republishTeams(teams.stream().filter(team -> !Boolean.TRUE.equals(team.getActive())).toList(), ResourcesEvents.DEACTIVATED);
            return teams.size();
        });
        return published == null ? 0 : published;
    }

    private int vehicles(Collection<UUID> ids) {
        Integer published = transactions.execute(status -> {
            List<Vehicle> vehicles = vehicleRepository.lockAllInIdOrder(ids);
            Map<UUID, List<VehicleDocument>> documents = documentRepository.findAllByVehicleIds(ids).stream()
                    .collect(Collectors.groupingBy(document -> document.getVehicle().getId()));
            Map<UUID, List<UnavailablePeriod>> unavailable = unavailability.ofVehicles(ids);
            vehicles.forEach(vehicle -> events.publish(ResourcesEvents.vehicle(vehicle, documents.getOrDefault(vehicle.getId(), List.of()),
                    unavailable.getOrDefault(vehicle.getId(), List.of()), eventName(vehicle.getActive()), false, true)));
            return vehicles.size();
        });
        return published == null ? 0 : published;
    }

    private int instruments(Collection<UUID> ids) {
        Integer published = transactions.execute(status -> {
            List<Instrument> instruments = instrumentRepository.lockAllInIdOrder(ids);
            instruments.forEach(instrument -> events.publish(ResourcesEvents.instrument(instrument, eventName(instrument.getActive()), true)));
            return instruments.size();
        });
        return published == null ? 0 : published;
    }

    private static int pages(Function<PageRequest, Page<UUID>> ids, Function<Collection<UUID>, Integer> publish) {
        int total = 0;
        PageRequest request = PageRequest.of(0, BATCH);
        while (true) {
            Page<UUID> page = ids.apply(request);
            if (page.hasContent()) {
                total += publish.apply(page.getContent());
            }
            if (!page.hasNext()) {
                return total;
            }
            request = request.next();
        }
    }

    private static int batches(List<UUID> ids, Function<Collection<UUID>, Integer> publish) {
        int total = 0;
        for (int from = 0; from < ids.size(); from += BATCH) {
            total += publish.apply(ids.subList(from, Math.min(ids.size(), from + BATCH)));
        }
        return total;
    }

    private static void add(Set<UUID> ids, Person person) {
        if (person != null) {
            ids.add(person.getId());
        }
    }

    private static String eventName(Boolean active) {
        return Boolean.TRUE.equals(active) ? ResourcesEvents.UPDATED : ResourcesEvents.DEACTIVATED;
    }
}
