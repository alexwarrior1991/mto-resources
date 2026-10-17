package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
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

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, UUID>, JpaSpecificationExecutor<OrganizationUnit> {

    boolean existsByCode(String code);

    Optional<OrganizationUnit> findByCode(String code);

    /**
     * La unidad con su fila bloqueada hasta el final de la transaccion. Es lo que serializa los cambios de
     * los linemen de un equipo (el tope de 6 y el minimo de 2 se cuentan con ella bloqueada) y lo que
     * hace que el orden del outbox sea el de los commits: toda transaccion que publica {@code team.updated}
     * bloquea antes los equipos que cuenta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from OrganizationUnit u where u.id = :id")
    Optional<OrganizationUnit> findByIdForUpdate(@Param("id") UUID id);

    /** Bloquea varias, siempre en el orden de su id: dos transacciones no se esperan la una a la otra. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from OrganizationUnit u where u.id in :ids order by u.id")
    List<OrganizationUnit> lockAllInIdOrder(@Param("ids") Collection<UUID> ids);

    List<OrganizationUnit> findByParentIdAndActiveTrue(UUID parentId);

    boolean existsByParentIdAndActiveTrue(UUID parentId);

    List<OrganizationUnit> findByHeadIdAndActiveTrue(UUID headPersonId);

    List<OrganizationUnit> findByKindAndActiveTrue(OrganizationUnitKind kind);

    /** Todas, para el arbol: el organigrama es pequeno (decenas, no miles). */
    @EntityGraph(attributePaths = {"head", "executionPackageIds"})
    @Query("select u from OrganizationUnit u order by u.code")
    List<OrganizationUnit> findAllForTree();

    /** Lo justo para recorrer el arbol hacia abajo: id, padre y nivel de cada unidad. */
    @Query("select u.id as id, u.parent.id as parentId, u.kind as kind, u.active as active from OrganizationUnit u")
    List<UnitLink> findAllLinks();

    /** Una arista del organigrama. */
    interface UnitLink {
        UUID getId();

        UUID getParentId();

        OrganizationUnitKind getKind();

        Boolean getActive();
    }

    // El tipo va como parametro y no como literal: un enumerado de PostgreSQL (NAMED_ENUM) escrito en
    // JPQL se traduce a un cast a un tipo que no existe ("organizationunitkind").
    @Query("select u.id from OrganizationUnit u where u.kind = :kind order by u.id")
    Page<UUID> findIdsByKind(@Param("kind") OrganizationUnitKind kind, Pageable pageable);

    @Query("select distinct u.id from OrganizationUnit u join u.executionPackageIds ep where u.kind = :kind and ep = :executionPackageId")
    List<UUID> findIdsByKindAndExecutionPackage(@Param("kind") OrganizationUnitKind kind,
                                                @Param("executionPackageId") Long executionPackageId);

    default Page<UUID> findTeamIds(Pageable pageable) {
        return findIdsByKind(OrganizationUnitKind.TEAM, pageable);
    }

    default List<UUID> findTeamIdsOfExecutionPackage(Long executionPackageId) {
        return findIdsByKindAndExecutionPackage(OrganizationUnitKind.TEAM, executionPackageId);
    }
}
