package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.expiry.ExpiryWatchResult;
import com.alejandro.mtoresources.application.dto.messaging.DomainEvent;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.ExpiryWatchService;
import com.alejandro.mtoresources.configuration.expiry.ExpiryWatchProperties;
import com.alejandro.mtoresources.domain.model.ExpiryRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonQualification;
import com.alejandro.mtoresources.infrastructure.persistence.entity.TeamMember;
import com.alejandro.mtoresources.infrastructure.persistence.repository.ExpiryCandidate;
import com.alejandro.mtoresources.infrastructure.persistence.repository.InstrumentRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.OrganizationUnitRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonQualificationRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.TeamMemberRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Avisa de lo que caduca o ha caducado.
 *
 * <p>Primero lee lo que quiza haya que avisar (lo que caduca hasta dentro de {@code warning-days} dias y no
 * tiene la marca de esa fecha), y despues, una fila por transaccion, escribe la marca con una condicion
 * dentro de la sentencia ({@code ... is distinct from :expiresOn}): solo si la sentencia cambia una fila
 * se construye y publica el aviso, en esa misma transaccion. Con varias instancias, o con una segunda
 * pasada, la segunda sentencia no encuentra nada que cambiar; y si aun asi dos llegaran a publicar, el
 * {@code operationId} derivado de lo que se cuenta deja uno en el inbox del consumidor. Una fila que falla
 * no impide las demas: se registra y se mira otra vez manana.</p>
 *
 * <p>Sin publicador ({@code app.rabbitmq.enabled=false}) no se marca nada: se gastarian las marcas sin que
 * nadie se enterase.</p>
 */
@Service
class ExpiryWatchServiceImpl implements ExpiryWatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpiryWatchServiceImpl.class);

    private final PersonQualificationRepository qualificationRepository;
    private final VehicleDocumentRepository documentRepository;
    private final InstrumentRepository instrumentRepository;
    private final TeamMemberRepository memberRepository;
    private final OrganizationUnitRepository unitRepository;
    private final DomainEventPublisher events;
    private final ExpiryWatchProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    ExpiryWatchServiceImpl(PersonQualificationRepository qualificationRepository, VehicleDocumentRepository documentRepository,
                           InstrumentRepository instrumentRepository, TeamMemberRepository memberRepository,
                           OrganizationUnitRepository unitRepository, DomainEventPublisher events, ExpiryWatchProperties properties,
                           Clock clock, PlatformTransactionManager transactionManager) {
        this.qualificationRepository = qualificationRepository;
        this.documentRepository = documentRepository;
        this.instrumentRepository = instrumentRepository;
        this.memberRepository = memberRepository;
        this.unitRepository = unitRepository;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public ExpiryWatchResult check() {
        LocalDate today = LocalDate.now(clock);
        int warningDays = properties.warningDays();
        if (!events.isEnabled()) {
            LOGGER.info("Expiry check of {} skipped: nothing is published (app.rabbitmq.enabled=false)", today);
            return new ExpiryWatchResult(today, warningDays, 0, 0, 0);
        }
        LocalDate horizon = today.plusDays(warningDays);

        int qualifications = watch(qualificationRepository.findExpiryCandidates(today, horizon), today, warningDays,
                (candidate, eventName) -> qualification(candidate, eventName, today));
        int documents = watch(documentRepository.findExpiryCandidates(today, horizon), today, warningDays,
                (candidate, eventName) -> document(candidate, eventName, today));
        int calibrations = watch(instrumentRepository.findExpiryCandidates(today, horizon), today, warningDays,
                (candidate, eventName) -> calibration(candidate, eventName, today));

        ExpiryWatchResult result = new ExpiryWatchResult(today, warningDays, qualifications, documents, calibrations);
        LOGGER.info("Expiry check of {}: {} qualification, {} vehicle document and {} calibration warnings", today, qualifications,
                documents, calibrations);
        return result;
    }

    /** Lo que avisa de una fila: marca con la condicion y, si la marca cambio, publica. Dice si publico. */
    @FunctionalInterface
    private interface Warning {
        boolean warn(ExpiryCandidate candidate, String eventName);
    }

    private int watch(List<ExpiryCandidate> candidates, LocalDate today, int warningDays, Warning warning) {
        int published = 0;
        for (ExpiryCandidate candidate : candidates) {
            Optional<ExpiryRules.Stage> stage = ExpiryRules.stage(candidate.getExpiresOn(), today, warningDays);
            if (stage.isEmpty()) {
                continue;
            }
            String eventName = stage.get() == ExpiryRules.Stage.EXPIRED ? ResourcesEvents.EXPIRED : ResourcesEvents.EXPIRING;
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> warning.warn(candidate, eventName)))) {
                    published++;
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Expiry warning {} of {} not published, it is tried again on the next check", eventName, candidate.getId(),
                        failure);
            }
        }
        return published;
    }

    private boolean qualification(ExpiryCandidate candidate, String eventName, LocalDate today) {
        if (!mark(eventName, candidate, qualificationRepository::markExpiringNotified, qualificationRepository::markExpiredNotified)) {
            return false;
        }
        PersonQualification qualification = qualificationRepository.findWithPersonAndType(candidate.getId()).orElseThrow();
        Person person = qualification.getPerson();
        // Su equipo abierto: en el que va de lineman, con su jefe; o el que dirige, y entonces sin jefe.
        Optional<TeamMember> membership = memberRepository.findOpenByPerson(person.getId());
        String teamCode = null;
        Person leader = null;
        if (membership.isPresent()) {
            OrganizationUnit team = membership.get().getTeam();
            teamCode = team.getCode();
            leader = team.getHead();
        } else {
            Optional<OrganizationUnit> led = unitRepository.findByHeadIdAndActiveTrue(person.getId()).stream()
                    .filter(OrganizationUnit::isTeam).findFirst();
            teamCode = led.map(OrganizationUnit::getCode).orElse(null);
        }
        publish(ResourcesEvents.qualificationExpiry(qualification, eventName, today, teamCode, leader), candidate.getExpiresOn());
        return true;
    }

    private boolean document(ExpiryCandidate candidate, String eventName, LocalDate today) {
        if (!mark(eventName, candidate, documentRepository::markExpiringNotified, documentRepository::markExpiredNotified)) {
            return false;
        }
        publish(ResourcesEvents.vehicleDocumentExpiry(documentRepository.findWithVehicle(candidate.getId()).orElseThrow(), eventName, today),
                candidate.getExpiresOn());
        return true;
    }

    private boolean calibration(ExpiryCandidate candidate, String eventName, LocalDate today) {
        if (!mark(eventName, candidate, instrumentRepository::markExpiringNotified, instrumentRepository::markExpiredNotified)) {
            return false;
        }
        publish(ResourcesEvents.calibrationExpiry(instrumentRepository.findById(candidate.getId()).orElseThrow(), eventName, today),
                candidate.getExpiresOn());
        return true;
    }

    @FunctionalInterface
    private interface Mark {
        int apply(UUID id, LocalDate expiresOn);
    }

    /** La marca condicional: una fila cambiada es que nadie habia avisado aun de esa fecha. */
    private static boolean mark(String eventName, ExpiryCandidate candidate, Mark expiring, Mark expired) {
        Mark mark = ResourcesEvents.EXPIRED.equals(eventName) ? expired : expiring;
        return mark.apply(candidate.getId(), candidate.getExpiresOn()) == 1;
    }

    private void publish(DomainEvent event, LocalDate expiresOn) {
        events.publish(ResourcesEvents.expiryOperationId(event, expiresOn), event);
    }
}
