package com.alejandro.mtoresources.infrastructure.persistence.specification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleType;
import org.springframework.data.jpa.domain.Specification;

/** Los filtros de {@code GET /vehicle-types}. */
public final class VehicleTypeSpecification {

    private VehicleTypeSpecification() {
    }

    public static Specification<VehicleType> filter(String search, VehicleMobility mobility, VehicleCategory category,
                                                    VehicleCapability capability, Boolean active) {
        return Specification.allOf(
                search(search),
                SpecificationUtils.equalsEnum("mobility", mobility),
                SpecificationUtils.equalsEnum("category", category),
                capability(capability),
                SpecificationUtils.equalsBoolean("active", active)
        );
    }

    private static Specification<VehicleType> search(String text) {
        String normalized = SpecificationUtils.normalize(text);
        if (normalized == null) {
            return SpecificationUtils.alwaysTrue();
        }
        String pattern = "%" + normalized.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("manufacturer")), pattern),
                cb.like(cb.lower(root.get("model")), pattern)
        );
    }

    private static Specification<VehicleType> capability(VehicleCapability capability) {
        if (capability == null) {
            return SpecificationUtils.alwaysTrue();
        }
        return (root, query, cb) -> cb.isMember(capability, root.get("capabilities"));
    }
}
