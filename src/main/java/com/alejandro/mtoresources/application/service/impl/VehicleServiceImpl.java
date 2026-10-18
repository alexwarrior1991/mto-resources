package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleUpdateRequest;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.FleetRuleException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.PageMapper;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.EntityAuditService;
import com.alejandro.mtoresources.application.service.VehicleService;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOwner;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePosition;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleType;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleDocumentRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehiclePositionRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleTypeRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import com.alejandro.mtoresources.infrastructure.persistence.specification.VehicleSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class VehicleServiceImpl implements VehicleService {

    private static final Set<String> CLEARABLE = Set.of("name", "registrationPlate", "railVehicleNumber", "serialNumber",
            "manufactureYear", "ownerCompany", "statusReason", "odometerKm", "engineHours", "notes");

    private final VehicleRepository repository;
    private final VehicleTypeRepository typeRepository;
    private final VehiclePositionRepository positionRepository;
    private final VehicleDocumentRepository documents;
    private final FleetResponses responses;
    private final EntityAuditService auditService;
    private final DomainEventPublisher events;

    @Override
    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Vehicle", code);
        }
        Vehicle vehicle = Vehicle.builder()
                .code(code)
                .name(SpecificationUtils.normalize(request.name()))
                .type(activeType(request.typeId()))
                .registrationPlate(identifier(request.registrationPlate()))
                .railVehicleNumber(identifier(request.railVehicleNumber()))
                .serialNumber(SpecificationUtils.normalize(request.serialNumber()))
                .manufactureYear(request.manufactureYear())
                .owner(request.owner() == null ? VehicleOwner.OWN : request.owner())
                .ownerCompany(SpecificationUtils.normalize(request.ownerCompany()))
                .operationalStatus(request.operationalStatus() == null ? VehicleOperationalStatus.OPERATIONAL : request.operationalStatus())
                .statusReason(SpecificationUtils.normalize(request.statusReason()))
                .odometerKm(request.odometerKm())
                .engineHours(request.engineHours())
                .notes(SpecificationUtils.normalize(request.notes()))
                .build();
        requireConsistent(vehicle);

        Vehicle saved = repository.saveAndFlush(vehicle);
        // Un vehiculo nuevo aun no tiene documentos: se dan despues, en /vehicles/{id}/documents.
        events.publish(ResourcesEvents.vehicle(saved, List.of(), ResourcesEvents.CREATED));
        return responses.vehicle(saved, null);
    }

    @Override
    @Transactional
    public VehicleResponse update(UUID id, VehicleUpdateRequest request) {
        return patch(id, MergePatch.of(request));
    }

    @Override
    @Transactional
    public VehicleResponse patch(UUID id, MergePatch<VehicleUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        VehicleUpdateRequest values = patch.values();
        // Bloqueada: un cambio de su modelo tambien la bloquea antes de publicar su evento.
        Vehicle vehicle = repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Vehicle", id));
        StaleVersionException.check("Vehicle " + vehicle.getCode(), vehicle.getVersion(), values.version());
        boolean wasAvailable = vehicle.isAvailable();

        if (values.isDeactivating()) {
            retire(vehicle);
            return responses.vehicle(vehicle, positionRepository.findById(id).orElse(null));
        }

        PatchRules.set(patch, "name", SpecificationUtils.normalize(values.name()), vehicle::setName);
        if (values.typeId() != null && !values.typeId().equals(vehicle.getType().getId())) {
            vehicle.setType(activeType(values.typeId()));
        }
        PatchRules.set(patch, "registrationPlate", identifier(values.registrationPlate()), vehicle::setRegistrationPlate);
        PatchRules.set(patch, "railVehicleNumber", identifier(values.railVehicleNumber()), vehicle::setRailVehicleNumber);
        PatchRules.set(patch, "serialNumber", SpecificationUtils.normalize(values.serialNumber()), vehicle::setSerialNumber);
        PatchRules.set(patch, "manufactureYear", values.manufactureYear(), vehicle::setManufactureYear);
        PatchRules.set(patch, "owner", values.owner(), vehicle::setOwner);
        PatchRules.set(patch, "ownerCompany", SpecificationUtils.normalize(values.ownerCompany()), vehicle::setOwnerCompany);
        if (values.operationalStatus() != null && values.operationalStatus() != vehicle.getOperationalStatus()) {
            vehicle.setOperationalStatus(values.operationalStatus());
            // De vuelta a operativo sin motivo nuevo: el de la averia ya no aplica.
            if (values.operationalStatus() == VehicleOperationalStatus.OPERATIONAL && values.statusReason() == null) {
                vehicle.setStatusReason(null);
            }
        }
        PatchRules.set(patch, "statusReason", SpecificationUtils.normalize(values.statusReason()), vehicle::setStatusReason);
        PatchRules.set(patch, "odometerKm", values.odometerKm(), vehicle::setOdometerKm);
        PatchRules.set(patch, "engineHours", values.engineHours(), vehicle::setEngineHours);
        PatchRules.set(patch, "notes", SpecificationUtils.normalize(values.notes()), vehicle::setNotes);
        if (Boolean.TRUE.equals(values.active()) && !Boolean.TRUE.equals(vehicle.getActive())) {
            if (!Boolean.TRUE.equals(vehicle.getType().getActive())) {
                throw new FleetRuleException("Vehicle type " + vehicle.getType().getCode() + " is retired: give the vehicle another one "
                        + "to bring it back");
            }
            vehicle.setActive(true);
        }
        requireConsistent(vehicle);

        Vehicle saved = repository.saveAndFlush(vehicle);
        events.publish(ResourcesEvents.vehicle(saved, documents.findAllOfVehicle(id), ResourcesEvents.UPDATED,
                saved.isAvailable() != wasAvailable, false));
        return responses.vehicle(saved, positionRepository.findById(id).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse findById(UUID id) {
        Vehicle vehicle = repository.findById(id).orElseThrow(() -> new NotFoundException("Vehicle", id));
        return responses.vehicle(vehicle, positionRepository.findById(id).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> search(String search, UUID typeId, VehicleMobility mobility, VehicleCategory category,
                                                VehicleOperationalStatus status, Boolean available, Boolean active, Pageable pageable) {
        Page<Vehicle> page = repository.findAll(VehicleSpecification.filter(search, typeId, mobility, category, status, available, active),
                pageable);
        Map<UUID, VehiclePosition> positions = new HashMap<>();
        if (page.hasContent()) {
            positionRepository.findAllByVehicleIds(page.getContent().stream().map(Vehicle::getId).toList())
                    .forEach(position -> positions.put(position.getVehicleId(), position));
        }
        return PageMapper.toPageResponse(page, vehicle -> responses.vehicle(vehicle, positions.get(vehicle.getId())));
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        retire(repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Vehicle", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<VehicleResponse>> findRevisions(UUID id, Pageable pageable) {
        return auditService.findRevisions(Vehicle.class, id, vehicle -> responses.vehicle(vehicle, null), pageable);
    }

    /** El vehiculo por defecto de un equipo activo no se retira sin darle antes otro al equipo. */
    private void retire(Vehicle vehicle) {
        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            return;
        }
        List<String> teams = repository.findActiveTeamsUsingAsDefault(vehicle.getId());
        if (!teams.isEmpty()) {
            throw new FleetRuleException("Vehicle " + vehicle.getCode() + " is the default vehicle of team " + String.join(", ", teams)
                    + ": give the team another one first");
        }
        boolean wasAvailable = vehicle.isAvailable();
        vehicle.setActive(false);
        Vehicle saved = repository.saveAndFlush(vehicle);
        events.publish(ResourcesEvents.vehicle(saved, documents.findAllOfVehicle(saved.getId()), ResourcesEvents.DEACTIVATED,
                wasAvailable, false));
    }

    private VehicleType activeType(UUID typeId) {
        VehicleType type = typeRepository.findById(typeId).orElseThrow(() -> new NotFoundException("Vehicle type", typeId));
        if (!Boolean.TRUE.equals(type.getActive())) {
            throw new FleetRuleException("Vehicle type " + type.getCode() + " is retired: no new vehicle is of it");
        }
        return type;
    }

    /**
     * Lo que su modelo exige: matricula si va por carretera (y ninguna si es de solo via), numero de
     * vehiculo ferroviario si puede ir por via abierta (y ninguno si es de solo carretera), empresa si no
     * es propio, motivo si no esta operativo; y matricula y numero que no tenga otro.
     */
    private void requireConsistent(Vehicle vehicle) {
        VehicleType type = vehicle.getType();
        VehicleMobility mobility = type.getMobility();
        if (mobility.runsOnRoad() && vehicle.getRegistrationPlate() == null) {
            throw new ValidationException("A " + type.getCategory() + " runs on the road: it has its registration plate");
        }
        if (mobility == VehicleMobility.RAIL_ONLY && vehicle.getRegistrationPlate() != null) {
            throw new ValidationException("A rail-only vehicle has no registration plate");
        }
        if (Boolean.TRUE.equals(type.getTransportModeAuthorised()) && vehicle.getRailVehicleNumber() == null) {
            throw new ValidationException("Model " + type.getCode() + " runs on open track in transport mode: the vehicle has its rail "
                    + "vehicle number");
        }
        if (mobility == VehicleMobility.ROAD_ONLY && vehicle.getRailVehicleNumber() != null) {
            throw new ValidationException("A road-only vehicle has no rail vehicle number");
        }
        if (vehicle.getOwner() != VehicleOwner.OWN && vehicle.getOwnerCompany() == null) {
            throw new ValidationException("A " + vehicle.getOwner() + " vehicle says whose it is (ownerCompany)");
        }
        if (vehicle.getOperationalStatus() != VehicleOperationalStatus.OPERATIONAL && vehicle.getStatusReason() == null) {
            throw new ValidationException("A vehicle that is " + vehicle.getOperationalStatus() + " says why (statusReason)");
        }
        if (vehicle.getRegistrationPlate() != null && repository.existsRegistrationPlate(vehicle.getRegistrationPlate(), vehicle.getId())) {
            throw new DuplicateCodeException("Vehicle", "registration plate", vehicle.getRegistrationPlate());
        }
        if (vehicle.getRailVehicleNumber() != null && repository.existsRailVehicleNumber(vehicle.getRailVehicleNumber(), vehicle.getId())) {
            throw new DuplicateCodeException("Vehicle", "rail vehicle number", vehicle.getRailVehicleNumber());
        }
    }

    private static String identifier(String value) {
        String normalized = SpecificationUtils.normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
