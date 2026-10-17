package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RailGearType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePower;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Modificacion de un modelo. Ni el codigo ni la movilidad cambian: un modelo de solo via no se vuelve
 * bivial, se da de alta otro. La categoria si, dentro de su movilidad. {@code capabilities} va entera:
 * la que llega sustituye a la anterior. {@code requiredDriverQualificationTypeId} a {@code null} (merge-patch)
 * es que no exige ninguna.
 *
 * <p>Ejemplo, {@code PATCH /vehicle-types/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"seats": 6, "craneReachM": null, "version": 1}
 * }</pre>
 */
public record VehicleTypeUpdateRequest(
        @Size(max = 150) String name,
        @Size(max = 100) String manufacturer,
        @Size(max = 100) String model,
        VehicleCategory category,
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
        UUID requiredDriverQualificationTypeId,
        Boolean active,
        Long version
) {

    /** Retirar pide ademas resources-delete, como el DELETE. */
    public boolean isDeactivating() {
        return Boolean.FALSE.equals(active);
    }
}
