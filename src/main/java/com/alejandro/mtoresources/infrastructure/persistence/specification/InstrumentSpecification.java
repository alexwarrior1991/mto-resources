package com.alejandro.mtoresources.infrastructure.persistence.specification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.Instrument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.InstrumentKind;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Los filtros de {@code GET /instruments}. */
public final class InstrumentSpecification {

    private InstrumentSpecification() {
    }

    public static Specification<Instrument> filter(String search, InstrumentKind kind, Boolean active, LocalDate calibrationExpiresBefore) {
        return Specification.allOf(
                search(search),
                SpecificationUtils.equalsEnum("kind", kind),
                SpecificationUtils.equalsBoolean("active", active),
                calibrationExpiresBefore(calibrationExpiresBefore)
        );
    }

    private static Specification<Instrument> search(String text) {
        String normalized = SpecificationUtils.normalize(text);
        if (normalized == null) {
            return SpecificationUtils.alwaysTrue();
        }
        String pattern = "%" + normalized.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("serialNumber")), pattern)
        );
    }

    /** Los que tienen calibracion y caduca antes de esa fecha (sin incluirla); los que no tienen, nunca. */
    private static Specification<Instrument> calibrationExpiresBefore(LocalDate date) {
        if (date == null) {
            return SpecificationUtils.alwaysTrue();
        }
        return (root, query, cb) -> cb.lessThan(root.get("calibrationExpiresOn"), date);
    }
}
