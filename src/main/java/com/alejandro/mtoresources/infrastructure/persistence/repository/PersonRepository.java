package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
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

public interface PersonRepository extends JpaRepository<Person, UUID>, JpaSpecificationExecutor<Person> {

    /** El siguiente numero de {@code PER-000001}: de la secuencia, nunca {@code MAX + 1}. */
    @Query(value = "select nextval('person_code_seq')", nativeQuery = true)
    long nextCodeValue();

    @Query("select p from Person p where lower(p.username) = lower(:username)")
    Optional<Person> findByUsernameIgnoreCase(@Param("username") String username);

    Optional<Person> findByKeycloakUserId(String keycloakUserId);

    @Query("select count(p) > 0 from Person p where lower(p.username) = lower(:username) and (:id is null or p.id <> :id)")
    boolean existsUsername(@Param("username") String username, @Param("id") UUID id);

    @Query("select count(p) > 0 from Person p where p.employeeNumber = :employeeNumber and (:id is null or p.id <> :id)")
    boolean existsEmployeeNumber(@Param("employeeNumber") String employeeNumber, @Param("id") UUID id);

    @Query("select count(p) > 0 from Person p where p.keycloakUserId = :keycloakUserId and (:id is null or p.id <> :id)")
    boolean existsKeycloakUserId(@Param("keycloakUserId") String keycloakUserId, @Param("id") UUID id);

    /**
     * La persona con su fila bloqueada hasta el final de la transaccion: lo que serializa los cambios de
     * sus cualificaciones, que cuentan todas en su evento, para que el ultimo {@code person.updated} sea
     * el del ultimo commit y no deje fuera la de otra transaccion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Person p where p.id = :id")
    Optional<Person> findByIdForUpdate(@Param("id") UUID id);

    /** Bloqueadas en orden de id, antes de republicar su evento: el orden de las marcas de agua es el de los commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Person p where p.id in :ids order by p.id")
    List<Person> lockAllInIdOrder(@Param("ids") Collection<UUID> ids);

    @Query("select p.id from Person p order by p.id")
    Page<UUID> findAllIds(Pageable pageable);
}
