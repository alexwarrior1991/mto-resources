package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.FleetPositionResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationResponse;
import com.alejandro.mtoresources.application.exception.BusinessException;
import com.alejandro.mtoresources.application.exception.FleetRuleException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.PageMapper;
import com.alejandro.mtoresources.application.service.VehicleLocationService;
import com.alejandro.mtoresources.configuration.AuditActorResolver;
import com.alejandro.mtoresources.domain.model.FleetRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSite;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationEvent;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationSource;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePosition;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleType;
import com.alejandro.mtoresources.infrastructure.persistence.repository.ParkingSiteRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleLocationEventRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehiclePositionRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import com.alejandro.mtoresources.infrastructure.persistence.specification.VehiclePositionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class VehicleLocationServiceImpl implements VehicleLocationService {

    /** Lo que se tolera de reloj adelantado en quien registra: un minuto, no una ubicacion del futuro. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final VehicleRepository vehicleRepository;
    private final ParkingSiteRepository siteRepository;
    private final VehicleLocationEventRepository eventRepository;
    private final VehiclePositionRepository positionRepository;
    private final FleetResponses responses;
    private final Clock clock;

    @Override
    @Transactional
    public VehicleLocationResponse recordManual(UUID vehicleId, VehicleLocationRequest request) {
        return record(vehicleId, request, VehicleLocationSource.MANUAL, null);
    }

    /**
     * Las reglas se comprueban antes de escribir nada, asi que un rechazo no deja nada a medias: no marca
     * para deshacer la transaccion de quien llama (el consumidor de turnos, que sigue con el siguiente
     * vehiculo).
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public VehicleLocationResponse record(UUID vehicleId, VehicleLocationRequest request, VehicleLocationSource source,
                                          String sourceReference) {
        Vehicle vehicle = vehicle(vehicleId);
        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            throw new FleetRuleException("Vehicle " + vehicle.getCode() + " is retired: it does not move");
        }
        Instant now = clock.instant();
        Instant observedAt = request.observedAt() == null ? now : request.observedAt();
        if (observedAt.isAfter(now.plus(CLOCK_SKEW))) {
            throw new ValidationException("observedAt " + observedAt + " is in the future: a location is recorded once it happened");
        }
        if (sourceReference != null && eventRepository.existsByVehicleIdAndSourceReferenceAndObservedAt(vehicleId, sourceReference, observedAt)) {
            return null;
        }
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new ValidationException("Coordinates travel together: latitude and longitude");
        }

        VehicleLocationKind kind = request.kind();
        ParkingSite site = request.parkingSiteId() == null ? null : site(request.parkingSiteId());
        Long executionPackageId = request.executionPackageId();
        Long trackId = request.trackId();
        var kp = request.kp();
        if (site != null) {
            // Lo que el sitio ya sabe, para que el tablero lo encuentre por EP y por via.
            executionPackageId = executionPackageId == null ? site.getExecutionPackageId() : executionPackageId;
            if (trackId == null && site.getTrackId() != null) {
                trackId = site.getTrackId();
                kp = kp == null ? site.getKp() : kp;
            }
        }
        String description = SpecificationUtils.normalize(request.description());

        Vehicle carrier = null;
        if (kind == VehicleLocationKind.ATTACHED) {
            carrier = carrier(vehicle, request);
        } else if (request.carrierVehicleId() != null || request.attachment() != null) {
            throw new ValidationException("Only an ATTACHED vehicle has a carrier and an attachment");
        }
        if (kind == VehicleLocationKind.ON_TRACK && trackId == null) {
            throw new ValidationException("A vehicle ON_TRACK says its track (trackId)");
        }
        if (kind == VehicleLocationKind.PARKED && site == null && trackId == null && description == null && request.latitude() == null) {
            throw new ValidationException("A PARKED vehicle says where: a parking site, a track and kp, a description or coordinates");
        }
        boolean onTrack = site != null ? Boolean.TRUE.equals(site.getRailAccess()) : trackId != null;
        boolean offTrack = site != null ? Boolean.TRUE.equals(site.getRoadAccess())
                : trackId == null && (description != null || request.latitude() != null);
        FleetRules.locationViolation("Vehicle " + vehicle.getCode(), vehicle.getType().getMobility(), kind, onTrack, offTrack)
                .ifPresent(reason -> {
                    throw new FleetRuleException(reason);
                });

        VehicleLocationEvent event = eventRepository.saveAndFlush(VehicleLocationEvent.builder()
                .vehicle(vehicle)
                .kind(kind)
                .parkingSite(site)
                .executionPackageId(executionPackageId)
                .trackId(trackId)
                .kp(kp)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .accessPoint(SpecificationUtils.normalize(request.accessPoint()))
                .description(description)
                .carrier(carrier)
                .attachment(carrier == null ? null : request.attachment())
                .source(source)
                .sourceReference(sourceReference)
                .observedAt(observedAt)
                .recordedAt(now)
                .recordedBy(AuditActorResolver.currentActor())
                .build());
        // La marca de agua va dentro del upsert: lo observado antes que la posicion actual no la mueve.
        positionRepository.moveTo(event.getId());
        return responses.location(event);
    }

    /**
     * El portador de un vehiculo enganchado: otro, activo, que no va a su vez enganchado y que puede
     * remolcarlo o transportarlo. Un vehiculo que lleva a otros no se engancha a un tercero.
     */
    private Vehicle carrier(Vehicle vehicle, VehicleLocationRequest request) {
        if (request.carrierVehicleId() == null || request.attachment() == null) {
            throw new ValidationException("An ATTACHED vehicle says what carries it (carrierVehicleId) and how (attachment: TOWED or CARRIED)");
        }
        if (request.carrierVehicleId().equals(vehicle.getId())) {
            throw new ValidationException("A vehicle is not attached to itself");
        }
        Vehicle carrier = vehicle(request.carrierVehicleId());
        if (!Boolean.TRUE.equals(carrier.getActive())) {
            throw new FleetRuleException("Vehicle " + carrier.getCode() + " is retired: it carries nothing");
        }
        positionRepository.findById(carrier.getId())
                .filter(position -> position.getKind() == VehicleLocationKind.ATTACHED)
                .ifPresent(position -> {
                    throw new FleetRuleException("Vehicle " + carrier.getCode() + " is itself attached to another: attach "
                            + vehicle.getCode() + " to the one that carries it");
                });
        if (!positionRepository.findByCarrierVehicleId(vehicle.getId()).isEmpty()) {
            throw new FleetRuleException("Vehicle " + vehicle.getCode() + " carries others: it is not attached to a third one");
        }
        VehicleType type = vehicle.getType();
        VehicleType carrierType = carrier.getType();
        FleetRules.attachmentViolation("Vehicle " + vehicle.getCode(), type.getMobility(), Boolean.TRUE.equals(type.getPortable()),
                        request.attachment(), "Vehicle " + carrier.getCode(), carrierType.getMobility(), carrierType.getCapabilities())
                .ifPresent(reason -> {
                    throw new FleetRuleException(reason);
                });
        return carrier;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleLocationResponse> history(UUID vehicleId, Instant from, Instant to, Pageable pageable) {
        vehicle(vehicleId);
        return PageMapper.toPageResponse(eventRepository.findHistory(vehicleId, from, to, pageable), responses::location);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FleetPositionResponse> positions(Long executionPackageId, Long trackId, VehicleLocationKind kind, Pageable pageable) {
        Page<VehiclePosition> page = positionRepository.findAll(VehiclePositionSpecification.filter(executionPackageId, trackId, kind), pageable);
        Set<UUID> ids = new HashSet<>();
        page.forEach(position -> {
            ids.add(position.getVehicleId());
            if (position.getCarrierVehicleId() != null) {
                ids.add(position.getCarrierVehicleId());
            }
        });
        Map<UUID, Vehicle> vehicles = new HashMap<>();
        Map<UUID, VehiclePosition> positions = new HashMap<>();
        if (!ids.isEmpty()) {
            vehicleRepository.findAllWithType(ids).forEach(vehicle -> vehicles.put(vehicle.getId(), vehicle));
            positionRepository.findAllByVehicleIds(ids).forEach(position -> positions.put(position.getVehicleId(), position));
        }
        return PageMapper.toPageResponse(page, position -> {
            Vehicle vehicle = vehicles.get(position.getVehicleId());
            VehiclePosition effective = position.getCarrierVehicleId() == null ? null : positions.get(position.getCarrierVehicleId());
            return new FleetPositionResponse(
                    FleetResponses.summary(vehicle),
                    vehicle.getOperationalStatus(),
                    vehicle.isAvailable(),
                    responses.location(position.getLocationEvent()),
                    effective == null ? null : responses.location(effective.getLocationEvent()));
        });
    }

    private Vehicle vehicle(UUID id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new NotFoundException("Vehicle", id));
    }

    private ParkingSite site(UUID id) {
        ParkingSite site = siteRepository.findById(id).orElseThrow(() -> new NotFoundException("Parking site", id));
        if (!Boolean.TRUE.equals(site.getActive())) {
            throw new FleetRuleException("Parking site " + site.getCode() + " is out of the catalogue: say the place with a track and kp "
                    + "or a description");
        }
        return site;
    }
}
