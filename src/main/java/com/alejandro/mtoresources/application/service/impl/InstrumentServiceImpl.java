package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentRequest;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentResponse;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentUpdateRequest;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.InstrumentMapper;
import com.alejandro.mtoresources.application.mapper.PageMapper;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.EntityAuditService;
import com.alejandro.mtoresources.application.service.InstrumentService;
import com.alejandro.mtoresources.domain.model.ExpiryRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Instrument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.InstrumentKind;
import com.alejandro.mtoresources.infrastructure.persistence.repository.InstrumentRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.InstrumentSpecification;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class InstrumentServiceImpl implements InstrumentService {

    private static final Set<String> CLEARABLE = Set.of("serialNumber", "calibratedOn", "calibrationExpiresOn", "notes");

    private final InstrumentRepository repository;
    private final InstrumentMapper mapper;
    private final EntityAuditService auditService;
    private final DomainEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional
    public InstrumentResponse create(InstrumentRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Instrument", code);
        }
        Instrument instrument = Instrument.builder()
                .code(code)
                .name(PatchRules.requireText(request.name(), "name"))
                .kind(request.kind())
                .serialNumber(SpecificationUtils.normalize(request.serialNumber()))
                .calibratedOn(request.calibratedOn())
                .calibrationExpiresOn(request.calibrationExpiresOn())
                .notes(SpecificationUtils.normalize(request.notes()))
                .build();
        requireDates(instrument);

        Instrument saved = repository.saveAndFlush(instrument);
        events.publish(ResourcesEvents.instrument(saved, ResourcesEvents.CREATED));
        return mapper.toResponse(saved, LocalDate.now(clock));
    }

    @Override
    @Transactional
    public InstrumentResponse update(UUID id, InstrumentUpdateRequest request) {
        return patch(id, MergePatch.of(request));
    }

    @Override
    @Transactional
    public InstrumentResponse patch(UUID id, MergePatch<InstrumentUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        InstrumentUpdateRequest values = patch.values();
        // Bloqueado: su evento sale en el orden de los commits.
        Instrument instrument = lockedInstrument(id);
        StaleVersionException.check("Instrument " + instrument.getCode(), instrument.getVersion(), values.version());
        boolean wasActive = Boolean.TRUE.equals(instrument.getActive());

        if (values.name() != null) {
            instrument.setName(PatchRules.requireText(values.name(), "name"));
        }
        PatchRules.set(patch, "kind", values.kind(), instrument::setKind);
        PatchRules.set(patch, "serialNumber", SpecificationUtils.normalize(values.serialNumber()), instrument::setSerialNumber);
        PatchRules.set(patch, "calibratedOn", values.calibratedOn(), instrument::setCalibratedOn);
        PatchRules.set(patch, "calibrationExpiresOn", values.calibrationExpiresOn(), instrument::setCalibrationExpiresOn);
        PatchRules.set(patch, "notes", SpecificationUtils.normalize(values.notes()), instrument::setNotes);
        PatchRules.set(patch, "active", values.active(), instrument::setActive);
        requireDates(instrument);

        Instrument saved = repository.saveAndFlush(instrument);
        // Se retira una vez: lo que se cambia de uno ya retirado es un updated, no otra baja.
        boolean retired = wasActive && !Boolean.TRUE.equals(saved.getActive());
        events.publish(ResourcesEvents.instrument(saved, retired ? ResourcesEvents.DEACTIVATED : ResourcesEvents.UPDATED));
        return mapper.toResponse(saved, LocalDate.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public InstrumentResponse findById(UUID id) {
        return mapper.toResponse(repository.findById(id).orElseThrow(() -> new NotFoundException("Instrument", id)), LocalDate.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InstrumentResponse> search(String search, InstrumentKind kind, Boolean active, LocalDate calibrationExpiresBefore,
                                                   Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return PageMapper.toPageResponse(repository.findAll(InstrumentSpecification.filter(search, kind, active, calibrationExpiresBefore),
                pageable), instrument -> mapper.toResponse(instrument, today));
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        Instrument instrument = lockedInstrument(id);
        if (!Boolean.TRUE.equals(instrument.getActive())) {
            return;
        }
        instrument.setActive(false);
        Instrument saved = repository.saveAndFlush(instrument);
        events.publish(ResourcesEvents.instrument(saved, ResourcesEvents.DEACTIVATED));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<InstrumentResponse>> findRevisions(UUID id, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return auditService.findRevisions(Instrument.class, id, instrument -> mapper.toResponse(instrument, today), pageable);
    }

    private Instrument lockedInstrument(UUID id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Instrument", id));
    }

    private static void requireDates(Instrument instrument) {
        ExpiryRules.datesViolation("A calibration", instrument.getCalibratedOn(), instrument.getCalibrationExpiresOn())
                .ifPresent(reason -> {
                    throw new ValidationException(reason);
                });
    }
}
