package com.alejandro.mtoresources.infrastructure.persistence.specification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSite;
import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSiteKind;
import org.springframework.data.jpa.domain.Specification;

/** Los filtros de {@code GET /parking-sites}. */
public final class ParkingSiteSpecification {

    private ParkingSiteSpecification() {
    }

    public static Specification<ParkingSite> filter(String search, ParkingSiteKind kind, Long executionPackageId, Long trackId,
                                                    Boolean railAccess, Boolean roadAccess, Boolean active) {
        return Specification.allOf(
                search(search),
                SpecificationUtils.equalsEnum("kind", kind),
                SpecificationUtils.equalsLong("executionPackageId", executionPackageId),
                SpecificationUtils.equalsLong("trackId", trackId),
                SpecificationUtils.equalsBoolean("railAccess", railAccess),
                SpecificationUtils.equalsBoolean("roadAccess", roadAccess),
                SpecificationUtils.equalsBoolean("active", active)
        );
    }

    private static Specification<ParkingSite> search(String text) {
        String normalized = SpecificationUtils.normalize(text);
        if (normalized == null) {
            return SpecificationUtils.alwaysTrue();
        }
        String pattern = "%" + normalized.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("address")), pattern)
        );
    }
}
