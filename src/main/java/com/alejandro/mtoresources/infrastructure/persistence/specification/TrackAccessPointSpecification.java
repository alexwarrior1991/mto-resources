package com.alejandro.mtoresources.infrastructure.persistence.specification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.TrackAccessPoint;
import org.springframework.data.jpa.domain.Specification;

/** Los filtros de {@code GET /track-access-points}: lo que ofrece el dialogo de ubicacion. Un filtro sin valor no filtra. */
public final class TrackAccessPointSpecification {

    private TrackAccessPointSpecification() {
    }

    public static Specification<TrackAccessPoint> filter(Long trackId, Long executionPackageId, Boolean enabled) {
        return SpecificationUtils.<TrackAccessPoint>equalsLong("trackId", trackId)
                .and(SpecificationUtils.equalsLong("executionPackageId", executionPackageId))
                .and(SpecificationUtils.equalsBoolean("enabled", enabled));
    }
}
