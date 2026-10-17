package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonQualification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
