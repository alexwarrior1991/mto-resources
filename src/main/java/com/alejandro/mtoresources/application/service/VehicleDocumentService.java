package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleDocumentUpdateRequest;

import java.util.List;
import java.util.UUID;

/**
 * Los documentos con caducidad de un vehiculo, uno de cada tipo. Toda alta, cambio o baja publica el
 * {@code vehicle.updated} del vehiculo con la lista entera ({@code availabilityChanged=false}: un documento
 * no cambia si esta disponible; si con uno caducado puede entrar en un turno lo decide mto-maintenance).
 */
public interface VehicleDocumentService {

    /** Todos, por el nombre de su tipo. */
    List<VehicleDocumentResponse> findAll(UUID vehicleId);

    VehicleDocumentResponse create(UUID vehicleId, VehicleDocumentRequest request);

    VehicleDocumentResponse patch(UUID vehicleId, UUID documentId, MergePatch<VehicleDocumentUpdateRequest> patch);

    /** Borrado fisico: el que se dio por error. Envers guarda el borrado. */
    void delete(UUID vehicleId, UUID documentId);
}
