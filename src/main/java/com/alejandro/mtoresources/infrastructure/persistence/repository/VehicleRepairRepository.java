package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.RepairStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleRepair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los estados van como parametro, nunca como literal en la JPQL: un literal de enumerado contra una
 * columna {@code NAMED_ENUM} se convierte a un tipo que no existe.
 */
public interface VehicleRepairRepository extends JpaRepository<VehicleRepair, UUID> {

    @Query(value = "select nextval('vehicle_repair_code_seq')", nativeQuery = true)
    long nextCodeValue();

    /** Las de un vehiculo, la mas reciente primero. */
    @Query("select r from VehicleRepair r where r.vehicle.id = :vehicleId order by r.code desc")
    List<VehicleRepair> findAllOfVehicle(@Param("vehicleId") UUID vehicleId);

    @Query("select r from VehicleRepair r where r.id = :id and r.vehicle.id = :vehicleId")
    Optional<VehicleRepair> findOfVehicle(@Param("vehicleId") UUID vehicleId, @Param("id") UUID id);

    /** Las que inmovilizan en esos estados de esos vehiculos: las que viajan en su evento. */
    @Query("select r from VehicleRepair r join fetch r.vehicle v where v.id in :vehicleIds and r.immobilises = true "
            + "and r.status in :statuses order by v.id, r.code")
    List<VehicleRepair> findImmobilising(@Param("vehicleIds") Collection<UUID> vehicleIds,
                                         @Param("statuses") Collection<RepairStatus> statuses);

    /** Los codigos de las que inmovilizan en ese estado, salvo una: si queda alguna abierta, el vehiculo sigue en el taller. */
    @Query("select r.code from VehicleRepair r where r.vehicle.id = :vehicleId and r.immobilises = true and r.status = :status "
            + "and r.id <> :exceptId order by r.code")
    List<String> findImmobilisingCodes(@Param("vehicleId") UUID vehicleId, @Param("status") RepairStatus status,
                                       @Param("exceptId") UUID exceptId);
}
