package com.alejandro.mtoresources.infrastructure.persistence.specification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

/** Los filtros de {@code GET /vehicles}. */
public final class VehicleSpecification {

    private VehicleSpecification() {
    }

    public static Specification<Vehicle> filter(String search, UUID typeId, VehicleMobility mobility, VehicleCategory category,
                                                VehicleOperationalStatus status, Boolean available, Boolean active) {
        return Specification.allOf(
                search(search),
                SpecificationUtils.associationIdEquals("type", typeId),
                SpecificationUtils.associationAttributeEquals("type", "mobility", mobility),
                SpecificationUtils.associationAttributeEquals("type", "category", category),
                SpecificationUtils.equalsEnum("operationalStatus", status),
                available(available),
                SpecificationUtils.equalsBoolean("active", active)
        );
    }

    /** El texto en el codigo, el nombre, la matricula o el numero ferroviario. */
    private static Specification<Vehicle> search(String text) {
        String normalized = SpecificationUtils.normalize(text);
        if (normalized == null) {
            return SpecificationUtils.alwaysTrue();
        }
        String pattern = "%" + normalized.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("registrationPlate")), pattern),
                cb.like(cb.lower(root.get("railVehicleNumber")), pattern)
        );
    }

    /** Disponible: activo y operativo, aunque sea con limitaciones (lo mismo que {@link Vehicle#isAvailable()}). */
    private static Specification<Vehicle> available(Boolean available) {
        if (available == null) {
            return SpecificationUtils.alwaysTrue();
        }
        List<VehicleOperationalStatus> working = java.util.Arrays.stream(VehicleOperationalStatus.values())
                .filter(VehicleOperationalStatus::canWork).toList();
        return (root, query, cb) -> {
            var yes = cb.and(cb.isTrue(root.get("active")), root.get("operationalStatus").in(working));
            return available ? yes : cb.not(yes);
        };
    }
}
