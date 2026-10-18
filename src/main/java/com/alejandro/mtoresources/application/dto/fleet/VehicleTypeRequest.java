package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RailGearType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePower;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Alta de un modelo de vehiculo. {@code mobility} y {@code category} deciden el resto: un modelo de solo
 * carretera no lleva ancho de via, uno de solo via no lleva permiso de conducir, y solo una bivial lleva
 * su equipo ferroviario ({@code railGearType}: 9A, 9B o 9C). Sin {@code requiredDriverQualificationTypeId},
 * la habilitacion de su conductor es la de su movilidad: una de solo via autopropulsada, {@code RAIL-DRIVER};
 * una bivial, {@code RR-OPERATOR}; una de carretera, el permiso que pide ({@code LIC-B}, {@code LIC-C},
 * {@code LIC-CE}).
 *
 * <p>Ejemplo, {@code POST /vehicle-types} de una bivial:</p>
 * <pre>{@code
 * {"code": "UNIMOG-RR", "name": "Unimog road-rail", "manufacturer": "Mercedes-Benz", "mobility": "ROAD_RAIL",
 *  "category": "ROAD_RAIL_TRUCK", "seats": 7, "trackGaugeMm": 1435, "railGearType": "RAIL_WHEEL_DRIVE",
 *  "onTrackingMinutes": 10, "needsPreparedAccess": false, "maxSpeedRoadKmh": 90, "requiredLicence": "C",
 *  "capabilities": ["CRANE", "TOWING", "CREW_CABIN"], "craneCapacityT": 1.5, "towingCapacityT": 40}
 * }</pre>
 */
public record VehicleTypeRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "letters, digits, '.', '-' and '_' only") String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String manufacturer,
        @Size(max = 100) String model,
        @NotNull VehicleMobility mobility,
        @NotNull VehicleCategory category,
        Boolean selfPropelled,
        Boolean portable,
        @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal lengthM,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal widthM,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal heightM,
        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal tareT,
        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal maxGrossT,
        @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal maxAxleLoadT,
        @Min(1) Integer axles,
        @Min(0) Integer seats,
        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal payloadT,
        VehiclePower power,
        @Min(1) Integer trackGaugeMm,
        @Min(0) Integer maxSpeedTrackWorkingKmh,
        @Min(0) Integer maxSpeedTrackTransportKmh,
        @DecimalMin("0") @Digits(integer = 4, fraction = 1) BigDecimal maxGradientPermille,
        @Min(0) Integer minCurveRadiusM,
        @Min(0) Integer maxWorkingCantMm,
        Boolean transportModeAuthorised,
        RailGearType railGearType,
        @Min(0) Integer onTrackingMinutes,
        Boolean needsPreparedAccess,
        @Min(0) Integer maxSpeedRoadKmh,
        DrivingLicence requiredLicence,
        Set<VehicleCapability> capabilities,
        @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal craneCapacityT,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal craneReachM,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal platformHeightM,
        @Min(0) Integer platformLoadKg,
        @DecimalMin("0") @Digits(integer = 4, fraction = 1) BigDecimal platformInsulationKv,
        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal towingCapacityT,
        String notes,
        UUID requiredDriverQualificationTypeId
) {
}
