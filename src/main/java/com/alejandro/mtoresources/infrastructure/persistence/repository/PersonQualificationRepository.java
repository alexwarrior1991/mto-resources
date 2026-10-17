package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonQualification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonQualificationRepository extends JpaRepository<PersonQualification, UUID> {

    /** Las de una persona con su tipo, por el codigo del tipo: lo que lista la API y lo que viaja en su evento. */
    @Query("select q from PersonQualification q join fetch q.qualificationType t where q.person.id = :personId order by t.code, q.id")
    List<PersonQualification> findAllOfPerson(@Param("personId") UUID personId);

    /** Las de varias personas a la vez, para republicarlas por lotes. */
    @Query("select q from PersonQualification q join fetch q.qualificationType t join fetch q.person p "
            + "where p.id in :personIds order by p.id, t.code, q.id")
    List<PersonQualification> findAllOfPeople(@Param("personIds") Collection<UUID> personIds);

    @Query("select q from PersonQualification q join fetch q.qualificationType where q.id = :id and q.person.id = :personId")
    Optional<PersonQualification> findOfPerson(@Param("personId") UUID personId, @Param("id") UUID id);

    boolean existsByPersonIdAndQualificationTypeId(UUID personId, UUID qualificationTypeId);

    /** Quien tiene un tipo: su evento cuenta el tipo de cada una. */
    @Query("select distinct q.person.id from PersonQualification q where q.qualificationType.id = :typeId")
    List<UUID> findHolderIds(@Param("typeId") UUID typeId);

    // ------------------------------------------------------------------ avisos de caducidad

    /**
     * Las de personas activas que caducan hasta {@code horizon} y de las que aun no se aviso por esa fecha:
     * de que caducan, las que aun valen; de que caducaron, las que ya no. Solo se lee: la marca la decide
     * la sentencia que la escribe.
     */
    @Query("select q.id as id, q.expiresOn as expiresOn from PersonQualification q join q.person p "
            + "where p.active = true and q.expiresOn is not null and q.expiresOn <= :horizon "
            + "and ((q.expiresOn < :today and (q.expiredNotifiedFor is null or q.expiredNotifiedFor <> q.expiresOn)) "
            + "or (q.expiresOn >= :today and (q.expiringNotifiedFor is null or q.expiringNotifiedFor <> q.expiresOn))) "
            + "order by q.expiresOn, q.id")
    List<ExpiryCandidate> findExpiryCandidates(@Param("today") LocalDate today, @Param("horizon") LocalDate horizon);

    /** Marca que se aviso de que caduca en esa fecha: 1 si la marca era otra (hay que avisar), 0 si no. */
    @Modifying
    @Query(value = "update person_qualification set expiring_notified_for = :expiresOn "
            + "where id = :id and expires_on = :expiresOn and expiring_notified_for is distinct from :expiresOn", nativeQuery = true)
    int markExpiringNotified(@Param("id") UUID id, @Param("expiresOn") LocalDate expiresOn);

    /** Marca que se aviso de que caduco en esa fecha: 1 si la marca era otra (hay que avisar), 0 si no. */
    @Modifying
    @Query(value = "update person_qualification set expired_notified_for = :expiresOn "
            + "where id = :id and expires_on = :expiresOn and expired_notified_for is distinct from :expiresOn", nativeQuery = true)
    int markExpiredNotified(@Param("id") UUID id, @Param("expiresOn") LocalDate expiresOn);

    /** Con su persona y su tipo, para contar el aviso. */
    @Query("select q from PersonQualification q join fetch q.person join fetch q.qualificationType where q.id = :id")
    Optional<PersonQualification> findWithPersonAndType(@Param("id") UUID id);
}
