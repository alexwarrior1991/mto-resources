package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceRequest;
import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceResponse;
import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceUpdateRequest;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.exception.AbsenceRuleException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.PersonAbsenceMapper;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.PersonAbsenceService;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonAbsence;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonAbsenceRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonQualificationRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Cada escritura bloquea antes la fila de la persona, como sus cualificaciones: su evento lleva la lista
 * entera de cuando no esta, y dos cambios a la vez publicarian cada uno una lista sin el del otro. Con el
 * bloqueo tambien se decide el solape sin que otra ausencia entre a la vez.
 */
@Service
@RequiredArgsConstructor
class PersonAbsenceServiceImpl implements PersonAbsenceService {

    private static final Set<String> CLEARABLE = Set.of("notes");

    private final PersonAbsenceRepository repository;
    private final PersonRepository personRepository;
    private final PersonQualificationRepository qualifications;
    private final PersonAbsenceMapper mapper;
    private final Unavailability unavailability;
    private final DomainEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public List<PersonAbsenceResponse> findAll(UUID personId) {
        if (!personRepository.existsById(personId)) {
            throw new NotFoundException("Person", personId);
        }
        return repository.findAllOfPerson(personId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public PersonAbsenceResponse create(UUID personId, PersonAbsenceRequest request) {
        Person person = lockedPerson(personId);
        PersonAbsence absence = PersonAbsence.builder()
                .person(person)
                .kind(request.kind())
                .startsOn(request.startsOn())
                .endsOn(request.endsOn())
                .notes(SpecificationUtils.normalize(request.notes()))
                .build();
        requireConsistent(person, absence);

        PersonAbsence saved = repository.saveAndFlush(absence);
        publish(person);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PersonAbsenceResponse patch(UUID personId, UUID absenceId, MergePatch<PersonAbsenceUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        PersonAbsenceUpdateRequest values = patch.values();
        Person person = lockedPerson(personId);
        PersonAbsence absence = absence(personId, absenceId);
        StaleVersionException.check("Absence of " + person.getCode() + " from " + absence.getStartsOn(), absence.getVersion(),
                values.version());

        PatchRules.set(patch, "kind", values.kind(), absence::setKind);
        PatchRules.set(patch, "startsOn", values.startsOn(), absence::setStartsOn);
        PatchRules.set(patch, "endsOn", values.endsOn(), absence::setEndsOn);
        PatchRules.set(patch, "notes", SpecificationUtils.normalize(values.notes()), absence::setNotes);
        requireConsistent(person, absence);

        PersonAbsence saved = repository.saveAndFlush(absence);
        publish(person);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID personId, UUID absenceId) {
        Person person = lockedPerson(personId);
        repository.delete(absence(personId, absenceId));
        repository.flush();
        publish(person);
    }

    private Person lockedPerson(UUID personId) {
        return personRepository.findByIdForUpdate(personId).orElseThrow(() -> new NotFoundException("Person", personId));
    }

    /** La de esa persona: una de otra es un 404, como una que no existe. */
    private PersonAbsence absence(UUID personId, UUID absenceId) {
        return repository.findOfPerson(personId, absenceId).orElseThrow(() -> new NotFoundException("Person absence", absenceId));
    }

    /** El fin no es anterior al inicio, y no se solapa con otra suya. */
    private void requireConsistent(Person person, PersonAbsence absence) {
        if (absence.getEndsOn().isBefore(absence.getStartsOn())) {
            throw new ValidationException("An absence cannot end (" + absence.getEndsOn() + ") before it starts (" + absence.getStartsOn() + ")");
        }
        repository.findOverlapping(person.getId(), absence.getStartsOn(), absence.getEndsOn()).stream()
                .filter(other -> !Objects.equals(other.getId(), absence.getId()))
                .findFirst()
                .ifPresent(other -> {
                    throw new AbsenceRuleException(person.fullName() + " is already absent from " + other.getStartsOn() + " to "
                            + other.getEndsOn() + ": change that absence instead of adding one that overlaps it");
                });
    }

    /** Su {@code person.updated} con sus cualificaciones y cuando no esta, tal como quedan. */
    private void publish(Person person) {
        events.publish(ResourcesEvents.person(person, qualifications.findAllOfPerson(person.getId()), unavailability.ofPerson(person.getId()),
                ResourcesEvents.UPDATED));
    }
}
