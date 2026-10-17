package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeUpdateRequest;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Los modelos de vehiculo: lo comun a todas sus unidades. */
public interface VehicleTypeService {

    VehicleTypeResponse create(VehicleTypeRequest request);

    VehicleTypeResponse update(UUID id, VehicleTypeUpdateRequest request);

    VehicleTypeResponse patch(UUID id, MergePatch<VehicleTypeUpdateRequest> patch);

    VehicleTypeResponse findById(UUID id);

    PageResponse<VehicleTypeResponse> search(String search, VehicleMobility mobility, VehicleCategory category,
                                             VehicleCapability capability, Boolean active, Pageable pageable);

    /** Lo retira: no se da de alta ningun vehiculo nuevo de ese modelo; los que hay siguen. */
    void deactivate(UUID id);
}
