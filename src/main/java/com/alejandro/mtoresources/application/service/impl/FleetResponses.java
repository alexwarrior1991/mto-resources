package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleSummary;
import com.alejandro.mtoresources.application.mapper.AuditableMapper;
import com.alejandro.mtoresources.application.mapper.ParkingSiteMapper;
import com.alejandro.mtoresources.application.mapper.TrackAccessPointMapper;
import com.alejandro.mtoresources.application.mapper.VehicleTypeMapper;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationEvent;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Las respuestas de la flota que se montan a mano: la de un vehiculo lleva su posicion, que no es un
 * campo suyo, y la de una ubicacion nombra su sitio, su punto de acceso y su portador. El historico, la
 * posicion de un vehiculo y el tablero salen todos de {@link #location}.
 */
@Component
@RequiredArgsConstructor
class FleetResponses {

    private final VehicleTypeMapper typeMapper;
    private final ParkingSiteMapper siteMapper;
    private final TrackAccessPointMapper accessPointMapper;
    private final AuditableMapper auditableMapper;

    static VehicleSummary summary(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        return new VehicleSummary(vehicle.getId(), vehicle.getCode(), vehicle.getName(),
                vehicle.getType().getMobility(), vehicle.getType().getCategory());
    }

    VehicleResponse vehicle(Vehicle vehicle, VehiclePosition position) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getCode(),
                vehicle.getName(),
                typeMapper.toSummary(vehicle.getType()),
                vehicle.getRegistrationPlate(),
                vehicle.getRailVehicleNumber(),
                vehicle.getSerialNumber(),
                vehicle.getManufactureYear(),
                vehicle.getOwner(),
                vehicle.getOwnerCompany(),
                vehicle.getOperationalStatus(),
                vehicle.getStatusReason(),
                vehicle.isAvailable(),
                vehicle.getOdometerKm(),
                vehicle.getEngineHours(),
                vehicle.getNotes(),
                position == null ? null : location(position.getLocationEvent()),
                vehicle.getActive(),
                vehicle.getVersion(),
                auditableMapper.toAuditMetadata(vehicle)
        );
    }

    VehicleLocationResponse location(VehicleLocationEvent event) {
        if (event == null) {
            return null;
        }
        return new VehicleLocationResponse(
                event.getId(),
                event.getKind(),
                event.getParkingSite() == null ? null : siteMapper.toSummary(event.getParkingSite()),
                event.getExecutionPackageId(),
                event.getTrackId(),
                event.getKp(),
                event.getLatitude(),
                event.getLongitude(),
                event.getAccessPoint(),
                event.getTrackAccessPoint() == null ? null : accessPointMapper.toSummary(event.getTrackAccessPoint()),
                event.getDescription(),
                summary(event.getCarrier()),
                event.getAttachment(),
                event.getSource(),
                event.getSourceReference(),
                event.getObservedAt(),
                event.getRecordedAt(),
                event.getRecordedBy()
        );
    }
}
