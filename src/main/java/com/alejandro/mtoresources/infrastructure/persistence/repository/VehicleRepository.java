package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    boolean existsByCode(String code);

    @Query("select count(v) > 0 from Vehicle v where upper(v.registrationPlate) = upper(:plate) and (:id is null or v.id <> :id)")
    boolean existsRegistrationPlate(@Param("plate") String plate, @Param("id") UUID id);

    @Query("select count(v) > 0 from Vehicle v where upper(v.railVehicleNumber) = upper(:number) and (:id is null or v.id <> :id)")
    boolean existsRailVehicleNumber(@Param("number") String number, @Param("id") UUID id);

    @EntityGraph(attributePaths = "type")
    @Query("select v from Vehicle v where v.id in :ids")
    List<Vehicle> findAllWithType(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = "type")
    @Query("select v from Vehicle v order by v.id")
    Page<Vehicle> findPageWithType(Pageable pageable);

    /** Los vehiculos de un modelo, bloqueados en orden de id: su evento cuenta el modelo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.type.id = :typeId order by v.id")
    List<Vehicle> lockAllOfTypeInIdOrder(@Param("typeId") UUID typeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") UUID id);

    /** Los vehiculos por defecto de equipos activos: un vehiculo asi no se retira sin cambiarlo antes. */
    @Query("select u.code from OrganizationUnit u where u.defaultVehicle.id = :vehicleId and u.active = true order by u.code")
    List<String> findActiveTeamsUsingAsDefault(@Param("vehicleId") UUID vehicleId);

    /** Bloqueados en orden de id, antes de republicar su evento: el orden de las marcas de agua es el de los commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v join fetch v.type where v.id in :ids order by v.id")
    List<Vehicle> lockAllInIdOrder(@Param("ids") Collection<UUID> ids);

    @Query("select v.id from Vehicle v order by v.id")
    Page<UUID> findAllIds(Pageable pageable);
}
