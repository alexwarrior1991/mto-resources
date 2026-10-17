package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface VehicleLocationEventRepository extends JpaRepository<VehicleLocationEvent, UUID> {

    @Query("""
            select e from VehicleLocationEvent e
             where e.vehicle.id = :vehicleId
               and (cast(:from as timestamp) is null or e.observedAt >= :from)
               and (cast(:to as timestamp) is null or e.observedAt < :to)
             order by e.observedAt desc, e.recordedAt desc, e.id
            """)
    Page<VehicleLocationEvent> findHistory(@Param("vehicleId") UUID vehicleId, @Param("from") Instant from, @Param("to") Instant to,
                                           Pageable pageable);

    /** Si ya se aplico un evento de turno a un vehiculo: una reentrega o una republicacion no lo repite. */
    boolean existsByVehicleIdAndSourceReferenceAndObservedAt(UUID vehicleId, String sourceReference, Instant observedAt);
}
