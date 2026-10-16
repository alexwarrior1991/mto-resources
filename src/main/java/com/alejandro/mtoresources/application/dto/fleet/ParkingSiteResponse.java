package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSiteKind;

import java.math.BigDecimal;
import java.util.UUID;

/** Un sitio conocido donde se puede aparcar. */
public record ParkingSiteResponse(
        UUID id,
        String code,
        String name,
        ParkingSiteKind kind,
        Long executionPackageId,
        Long stationId,
        Long trackId,
        BigDecimal kp,
        Boolean railAccess,
        Boolean roadAccess,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String notes,
        Boolean active,
        Long version,
        AuditMetadataResponse audit
) {
}
