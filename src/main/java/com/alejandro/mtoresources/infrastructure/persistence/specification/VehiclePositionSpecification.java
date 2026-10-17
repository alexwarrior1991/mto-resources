package com.alejandro.mtoresources.infrastructure.persistence.specification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePosition;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Los filtros del tablero de la flota ({@code GET /vehicles/positions}). Miran donde esta cada vehiculo
 * de verdad: uno {@code ATTACHED} cumple el filtro si lo cumple su portador. Solo vehiculos activos.
 */
public final class VehiclePositionSpecification {

    private VehiclePositionSpecification() {
    }

    public static Specification<VehiclePosition> filter(Long executionPackageId, Long trackId, VehicleLocationKind kind) {
        Specification<VehiclePosition> active = (root, query, cb) -> {
            Subquery<UUID> vehicles = query.subquery(UUID.class);
            Root<Vehicle> vehicle = vehicles.from(Vehicle.class);
            vehicles.select(vehicle.get("id")).where(cb.isTrue(vehicle.get("active")));
            return root.get("vehicleId").in(vehicles);
        };
        if (executionPackageId == null && trackId == null && kind == null) {
            return active;
        }
        Specification<VehiclePosition> where = (root, query, cb) -> {
            Subquery<UUID> carriers = query.subquery(UUID.class);
            Root<VehiclePosition> carrier = carriers.from(VehiclePosition.class);
            carriers.select(carrier.get("vehicleId")).where(matches(carrier, cb, executionPackageId, trackId, kind));
            Predicate throughCarrier = cb.and(
                    cb.equal(root.get("kind"), VehicleLocationKind.ATTACHED),
                    root.get("carrierVehicleId").in(carriers));
            return cb.or(matches(root, cb, executionPackageId, trackId, kind), throughCarrier);
        };
        return active.and(where);
    }

    private static Predicate matches(Root<VehiclePosition> position, CriteriaBuilder cb, Long executionPackageId, Long trackId,
                                     VehicleLocationKind kind) {
        List<Predicate> predicates = new ArrayList<>();
        if (executionPackageId != null) {
            predicates.add(cb.equal(position.get("executionPackageId"), executionPackageId));
        }
        if (trackId != null) {
            predicates.add(cb.equal(position.get("trackId"), trackId));
        }
        if (kind != null) {
            predicates.add(cb.equal(position.get("kind"), kind));
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }
}
