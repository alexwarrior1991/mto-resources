package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeRequest;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeResponse;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeUpdateRequest;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.PageMapper;
import com.alejandro.mtoresources.application.mapper.QualificationTypeMapper;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.QualificationTypeService;
import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonQualification;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationType;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonQualificationRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.QualificationTypeRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.QualificationTypeSpecification;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class QualificationTypeServiceImpl implements QualificationTypeService {

    private static final Set<String> CLEARABLE = Set.of("drivingLicence", "validityMonths", "description");

    private final QualificationTypeRepository repository;
    private final PersonRepository personRepository;
    private final PersonQualificationRepository qualificationRepository;
    private final QualificationTypeMapper mapper;
    private final DomainEventPublisher events;

    @Override
    @Transactional
    public QualificationTypeResponse create(QualificationTypeRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Qualification type", code);
        }
        QualificationType type = QualificationType.builder()
                .code(code)
                .name(PatchRules.requireText(request.name(), "name"))
                .kind(request.kind())
                .drivingLicence(request.drivingLicence())
                .validityMonths(request.validityMonths())
                .description(SpecificationUtils.normalize(request.description()))
                .build();
        requireConsistent(type);
        return mapper.toResponse(repository.save(type));
    }

    @Override
    @Transactional
    public QualificationTypeResponse update(UUID id, QualificationTypeUpdateRequest request) {
        return patch(id, MergePatch.of(request));
    }

    @Override
    @Transactional
    public QualificationTypeResponse patch(UUID id, MergePatch<QualificationTypeUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        QualificationTypeUpdateRequest values = patch.values();
        QualificationType type = type(id);
        StaleVersionException.check("Qualification type " + type.getCode(), type.getVersion(), values.version());

        // El tipo y el permiso viajan en el evento de cada persona que lo tiene: si se tocan, esas personas
        // se bloquean en orden de id antes de cambiar nada, como los vehiculos de un modelo.
        boolean touchesHolders = values.kind() != null || values.drivingLicence() != null || patch.clears("drivingLicence");
        List<Person> holders = touchesHolders ? personRepository.lockAllInIdOrder(qualificationRepository.findHolderIds(id)) : List.of();
        String before = holderFacing(type);

        if (values.name() != null) {
            type.setName(PatchRules.requireText(values.name(), "name"));
        }
        PatchRules.set(patch, "kind", values.kind(), type::setKind);
        PatchRules.set(patch, "drivingLicence", values.drivingLicence(), type::setDrivingLicence);
        // Lo que deja de ser un permiso de conducir deja de decir cual, sin tener que vaciarlo a mano (un PUT
        // no sabe vaciar); decirlo a la vez que otro tipo es el 400 de requireConsistent.
        if (type.getKind() != QualificationKind.DRIVING_LICENCE && values.drivingLicence() == null) {
            type.setDrivingLicence(null);
        }
        PatchRules.set(patch, "validityMonths", values.validityMonths(), type::setValidityMonths);
        PatchRules.set(patch, "description", SpecificationUtils.normalize(values.description()), type::setDescription);
        PatchRules.set(patch, "active", values.active(), type::setActive);
        requireConsistent(type);

        QualificationType saved = repository.saveAndFlush(type);
        if (!holders.isEmpty() && !before.equals(holderFacing(saved))) {
            Map<UUID, List<PersonQualification>> qualifications = qualificationRepository
                    .findAllOfPeople(holders.stream().map(Person::getId).toList()).stream()
                    .collect(Collectors.groupingBy(qualification -> qualification.getPerson().getId()));
            for (Person holder : holders) {
                events.publish(ResourcesEvents.person(holder, qualifications.getOrDefault(holder.getId(), List.of()),
                        ResourcesEvents.UPDATED));
            }
        }
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public QualificationTypeResponse findById(UUID id) {
        return mapper.toResponse(type(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QualificationTypeResponse> search(String search, QualificationKind kind, Boolean active, Pageable pageable) {
        return PageMapper.toPageResponse(repository.findAll(QualificationTypeSpecification.filter(search, kind, active), pageable),
                mapper::toResponse);
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        type(id).setActive(false);
    }

    private QualificationType type(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Qualification type", id));
    }

    /** Un permiso de conducir dice cual; lo demas, ninguno. */
    private static void requireConsistent(QualificationType type) {
        if (type.getKind() == QualificationKind.DRIVING_LICENCE && type.getDrivingLicence() == null) {
            throw new ValidationException("A driving licence says which one (drivingLicence: " + List.of(DrivingLicence.values()) + ")");
        }
        if (type.getKind() != QualificationKind.DRIVING_LICENCE && type.getDrivingLicence() != null) {
            throw new ValidationException("Only a DRIVING_LICENCE qualification carries a drivingLicence, not a " + type.getKind());
        }
    }

    /** Lo que sale de un tipo en el evento de quien lo tiene (el codigo no cambia). */
    private static String holderFacing(QualificationType type) {
        return type.getKind() + "|" + Objects.toString(type.getDrivingLicence(), "");
    }
}
