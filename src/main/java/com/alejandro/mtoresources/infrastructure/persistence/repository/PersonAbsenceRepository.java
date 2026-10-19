package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonAbsence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonAbsenceRepository extends JpaRepository<PersonAbsence, UUID> {

    /** Las de una persona, la mas reciente primero: lo que lista la API. */
    @Query("select a from PersonAbsence a where a.person.id = :personId order by a.startsOn desc, a.id")
    List<PersonAbsence> findAllOfPerson(@Param("personId") UUID personId);

    @Query("select a from PersonAbsence a where a.id = :id and a.person.id = :personId")
    Optional<PersonAbsence> findOfPerson(@Param("personId") UUID personId, @Param("id") UUID id);

    /** Las de esas personas que terminan hoy o despues: las que viajan en su evento. */
    @Query("select a from PersonAbsence a join fetch a.person p where p.id in :personIds and a.endsOn >= :today "
            + "order by p.id, a.startsOn, a.id")
    List<PersonAbsence> findCurrentOfPeople(@Param("personIds") Collection<UUID> personIds, @Param("today") LocalDate today);

    /** Las de una persona que comparten algun dia con {@code [from, to]}: la propia, si se modifica, se descarta fuera. */
    @Query("select a from PersonAbsence a where a.person.id = :personId and a.startsOn <= :to and a.endsOn >= :from order by a.startsOn")
    List<PersonAbsence> findOverlapping(@Param("personId") UUID personId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
