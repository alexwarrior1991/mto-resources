package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VehiclePositionRepository extends JpaRepository<VehiclePosition, UUID>, JpaSpecificationExecutor<VehiclePosition> {

    /**
     * Mueve la posicion actual al evento dado, si es posterior a la que hay. La marca de agua va dentro de
     * la sentencia, nunca leyendo y escribiendo despues: dos eventos del mismo vehiculo a la vez, o uno que
     * llega tarde, no pueden dejar la posicion en el mas viejo. 0 filas: el evento queda en el historico,
     * pero el vehiculo ya estaba en otra parte mas tarde.
     */
    @Modifying
    @Query(value = """
            insert into vehicle_position as p
                (vehicle_id, location_event_id, kind, parking_site_id, execution_package_id, track_id, kp,
                 carrier_vehicle_id, source, source_reference, observed_at, updated_at)
            select e.vehicle_id, e.id, e.kind, e.parking_site_id, e.execution_package_id, e.track_id, e.kp,
                   e.carrier_vehicle_id, e.source, e.source_reference, e.observed_at, now()
              from vehicle_location_event e
             where e.id = :eventId
            on conflict (vehicle_id) do update
               set location_event_id = excluded.location_event_id,
                   kind = excluded.kind,
                   parking_site_id = excluded.parking_site_id,
                   execution_package_id = excluded.execution_package_id,
                   track_id = excluded.track_id,
                   kp = excluded.kp,
                   carrier_vehicle_id = excluded.carrier_vehicle_id,
                   source = excluded.source,
                   source_reference = excluded.source_reference,
                   observed_at = excluded.observed_at,
                   updated_at = now()
             where p.observed_at < excluded.observed_at
            """, nativeQuery = true)
    int moveTo(@Param("eventId") UUID eventId);

    @Query("select p from VehiclePosition p join fetch p.locationEvent where p.vehicleId in :vehicleIds")
    List<VehiclePosition> findAllByVehicleIds(@Param("vehicleIds") Collection<UUID> vehicleIds);

    /** Los que estan enganchados a uno: siguen a su portador. */
    List<VehiclePosition> findByCarrierVehicleId(UUID carrierVehicleId);
}
