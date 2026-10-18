package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.fleet.TrackAccessPointResponse;
import com.alejandro.mtoresources.application.mapper.TrackAccessPointMapper;
import com.alejandro.mtoresources.application.service.TrackAccessPointService;
import com.alejandro.mtoresources.infrastructure.persistence.repository.TrackAccessPointRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.TrackAccessPointSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
class TrackAccessPointServiceImpl implements TrackAccessPointService {

    /** El nombre del agregado en sus 404 ({@code TAP-404}). */
    static final String AGGREGATE = "Track access point";

    /** Por via y kp, como estan en el terreno; uno sin kp al final de su via, y el id para desempatar. */
    private static final Sort ORDER = Sort.by(Sort.Order.asc("trackId"), Sort.Order.asc("kp").nullsLast(), Sort.Order.asc("id"));

    private final TrackAccessPointRepository repository;
    private final TrackAccessPointMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<TrackAccessPointResponse> list(Long trackId, Long executionPackageId, Boolean enabled) {
        return repository.findAll(TrackAccessPointSpecification.filter(trackId, executionPackageId, enabled), ORDER).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
