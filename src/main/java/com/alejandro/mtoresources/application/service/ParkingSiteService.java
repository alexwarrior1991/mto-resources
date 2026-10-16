package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteRequest;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteResponse;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteUpdateRequest;
import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSiteKind;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** El catalogo opcional de sitios donde se sabe que se puede aparcar. */
public interface ParkingSiteService {

    ParkingSiteResponse create(ParkingSiteRequest request);

    ParkingSiteResponse update(UUID id, ParkingSiteUpdateRequest request);

    ParkingSiteResponse patch(UUID id, MergePatch<ParkingSiteUpdateRequest> patch);

    ParkingSiteResponse findById(UUID id);

    PageResponse<ParkingSiteResponse> search(String search, ParkingSiteKind kind, Long executionPackageId, Long trackId,
                                             Boolean railAccess, Boolean roadAccess, Boolean active, Pageable pageable);

    /** Lo retira del catalogo: las ubicaciones registradas alli lo siguen nombrando. */
    void deactivate(UUID id);
}
