package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocumentKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, UUID> {

    /**
     * Por el nombre de su tipo, como viajan en el evento del vehiculo (en SQL, un enumerado de PostgreSQL se
     * ordena por su declaracion, no por su nombre).
     */
    Comparator<VehicleDocument> BY_KIND = Comparator.comparing((VehicleDocument document) -> document.getKind().name());

    @Query("select d from VehicleDocument d where d.vehicle.id = :vehicleId")
    List<VehicleDocument> findAllByVehicleId(@Param("vehicleId") UUID vehicleId);

    /** Los de varios vehiculos a la vez, para republicarlos por lotes o tras cambiar su modelo. */
    @Query("select d from VehicleDocument d join fetch d.vehicle v where v.id in :vehicleIds")
    List<VehicleDocument> findAllByVehicleIds(@Param("vehicleIds") Collection<UUID> vehicleIds);

    @Query("select d from VehicleDocument d where d.id = :id and d.vehicle.id = :vehicleId")
    Optional<VehicleDocument> findOfVehicle(@Param("vehicleId") UUID vehicleId, @Param("id") UUID id);

    boolean existsByVehicleIdAndKind(UUID vehicleId, VehicleDocumentKind kind);

    /** Los de un vehiculo, por el nombre de su tipo: lo que lista la API y lo que viaja en su evento. */
    default List<VehicleDocument> findAllOfVehicle(UUID vehicleId) {
        return findAllByVehicleId(vehicleId).stream().sorted(BY_KIND).toList();
    }
}
