package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.Instrument;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
