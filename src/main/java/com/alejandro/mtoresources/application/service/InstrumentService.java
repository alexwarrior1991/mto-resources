package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentRequest;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentResponse;
import com.alejandro.mtoresources.application.dto.instrument.InstrumentUpdateRequest;
import com.alejandro.mtoresources.infrastructure.persistence.entity.InstrumentKind;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Los instrumentos calibrados (pertigas de puesta a tierra, detectores de tension, equipos de medida).
 * Publica {@code instrument.created}, {@code updated} y {@code deactivated}: {@code mto-maintenance}
 * guarda su instantanea para elegirlos en un turno.
 */
public interface InstrumentService {

    InstrumentResponse create(InstrumentRequest request);

    InstrumentResponse update(UUID id, InstrumentUpdateRequest request);

    InstrumentResponse patch(UUID id, MergePatch<InstrumentUpdateRequest> patch);

    InstrumentResponse findById(UUID id);

    /** {@code calibrationExpiresBefore}: los que tienen calibracion y caduca antes de esa fecha. */
    PageResponse<InstrumentResponse> search(String search, InstrumentKind kind, Boolean active, LocalDate calibrationExpiresBefore,
                                            Pageable pageable);

    /** Lo retira: no se borra nunca, porque los turnos lo nombran. */
    void deactivate(UUID id);

    PageResponse<EntityRevisionResponse<InstrumentResponse>> findRevisions(UUID id, Pageable pageable);
}
