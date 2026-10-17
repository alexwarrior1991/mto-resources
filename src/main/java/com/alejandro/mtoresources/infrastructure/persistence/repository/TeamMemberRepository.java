package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    /** Los linemen que estan ahora en el equipo, por su fecha de entrada. */
    @Query("select m from TeamMember m join fetch m.person where m.team.id = :teamId and m.leftOn is null order by m.joinedOn, m.id")
    List<TeamMember> findOpenByTeam(@Param("teamId") UUID teamId);

    @Query("select m from TeamMember m join fetch m.person where m.team.id = :teamId order by m.joinedOn desc, m.id")
    List<TeamMember> findAllByTeam(@Param("teamId") UUID teamId);

    @Query("select m from TeamMember m where m.person.id = :personId and m.leftOn is null")
    Optional<TeamMember> findOpenByPerson(@Param("personId") UUID personId);

    @Query("select m from TeamMember m join fetch m.team where m.person.id in :personIds and m.leftOn is null")
    List<TeamMember> findOpenByPersons(@Param("personIds") Collection<UUID> personIds);

    /** Cuantos linemen tiene ahora cada equipo que tiene alguno: para el arbol, en una consulta. */
    @Query("select m.team.id as teamId, count(m) as linemen from TeamMember m where m.leftOn is null group by m.team.id")
    List<TeamSize> countOpenPerTeam();

    interface TeamSize {
        UUID getTeamId();

        long getLinemen();
    }

    @Query("select count(m) from TeamMember m where m.team.id = :teamId and m.leftOn is null")
    int countOpenByTeam(@Param("teamId") UUID teamId);
}
