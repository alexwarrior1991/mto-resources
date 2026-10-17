package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.instrument.InstrumentResponse;
import com.alejandro.mtoresources.domain.model.ExpiryRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Instrument;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;

/** {@code calibrationExpired} depende de hoy, que lo da el reloj del servicio: llega como contexto. */
@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class, imports = ExpiryRules.class)
public interface InstrumentMapper {

    @Mapping(target = "audit", source = "instrument")
    @Mapping(target = "calibrationExpired", expression = "java(ExpiryRules.isExpired(instrument.getCalibrationExpiresOn(), today))")
    InstrumentResponse toResponse(Instrument instrument, @Context LocalDate today);
}
