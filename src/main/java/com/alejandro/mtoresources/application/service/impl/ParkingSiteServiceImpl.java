package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteRequest;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteResponse;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteUpdateRequest;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.PageMapper;
import com.alejandro.mtoresources.application.mapper.ParkingSiteMapper;
import com.alejandro.mtoresources.application.service.ParkingSiteService;
import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSite;
import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSiteKind;
import com.alejandro.mtoresources.infrastructure.persistence.repository.ParkingSiteRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.ParkingSiteSpecification;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ParkingSiteServiceImpl implements ParkingSiteService {

    private static final Set<String> CLEARABLE = Set.of("executionPackageId", "stationId", "trackId", "kp", "address", "latitude",
            "longitude", "notes");

    private final ParkingSiteRepository repository;
    private final ParkingSiteMapper mapper;

    @Override
    @Transactional
    public ParkingSiteResponse create(ParkingSiteRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Parking site", code);
        }
        ParkingSite site = ParkingSite.builder()
                .code(code)
                .name(PatchRules.requireText(request.name(), "name"))
                .kind(request.kind())
                .executionPackageId(request.executionPackageId())
                .stationId(request.stationId())
                .trackId(request.trackId())
                .kp(request.kp())
                .railAccess(request.railAccess())
                .roadAccess(request.roadAccess())
                .address(SpecificationUtils.normalize(request.address()))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .notes(SpecificationUtils.normalize(request.notes()))
                .build();
        requireConsistent(site);
        return mapper.toResponse(repository.save(site));
    }

    @Override
    @Transactional
    public ParkingSiteResponse update(UUID id, ParkingSiteUpdateRequest request) {
        return patch(id, MergePatch.of(request));
    }

    @Override
    @Transactional
    public ParkingSiteResponse patch(UUID id, MergePatch<ParkingSiteUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        ParkingSiteUpdateRequest values = patch.values();
        ParkingSite site = site(id);
        StaleVersionException.check("Parking site " + site.getCode(), site.getVersion(), values.version());

        if (values.name() != null) {
            site.setName(PatchRules.requireText(values.name(), "name"));
        }
        PatchRules.set(patch, "kind", values.kind(), site::setKind);
        PatchRules.set(patch, "executionPackageId", values.executionPackageId(), site::setExecutionPackageId);
        PatchRules.set(patch, "stationId", values.stationId(), site::setStationId);
        PatchRules.set(patch, "trackId", values.trackId(), site::setTrackId);
        PatchRules.set(patch, "kp", values.kp(), site::setKp);
        PatchRules.set(patch, "railAccess", values.railAccess(), site::setRailAccess);
        PatchRules.set(patch, "roadAccess", values.roadAccess(), site::setRoadAccess);
        PatchRules.set(patch, "address", SpecificationUtils.normalize(values.address()), site::setAddress);
        PatchRules.set(patch, "latitude", values.latitude(), site::setLatitude);
        PatchRules.set(patch, "longitude", values.longitude(), site::setLongitude);
        PatchRules.set(patch, "notes", SpecificationUtils.normalize(values.notes()), site::setNotes);
        PatchRules.set(patch, "active", values.active(), site::setActive);
        requireConsistent(site);
        return mapper.toResponse(repository.saveAndFlush(site));
    }

    @Override
    @Transactional(readOnly = true)
    public ParkingSiteResponse findById(UUID id) {
        return mapper.toResponse(site(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ParkingSiteResponse> search(String search, ParkingSiteKind kind, Long executionPackageId, Long trackId,
                                                    Boolean railAccess, Boolean roadAccess, Boolean active, Pageable pageable) {
        return PageMapper.toPageResponse(repository.findAll(
                ParkingSiteSpecification.filter(search, kind, executionPackageId, trackId, railAccess, roadAccess, active), pageable),
                mapper::toResponse);
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        site(id).setActive(false);
    }

    private ParkingSite site(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Parking site", id));
    }

    /** Un sitio se alcanza por via, por carretera o por las dos; uno con via dice cual; unas coordenadas van enteras. */
    private static void requireConsistent(ParkingSite site) {
        if (!Boolean.TRUE.equals(site.getRailAccess()) && !Boolean.TRUE.equals(site.getRoadAccess())) {
            throw new ValidationException("A parking site is reached by rail, by road or both (railAccess, roadAccess)");
        }
        if (Boolean.TRUE.equals(site.getRailAccess()) && site.getTrackId() == null) {
            throw new ValidationException("A parking site with rail access says its track (trackId)");
        }
        if ((site.getLatitude() == null) != (site.getLongitude() == null)) {
            throw new ValidationException("Coordinates travel together: latitude and longitude");
        }
    }
}
