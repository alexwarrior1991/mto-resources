package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentResponse;
import com.alejandro.mtoresources.domain.model.ExpiryRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocument;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;

/** {@code expired} depende de hoy, que lo da el reloj del servicio: llega como contexto. */
@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class, imports = ExpiryRules.class)
public interface VehicleDocumentMapper {

    @Mapping(target = "audit", source = "document")
    @Mapping(target = "vehicleId", source = "document.vehicle.id")
    @Mapping(target = "expired", expression = "java(ExpiryRules.isExpired(document.getExpiresOn(), today))")
    VehicleDocumentResponse toResponse(VehicleDocument document, @Context LocalDate today);
}
