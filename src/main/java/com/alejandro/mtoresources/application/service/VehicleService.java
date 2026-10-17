package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleUpdateRequest;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Las unidades de la flota. Donde esta cada una lo lleva {@link VehicleLocationService}. */
public interface VehicleService {

    VehicleResponse create(VehicleRequest request);

    VehicleResponse update(UUID id, VehicleUpdateRequest request);

    VehicleResponse patch(UUID id, MergePatch<VehicleUpdateRequest> patch);

    VehicleResponse findById(UUID id);

    PageResponse<VehicleResponse> search(String search, UUID typeId, VehicleMobility mobility, VehicleCategory category,
                                         VehicleOperationalStatus status, Boolean available, Boolean active, Pageable pageable);

    /** Lo retira: no se borra nunca, porque su historico de ubicaciones y los turnos lo nombran. */
    void deactivate(UUID id);

    PageResponse<EntityRevisionResponse<VehicleResponse>> findRevisions(UUID id, Pageable pageable);
}
