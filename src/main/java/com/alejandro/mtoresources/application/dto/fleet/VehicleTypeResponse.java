package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeSummary;
import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RailGearType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePower;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Un modelo de vehiculo con todas sus caracteristicas.
 *
 * <p>Ejemplo, {@code GET /vehicle-types/{id}} de un castillete de catenaria:</p>
 * <pre>{@code
 * {"id": "…", "code": "DIC-30", "name": "Plasser DIC-30 MTW", "mobility": "RAIL_ONLY",
 *  "category": "CATENARY_TOWER_CAR", "selfPropelled": true, "seats": 8, "trackGaugeMm": 1435,
 *  "maxSpeedTrackTransportKmh": 100, "transportModeAuthorised": true,
 *  "capabilities": ["ELEVATING_PLATFORM", "INSULATED_PLATFORM", "PANTOGRAPH", "CREW_CABIN"],
 *  "platformHeightM": 6.5, "platformInsulationKv": 30.0,
 *  "requiredDriverQualification": {"id": "…", "code": "RAIL-DRIVER", "name": "Rail auxiliary vehicle driver", "kind": "VEHICLE_OPERATION"},
 *  "active": true, "version": 0, …}
 * }</pre>
 */
public record VehicleTypeResponse(
        UUID id,
        String code,
        String name,
        String manufacturer,
        String model,
        VehicleMobility mobility,
        VehicleCategory category,
        Boolean selfPropelled,
        Boolean portable,
        BigDecimal lengthM,
        BigDecimal widthM,
        BigDecimal heightM,
        BigDecimal tareT,
        BigDecimal maxGrossT,
        BigDecimal maxAxleLoadT,
        Integer axles,
        Integer seats,
        BigDecimal payloadT,
        VehiclePower power,
        Integer trackGaugeMm,
        Integer maxSpeedTrackWorkingKmh,
        Integer maxSpeedTrackTransportKmh,
        BigDecimal maxGradientPermille,
        Integer minCurveRadiusM,
        Integer maxWorkingCantMm,
        Boolean transportModeAuthorised,
        RailGearType railGearType,
        Integer onTrackingMinutes,
        Boolean needsPreparedAccess,
        Integer maxSpeedRoadKmh,
        DrivingLicence requiredLicence,
        Set<VehicleCapability> capabilities,
        BigDecimal craneCapacityT,
        BigDecimal craneReachM,
        BigDecimal platformHeightM,
        Integer platformLoadKg,
        BigDecimal platformInsulationKv,
        BigDecimal towingCapacityT,
        String notes,
        QualificationTypeSummary requiredDriverQualification,
        Boolean active,
        Long version,
        AuditMetadataResponse audit
) {
}
