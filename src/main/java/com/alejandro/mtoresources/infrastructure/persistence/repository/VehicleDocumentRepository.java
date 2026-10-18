package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocumentKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    // ------------------------------------------------------------------ avisos de caducidad

    /** Los de vehiculos activos que caducan hasta {@code horizon} y de los que aun no se aviso por esa fecha. */
    @Query("select d.id as id, d.expiresOn as expiresOn from VehicleDocument d join d.vehicle v "
            + "where v.active = true and d.expiresOn <= :horizon "
            + "and ((d.expiresOn < :today and (d.expiredNotifiedFor is null or d.expiredNotifiedFor <> d.expiresOn)) "
            + "or (d.expiresOn >= :today and (d.expiringNotifiedFor is null or d.expiringNotifiedFor <> d.expiresOn))) "
            + "order by d.expiresOn, d.id")
    List<ExpiryCandidate> findExpiryCandidates(@Param("today") LocalDate today, @Param("horizon") LocalDate horizon);

    @Modifying
    @Query(value = "update vehicle_document set expiring_notified_for = :expiresOn "
            + "where id = :id and expires_on = :expiresOn and expiring_notified_for is distinct from :expiresOn", nativeQuery = true)
    int markExpiringNotified(@Param("id") UUID id, @Param("expiresOn") LocalDate expiresOn);

    @Modifying
    @Query(value = "update vehicle_document set expired_notified_for = :expiresOn "
            + "where id = :id and expires_on = :expiresOn and expired_notified_for is distinct from :expiresOn", nativeQuery = true)
    int markExpiredNotified(@Param("id") UUID id, @Param("expiresOn") LocalDate expiresOn);

    @Query("select d from VehicleDocument d join fetch d.vehicle where d.id = :id")
    Optional<VehicleDocument> findWithVehicle(@Param("id") UUID id);
}
