package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.TrackAccessPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/**
 * La instantanea de los puntos de acceso a la via. Se lee con JPA y solo se escribe aqui, con SQL nativo y
 * la marca de agua dentro de la sentencia: nunca leer y luego escribir, que dejaria pasar a la vez dos
 * entregas o una tardia.
 */
public interface TrackAccessPointRepository extends JpaRepository<TrackAccessPoint, Long>, JpaSpecificationExecutor<TrackAccessPoint> {

    /**
     * Lo que dice el evento, si no es anterior a lo guardado: la secuencia del outbox de
     * {@code mto-configuration} es global y creciente, asi que una menor describe un estado anterior. Sin
     * secuencia se aplica y conserva la guardada; una igual (una reentrega) se aplica otra vez, que no
     * cambia nada. Un {@code DELETED} llega aqui con {@code enabled=false}: tambien avanza la marca, y si el
     * acceso no se conocia queda dado de alta desactivado, para que un alta tardia no lo resucite.
     *
     * @return 1 si escribio, 0 si el evento era anterior a lo guardado
     */
    @Modifying
    @Query(value = """
            insert into track_access_point (
                id, name, track_id, execution_package_id, station_id, kp, kind, side, road_rail_access,
                max_axle_load_t, max_gross_weight_t, latitude, longitude, enabled, source_sequence_number, created_at, updated_at
            ) values (
                :id, :name, :trackId, :executionPackageId, :stationId, :kp, :kind, :side, :roadRailAccess,
                :maxAxleLoadT, :maxGrossWeightT, :latitude, :longitude, :enabled, :sequenceNumber, now(), now()
            ) on conflict (id) do update
               set name = excluded.name,
                   track_id = excluded.track_id,
                   execution_package_id = excluded.execution_package_id,
                   station_id = excluded.station_id,
                   kp = excluded.kp,
                   kind = excluded.kind,
                   side = excluded.side,
                   road_rail_access = excluded.road_rail_access,
                   max_axle_load_t = excluded.max_axle_load_t,
                   max_gross_weight_t = excluded.max_gross_weight_t,
                   latitude = excluded.latitude,
                   longitude = excluded.longitude,
                   enabled = excluded.enabled,
                   source_sequence_number = coalesce(excluded.source_sequence_number, track_access_point.source_sequence_number),
                   updated_at = now()
             where track_access_point.source_sequence_number is null
                or excluded.source_sequence_number is null
                or excluded.source_sequence_number >= track_access_point.source_sequence_number
            """, nativeQuery = true)
    int upsertFromMasterData(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("trackId") Long trackId,
            @Param("executionPackageId") Long executionPackageId,
            @Param("stationId") Long stationId,
            @Param("kp") BigDecimal kp,
            @Param("kind") String kind,
            @Param("side") String side,
            @Param("roadRailAccess") boolean roadRailAccess,
            @Param("maxAxleLoadT") BigDecimal maxAxleLoadT,
            @Param("maxGrossWeightT") BigDecimal maxGrossWeightT,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude,
            @Param("enabled") boolean enabled,
            @Param("sequenceNumber") Long sequenceNumber
    );

    /**
     * Un {@code DELETED} cuyos {@code values} no bastan para el upsert: solo desactiva lo que ya se
     * conocia, y avanza la marca aunque ya estuviera desactivado.
     *
     * @return 1 si escribio, 0 si no se conocia o el evento era anterior a lo guardado
     */
    @Modifying
    @Query(value = """
            update track_access_point
               set enabled = false,
                   source_sequence_number = coalesce(cast(:sequenceNumber as bigint), source_sequence_number),
                   updated_at = now()
             where id = :id
               and (source_sequence_number is null
                    or cast(:sequenceNumber as bigint) is null
                    or cast(:sequenceNumber as bigint) >= source_sequence_number)
            """, nativeQuery = true)
    int deactivateFromMasterData(@Param("id") Long id, @Param("sequenceNumber") Long sequenceNumber);
}
