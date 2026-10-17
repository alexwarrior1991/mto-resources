package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.audit.RevisionMetadataResponse;
import com.alejandro.mtoresources.application.dto.audit.RevisionOperation;
import com.alejandro.mtoresources.application.dto.common.PageMetadataResponse;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.service.EntityAuditService;
import com.alejandro.mtoresources.infrastructure.persistence.audit.AuditRevision;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lee el historial con el {@code AuditReader} de Envers.
 *
 * <p>Se usa el {@code AuditReader} directamente y no {@code spring-data-envers}: su
 * {@code RevisionRepository} obligaría a cambiar la fábrica de repositorios de todo el paquete —
 * incluidos {@code InventoryBalanceRepository} e {@code InboxMessageRepository}, cuya corrección
 * depende de sus consultas nativas — a cambio de poco más que lo que hay aquí.</p>
 */
@Service
class EntityAuditServiceImpl implements EntityAuditService {

    private static final Logger log = LoggerFactory.getLogger(EntityAuditServiceImpl.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public <E, R> PageResponse<EntityRevisionResponse<R>> findRevisions(Class<E> entityType,
                                                                       UUID id,
                                                                       Function<E, R> toResponse,
                                                                       Pageable pageable) {
        log.debug("Reading revision history of {} id={}", entityType.getSimpleName(), id);

        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        long totalElements = countRevisions(auditReader, entityType, id);

        // Cero revisiones es un 404: la entidad no existe, o es un activo que solo ha llegado por datos
        // maestros, que se escriben con SQL nativo y no dejan revisión (docs/07-auditing.md). Aquí no
        // hay revisión de partida. Los clientes lo dicen como «sin historial todavía».
        if (totalElements == 0) {
            throw new NotFoundException(aggregateName(entityType), id);
        }

        List<Object[]> rows = revisionsQuery(auditReader, entityType, id)
                .addOrder(AuditEntity.revisionNumber().desc())
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<EntityRevisionResponse<R>> content = rows.stream()
                .map(row -> toRevisionResponse(row, toResponse))
                .toList();

        return new PageResponse<>(content, pageMetadata(pageable, totalElements, content.size()));
    }

    private static long countRevisions(AuditReader auditReader, Class<?> entityType, UUID id) {
        return (long) revisionsQuery(auditReader, entityType, id)
                .addProjection(AuditEntity.revisionNumber().count())
                .getSingleResult();
    }

    /**
     * {@code selectDeletedEntities = true}: sin eso, la revisión que más importa —la que dice que
     * algo se borró— sería justo la que no aparece.
     */
    private static AuditQuery revisionsQuery(AuditReader auditReader, Class<?> entityType, UUID id) {
        return auditReader.createQuery()
                .forRevisionsOfEntity(entityType, false, true)
                .add(AuditEntity.id().eq(id));
    }

    @SuppressWarnings("unchecked")
    private static <E, R> EntityRevisionResponse<R> toRevisionResponse(Object[] row, Function<E, R> toResponse) {
        E entity = (E) row[0];
        AuditRevision revision = (AuditRevision) row[1];
        RevisionType revisionType = (RevisionType) row[2];

        RevisionMetadataResponse metadata = new RevisionMetadataResponse(
                revision.getId(),
                revision.getRevisionInstant(),
                toOperation(revisionType),
                revision.getUsername(),
                revision.getSource() == null ? null : revision.getSource().name(),
                revision.getCorrelationId()
        );

        return new EntityRevisionResponse<>(metadata, entity == null ? null : toResponse.apply(entity));
    }

    private static RevisionOperation toOperation(RevisionType revisionType) {
        return switch (revisionType) {
            case ADD -> RevisionOperation.CREATED;
            case MOD -> RevisionOperation.UPDATED;
            case DEL -> RevisionOperation.DELETED;
        };
    }

    private static PageMetadataResponse pageMetadata(Pageable pageable, long totalElements, int contentSize) {
        int size = pageable.getPageSize();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int number = pageable.getPageNumber();
        return new PageMetadataResponse(
                number,
                size,
                totalElements,
                totalPages,
                number == 0,
                pageable.getOffset() + contentSize >= totalElements
        );
    }

    /**
     * El nombre del agregado como lo dicen los demas 404 ({@code "Organization unit"} para
     * {@code OrganizationUnit}), que es de donde sale el prefijo del codigo de error: un historial
     * vacio de una unidad es {@code UNI-404}, como la unidad que no existe, y no {@code APP-404}.
     */
    static String aggregateName(Class<?> entityType) {
        String[] words = entityType.getSimpleName().split("(?<=[a-z])(?=[A-Z])");
        StringBuilder name = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            name.append(' ').append(words[i].toLowerCase(Locale.ROOT));
        }
        return name.toString();
    }
}
