package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentUpdateRequest;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.VehicleDocumentMapper;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.VehicleDocumentService;
import com.alejandro.mtoresources.domain.model.ExpiryRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocument;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleDocumentRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepository;
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
 * Cada escritura bloquea antes la fila del vehiculo, como un cambio de su ficha o de su modelo: su evento
 * lleva todos sus documentos, y el ultimo tiene que llevar los de todas las transacciones.
 */
@Service
@RequiredArgsConstructor
class VehicleDocumentServiceImpl implements VehicleDocumentService {

    /** La caducidad no se vacia: un documento siempre caduca. */
    private static final Set<String> CLEARABLE = Set.of("reference", "issuedOn", "notes");

    private final VehicleDocumentRepository repository;
    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentMapper mapper;
    private final DomainEventPublisher events;
    private final Unavailability unavailability;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<VehicleDocumentResponse> findAll(UUID vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new NotFoundException("Vehicle", vehicleId);
        }
        LocalDate today = LocalDate.now(clock);
        return repository.findAllOfVehicle(vehicleId).stream().map(document -> mapper.toResponse(document, today)).toList();
    }

    @Override
    @Transactional
    public VehicleDocumentResponse create(UUID vehicleId, VehicleDocumentRequest request) {
        Vehicle vehicle = lockedVehicle(vehicleId);
        if (repository.existsByVehicleIdAndKind(vehicleId, request.kind())) {
            throw new DuplicateCodeException("Vehicle document", "kind", request.kind() + " of vehicle " + vehicle.getCode());
        }
        VehicleDocument document = VehicleDocument.builder()
                .vehicle(vehicle)
                .kind(request.kind())
                .reference(SpecificationUtils.normalize(request.reference()))
                .issuedOn(request.issuedOn())
                .expiresOn(request.expiresOn())
                .notes(SpecificationUtils.normalize(request.notes()))
                .build();
        requireDates(document);

        VehicleDocument saved = repository.saveAndFlush(document);
        publish(vehicle);
        return mapper.toResponse(saved, LocalDate.now(clock));
    }

    @Override
    @Transactional
    public VehicleDocumentResponse patch(UUID vehicleId, UUID documentId, MergePatch<VehicleDocumentUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        VehicleDocumentUpdateRequest values = patch.values();
        Vehicle vehicle = lockedVehicle(vehicleId);
        VehicleDocument document = document(vehicleId, documentId);
        StaleVersionException.check(document.getKind() + " of " + vehicle.getCode(), document.getVersion(), values.version());

        PatchRules.set(patch, "reference", SpecificationUtils.normalize(values.reference()), document::setReference);
        PatchRules.set(patch, "issuedOn", values.issuedOn(), document::setIssuedOn);
        PatchRules.set(patch, "expiresOn", values.expiresOn(), document::setExpiresOn);
        PatchRules.set(patch, "notes", SpecificationUtils.normalize(values.notes()), document::setNotes);
        requireDates(document);

        VehicleDocument saved = repository.saveAndFlush(document);
        publish(vehicle);
        return mapper.toResponse(saved, LocalDate.now(clock));
    }

    @Override
    @Transactional
    public void delete(UUID vehicleId, UUID documentId) {
        Vehicle vehicle = lockedVehicle(vehicleId);
        repository.delete(document(vehicleId, documentId));
        repository.flush();
        publish(vehicle);
    }

    private Vehicle lockedVehicle(UUID vehicleId) {
        return vehicleRepository.findByIdForUpdate(vehicleId).orElseThrow(() -> new NotFoundException("Vehicle", vehicleId));
    }

    /** El de ese vehiculo: uno de otro es un 404, como uno que no existe. */
    private VehicleDocument document(UUID vehicleId, UUID documentId) {
        return repository.findOfVehicle(vehicleId, documentId).orElseThrow(() -> new NotFoundException("Vehicle document", documentId));
    }

    /**
     * Su {@code vehicle.updated} con la lista entera tal como queda; tambien el de un vehiculo retirado (con
     * {@code active: false}). Un documento no cambia si el vehiculo esta disponible.
     */
    private void publish(Vehicle vehicle) {
        events.publish(ResourcesEvents.vehicle(vehicle, repository.findAllOfVehicle(vehicle.getId()),
                unavailability.ofVehicle(vehicle.getId()), ResourcesEvents.UPDATED, false, false));
    }

    private static void requireDates(VehicleDocument document) {
        ExpiryRules.datesViolation("A " + document.getKind() + " document", document.getIssuedOn(), document.getExpiresOn())
                .ifPresent(reason -> {
                    throw new ValidationException(reason);
                });
    }
}
