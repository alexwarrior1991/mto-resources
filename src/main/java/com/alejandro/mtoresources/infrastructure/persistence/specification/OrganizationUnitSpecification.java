package com.alejandro.mtoresources.infrastructure.persistence.specification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/** Los filtros de {@code GET /units}. */
public final class OrganizationUnitSpecification {

    private OrganizationUnitSpecification() {
    }

    public static Specification<OrganizationUnit> filter(String search, OrganizationUnitKind kind, WorkDiscipline discipline,
                                                         UUID parentId, Long executionPackageId, UUID headPersonId, Boolean active) {
        return Specification.allOf(
                search(search),
                SpecificationUtils.equalsEnum("kind", kind),
                SpecificationUtils.equalsEnum("discipline", discipline),
                SpecificationUtils.associationIdEquals("parent", parentId),
                executionPackage(executionPackageId),
                SpecificationUtils.associationIdEquals("head", headPersonId),
                SpecificationUtils.equalsBoolean("active", active)
        );
    }

    private static Specification<OrganizationUnit> search(String text) {
        String normalized = SpecificationUtils.normalize(text);
        if (normalized == null) {
            return SpecificationUtils.alwaysTrue();
        }
        String pattern = "%" + normalized.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("name")), pattern)
        );
    }

    private static Specification<OrganizationUnit> executionPackage(Long executionPackageId) {
        if (executionPackageId == null) {
            return SpecificationUtils.alwaysTrue();
        }
        return (root, query, cb) -> cb.isMember(executionPackageId, root.get("executionPackageIds"));
    }
}
