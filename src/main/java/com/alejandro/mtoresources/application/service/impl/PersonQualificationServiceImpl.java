package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationRequest;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationResponse;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationUpdateRequest;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.QualificationRuleException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.PersonQualificationMapper;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.PersonQualificationService;
import com.alejandro.mtoresources.domain.model.ExpiryRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonQualification;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationType;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonQualificationRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.QualificationTypeRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cada escritura bloquea antes la fila de la persona: su evento lleva todas sus cualificaciones, y dos
 * cambios a la vez publicarian cada uno una lista sin la del otro. Con el bloqueo, la segunda espera a
 * la primera y su evento, el ultimo, lleva las dos.
 */
@Service
@RequiredArgsConstructor
class PersonQualificationServiceImpl implements PersonQualificationService {

    private static final Set<String> CLEARABLE = Set.of("reference", "issuedOn", "expiresOn", "notes");

    private final PersonQualificationRepository repository;
    private final QualificationTypeRepository typeRepository;
    private final PersonRepository personRepository;
    private final PersonQualificationMapper mapper;
    private final DomainEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<PersonQualificationResponse> findAll(UUID personId) {
        if (!personRepository.existsById(personId)) {
            throw new NotFoundException("Person", personId);
        }
        LocalDate today = LocalDate.now(clock);
        return repository.findAllOfPerson(personId).stream().map(qualification -> mapper.toResponse(qualification, today)).toList();
    }

    @Override
    @Transactional
    public PersonQualificationResponse create(UUID personId, PersonQualificationRequest request) {
        Person person = lockedPerson(personId);
        QualificationType type = typeRepository.findById(request.qualificationTypeId())
                .orElseThrow(() -> new NotFoundException("Qualification type", request.qualificationTypeId()));
        if (!Boolean.TRUE.equals(type.getActive())) {
            throw new QualificationRuleException("Qualification type " + type.getCode() + " is retired: no new one is given");
        }
        if (repository.existsByPersonIdAndQualificationTypeId(personId, type.getId())) {
            throw new QualificationRuleException(person.fullName() + " already has " + type.getCode()
                    + ": renew it by changing its expiry");
        }
        LocalDate expiresOn = request.expiresOn() != null
                ? request.expiresOn()
                : ExpiryRules.defaultExpiry(request.issuedOn(), type.getValidityMonths()).orElse(null);
        PersonQualification qualification = PersonQualification.builder()
                .person(person)
                .qualificationType(type)
                .reference(SpecificationUtils.normalize(request.reference()))
                .issuedOn(request.issuedOn())
                .expiresOn(expiresOn)
                .notes(SpecificationUtils.normalize(request.notes()))
                .build();
        requireDates(qualification);

        PersonQualification saved = repository.saveAndFlush(qualification);
        publish(person);
        return mapper.toResponse(saved, LocalDate.now(clock));
    }

    @Override
    @Transactional
    public PersonQualificationResponse patch(UUID personId, UUID qualificationId, MergePatch<PersonQualificationUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        PersonQualificationUpdateRequest values = patch.values();
        Person person = lockedPerson(personId);
        PersonQualification qualification = qualification(personId, qualificationId);
        StaleVersionException.check("Qualification " + qualification.getQualificationType().getCode() + " of " + person.getCode(),
                qualification.getVersion(), values.version());

        PatchRules.set(patch, "reference", SpecificationUtils.normalize(values.reference()), qualification::setReference);
        PatchRules.set(patch, "issuedOn", values.issuedOn(), qualification::setIssuedOn);
        PatchRules.set(patch, "expiresOn", values.expiresOn(), qualification::setExpiresOn);
        PatchRules.set(patch, "notes", SpecificationUtils.normalize(values.notes()), qualification::setNotes);
        requireDates(qualification);

        PersonQualification saved = repository.saveAndFlush(qualification);
        publish(person);
        return mapper.toResponse(saved, LocalDate.now(clock));
    }

    @Override
    @Transactional
    public void delete(UUID personId, UUID qualificationId) {
        Person person = lockedPerson(personId);
        repository.delete(qualification(personId, qualificationId));
        repository.flush();
        publish(person);
    }

    private Person lockedPerson(UUID personId) {
        return personRepository.findByIdForUpdate(personId).orElseThrow(() -> new NotFoundException("Person", personId));
    }

    /** La de esa persona: una de otra es un 404, como una que no existe. */
    private PersonQualification qualification(UUID personId, UUID qualificationId) {
        return repository.findOfPerson(personId, qualificationId)
                .orElseThrow(() -> new NotFoundException("Person qualification", qualificationId));
    }

    /**
     * Su {@code person.updated} con la lista entera tal como queda. Tambien de una persona desactivada (con
     * {@code active: false}): un {@code deactivated} mas diria a quien avisa de bajas que se fue otra vez.
     */
    private void publish(Person person) {
        events.publish(ResourcesEvents.person(person, repository.findAllOfPerson(person.getId()), ResourcesEvents.UPDATED));
    }

    private static void requireDates(PersonQualification qualification) {
        ExpiryRules.datesViolation("A qualification", qualification.getIssuedOn(), qualification.getExpiresOn()).ifPresent(reason -> {
            throw new ValidationException(reason);
        });
    }
}
