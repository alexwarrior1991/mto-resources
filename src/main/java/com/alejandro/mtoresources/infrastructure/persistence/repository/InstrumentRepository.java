package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.Instrument;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID>, JpaSpecificationExecutor<Instrument> {

    boolean existsByCode(String code);

    /** Bloqueado hasta el final de la transaccion, antes de publicar su evento: el orden de las marcas de agua es el de los commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instrument i where i.id = :id")
    Optional<Instrument> findByIdForUpdate(@Param("id") UUID id);

    /** Bloqueados en orden de id, antes de republicar su evento. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instrument i where i.id in :ids order by i.id")
    List<Instrument> lockAllInIdOrder(@Param("ids") Collection<UUID> ids);

    @Query("select i.id from Instrument i order by i.id")
    Page<UUID> findAllIds(Pageable pageable);

    // ------------------------------------------------------------------ avisos de caducidad

    /** Los activos cuya calibracion caduca hasta {@code horizon} y de los que aun no se aviso por esa fecha. */
    @Query("select i.id as id, i.calibrationExpiresOn as expiresOn from Instrument i "
            + "where i.active = true and i.calibrationExpiresOn is not null and i.calibrationExpiresOn <= :horizon "
            + "and ((i.calibrationExpiresOn < :today and (i.expiredNotifiedFor is null or i.expiredNotifiedFor <> i.calibrationExpiresOn)) "
            + "or (i.calibrationExpiresOn >= :today and (i.expiringNotifiedFor is null or i.expiringNotifiedFor <> i.calibrationExpiresOn))) "
            + "order by i.calibrationExpiresOn, i.id")
    List<ExpiryCandidate> findExpiryCandidates(@Param("today") LocalDate today, @Param("horizon") LocalDate horizon);

    @Modifying
    @Query(value = "update instrument set expiring_notified_for = :expiresOn "
            + "where id = :id and calibration_expires_on = :expiresOn and expiring_notified_for is distinct from :expiresOn", nativeQuery = true)
    int markExpiringNotified(@Param("id") UUID id, @Param("expiresOn") LocalDate expiresOn);

    @Modifying
    @Query(value = "update instrument set expired_notified_for = :expiresOn "
            + "where id = :id and calibration_expires_on = :expiresOn and expired_notified_for is distinct from :expiresOn", nativeQuery = true)
    int markExpiredNotified(@Param("id") UUID id, @Param("expiresOn") LocalDate expiresOn);
}
