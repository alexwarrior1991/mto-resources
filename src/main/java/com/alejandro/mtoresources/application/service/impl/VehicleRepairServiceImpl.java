package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.repair.VehicleRepairRequest;
import com.alejandro.mtoresources.application.dto.repair.VehicleRepairResponse;
import com.alejandro.mtoresources.application.dto.repair.VehicleRepairUpdateRequest;
import com.alejandro.mtoresources.application.exception.FleetRuleException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.RepairRuleException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.VehicleRepairMapper;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.VehicleRepairService;
import com.alejandro.mtoresources.domain.model.RepairRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RepairStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleRepair;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleDocumentRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepairRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Toda escritura bloquea antes la fila del vehiculo: su evento lleva la lista entera de cuando no esta
 * disponible, y la reparacion puede cambiar su estado. Con el bloqueo, dos cambios a la vez se ponen en
 * fila y el ultimo evento lleva los dos.
 */
@Service
@RequiredArgsConstructor
class VehicleRepairServiceImpl implements VehicleRepairService {

    private static final Set<String> CLEARABLE = Set.of("workshop", "plannedTo", "odometerKm", "engineHours", "cost", "notes");

    private final VehicleRepairRepository repository;
    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentRepository documents;
    private final VehicleRepairMapper mapper;
    private final Unavailability unavailability;
    private final DomainEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<VehicleRepairResponse> findAll(UUID vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new NotFoundException("Vehicle", vehicleId);
        }
        return repository.findAllOfVehicle(vehicleId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public VehicleRepairResponse create(UUID vehicleId, VehicleRepairRequest request) {
        Vehicle vehicle = lockedVehicle(vehicleId);
        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            throw new FleetRuleException("Vehicle " + vehicle.getCode() + " is retired: it goes to no workshop");
        }
        RepairStatus status = request.status() == null ? RepairStatus.PLANNED : request.status();
        if (!RepairRules.isLive(status)) {
            throw new ValidationException("A repair is born PLANNED or OPEN, not " + status);
        }
        boolean wasAvailable = vehicle.isAvailable();
        VehicleRepair repair = VehicleRepair.builder()
                .code("REP-%06d".formatted(repository.nextCodeValue()))
                .vehicle(vehicle)
                .kind(request.kind())
                .status(status)
                .immobilises(request.immobilises() == null || request.immobilises())
                .description(PatchRules.requireText(request.description(), "description"))
                .workshop(SpecificationUtils.normalize(request.workshop()))
                .plannedFrom(request.plannedFrom())
                .plannedTo(request.plannedTo())
                .openedAt(status == RepairStatus.OPEN ? clock.instant() : null)
                .odometerKm(request.odometerKm())
                .engineHours(request.engineHours())
                .cost(request.cost())
                .notes(SpecificationUtils.normalize(request.notes()))
                .build();
        requireDates(repair);

        VehicleRepair saved = repository.saveAndFlush(repair);
        settleVehicle(vehicle, saved, false);
        publish(vehicle, wasAvailable);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VehicleRepairResponse patch(UUID vehicleId, UUID repairId, MergePatch<VehicleRepairUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        VehicleRepairUpdateRequest values = patch.values();
        Vehicle vehicle = lockedVehicle(vehicleId);
        VehicleRepair repair = repair(vehicleId, repairId);
        StaleVersionException.check("Repair " + repair.getCode(), repair.getVersion(), values.version());
        boolean wasAvailable = vehicle.isAvailable();

        boolean datesChange = values.plannedFrom() != null || values.plannedTo() != null || patch.clears("plannedTo");
        if (datesChange && !RepairRules.isLive(repair.getStatus())) {
            throw new RepairRuleException("Repair " + repair.getCode() + " is " + repair.getStatus() + ": its planned dates no longer change");
        }
        if (values.immobilises() != null && !values.immobilises().equals(repair.getImmobilises())
                && repair.getStatus() != RepairStatus.PLANNED) {
            throw new RepairRuleException("Repair " + repair.getCode() + " is " + repair.getStatus()
                    + ": whether it immobilises the vehicle is decided before opening it");
        }
        PatchRules.set(patch, "kind", values.kind(), repair::setKind);
        PatchRules.set(patch, "immobilises", values.immobilises(), repair::setImmobilises);
        if (values.description() != null) {
            repair.setDescription(PatchRules.requireText(values.description(), "description"));
        }
        PatchRules.set(patch, "workshop", SpecificationUtils.normalize(values.workshop()), repair::setWorkshop);
        PatchRules.set(patch, "plannedFrom", values.plannedFrom(), repair::setPlannedFrom);
        PatchRules.set(patch, "plannedTo", values.plannedTo(), repair::setPlannedTo);
        PatchRules.set(patch, "odometerKm", values.odometerKm(), repair::setOdometerKm);
        PatchRules.set(patch, "engineHours", values.engineHours(), repair::setEngineHours);
        PatchRules.set(patch, "cost", values.cost(), repair::setCost);
        PatchRules.set(patch, "notes", SpecificationUtils.normalize(values.notes()), repair::setNotes);
        requireDates(repair);

        VehicleRepair saved = repository.saveAndFlush(repair);
        publish(vehicle, wasAvailable);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VehicleRepairResponse open(UUID vehicleId, UUID repairId) {
        return transition(vehicleId, repairId, RepairStatus.OPEN);
    }

    @Override
    @Transactional
    public VehicleRepairResponse close(UUID vehicleId, UUID repairId) {
        return transition(vehicleId, repairId, RepairStatus.CLOSED);
    }

    @Override
    @Transactional
    public VehicleRepairResponse cancel(UUID vehicleId, UUID repairId) {
        return transition(vehicleId, repairId, RepairStatus.CANCELLED);
    }

    private VehicleRepairResponse transition(UUID vehicleId, UUID repairId, RepairStatus target) {
        Vehicle vehicle = lockedVehicle(vehicleId);
        VehicleRepair repair = repair(vehicleId, repairId);
        RepairRules.transitionViolation(repair.getCode(), repair.getStatus(), target).ifPresent(reason -> {
            throw new RepairRuleException(reason);
        });
        if (target == RepairStatus.OPEN && !Boolean.TRUE.equals(vehicle.getActive())) {
            throw new FleetRuleException("Vehicle " + vehicle.getCode() + " is retired: it goes to no workshop");
        }
        boolean wasAvailable = vehicle.isAvailable();
        boolean leavesTheWorkshop = repair.getStatus() == RepairStatus.OPEN;
        repair.setStatus(target);
        if (target == RepairStatus.OPEN) {
            repair.setOpenedAt(clock.instant());
        } else if (target == RepairStatus.CLOSED) {
            repair.setClosedAt(clock.instant());
        }

        VehicleRepair saved = repository.saveAndFlush(repair);
        settleVehicle(vehicle, saved, leavesTheWorkshop);
        publish(vehicle, wasAvailable);
        return mapper.toResponse(saved);
    }

    /**
     * La reparacion manda en el estado del vehiculo. Abierta y que inmoviliza: si podia trabajar, pasa a
     * {@code UNDER_REPAIR} con el codigo como motivo. Al salir del taller la ultima abierta que lo
     * inmovilizaba, vuelve a {@code OPERATIONAL} si sigue {@code UNDER_REPAIR}: uno {@code OUT_OF_SERVICE}
     * puesto a mano no se toca.
     */
    private void settleVehicle(Vehicle vehicle, VehicleRepair repair, boolean leftTheWorkshop) {
        if (!Boolean.TRUE.equals(repair.getImmobilises())) {
            return;
        }
        List<String> stillOpen = repository.findImmobilisingCodes(vehicle.getId(), RepairStatus.OPEN, repair.getId());
        if (repair.getStatus() == RepairStatus.OPEN && vehicle.getOperationalStatus().canWork()) {
            vehicle.setOperationalStatus(VehicleOperationalStatus.UNDER_REPAIR);
            vehicle.setStatusReason("Repair " + repair.getCode());
            vehicleRepository.saveAndFlush(vehicle);
        } else if (leftTheWorkshop && stillOpen.isEmpty() && vehicle.getOperationalStatus() == VehicleOperationalStatus.UNDER_REPAIR) {
            vehicle.setOperationalStatus(VehicleOperationalStatus.OPERATIONAL);
            vehicle.setStatusReason(null);
            vehicleRepository.saveAndFlush(vehicle);
        }
    }

    private Vehicle lockedVehicle(UUID vehicleId) {
        return vehicleRepository.findByIdForUpdate(vehicleId).orElseThrow(() -> new NotFoundException("Vehicle", vehicleId));
    }

    /** La de ese vehiculo: una de otro es un 404, como una que no existe. */
    private VehicleRepair repair(UUID vehicleId, UUID repairId) {
        return repository.findOfVehicle(vehicleId, repairId).orElseThrow(() -> new NotFoundException("Vehicle repair", repairId));
    }

    /** Una planificada dice desde cuando, y su fin previsto no es anterior a su inicio. */
    private static void requireDates(VehicleRepair repair) {
        if (repair.getStatus() == RepairStatus.PLANNED && repair.getPlannedFrom() == null) {
            throw new ValidationException("A planned repair says when it starts (plannedFrom)");
        }
        if (repair.getPlannedFrom() != null && repair.getPlannedTo() != null && repair.getPlannedTo().isBefore(repair.getPlannedFrom())) {
            throw new ValidationException("A repair cannot end (" + repair.getPlannedTo() + ") before it starts (" + repair.getPlannedFrom() + ")");
        }
    }

    /** Su {@code vehicle.updated} con sus documentos y cuando no esta disponible, tal como quedan. */
    private void publish(Vehicle vehicle, boolean wasAvailable) {
        events.publish(ResourcesEvents.vehicle(vehicle, documents.findAllOfVehicle(vehicle.getId()), unavailability.ofVehicle(vehicle.getId()),
                ResourcesEvents.UPDATED, vehicle.isAvailable() != wasAvailable, false));
    }
}
