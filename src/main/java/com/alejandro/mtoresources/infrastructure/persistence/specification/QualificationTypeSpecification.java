package com.alejandro.mtoresources.infrastructure.persistence.specification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationType;
import org.springframework.data.jpa.domain.Specification;

/** Los filtros de {@code GET /qualification-types}. */
public final class QualificationTypeSpecification {

    private QualificationTypeSpecification() {
    }

    public static Specification<QualificationType> filter(String search, QualificationKind kind, Boolean active) {
        return Specification.allOf(
                search(search),
                SpecificationUtils.equalsEnum("kind", kind),
                SpecificationUtils.equalsBoolean("active", active)
        );
    }

    private static Specification<QualificationType> search(String text) {
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
}
