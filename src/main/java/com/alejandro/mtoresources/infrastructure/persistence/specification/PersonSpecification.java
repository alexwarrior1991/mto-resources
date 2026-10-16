package com.alejandro.mtoresources.infrastructure.persistence.specification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.TeamMember;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/** Los filtros de {@code GET /people}. */
public final class PersonSpecification {

    private PersonSpecification() {
    }

    public static Specification<Person> filter(String search, PersonCategory category, UUID unitId, Boolean active) {
        return Specification.allOf(
                search(search),
                SpecificationUtils.equalsEnum("category", category),
                inUnit(unitId),
                SpecificationUtils.equalsBoolean("active", active)
        );
    }

    /** El texto en el codigo, el nombre, los apellidos, el usuario o el numero de empleado. */
    private static Specification<Person> search(String text) {
        String normalized = SpecificationUtils.normalize(text);
        if (normalized == null) {
            return SpecificationUtils.alwaysTrue();
        }
        String pattern = "%" + normalized.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern),
                cb.like(cb.lower(root.get("username")), pattern),
                cb.like(cb.lower(root.get("employeeNumber")), pattern)
        );
    }

    /** Quien dirige la unidad o es uno de sus linemen ahora. */
    private static Specification<Person> inUnit(UUID unitId) {
        if (unitId == null) {
            return SpecificationUtils.alwaysTrue();
        }
        return (root, query, cb) -> {
            Subquery<UUID> heads = query.subquery(UUID.class);
            Root<OrganizationUnit> unit = heads.from(OrganizationUnit.class);
            heads.select(unit.get("head").get("id")).where(cb.equal(unit.get("id"), unitId));

            Subquery<UUID> members = query.subquery(UUID.class);
            Root<TeamMember> member = members.from(TeamMember.class);
            members.select(member.get("person").get("id"))
                    .where(cb.equal(member.get("team").get("id"), unitId), cb.isNull(member.get("leftOn")));

            return cb.or(root.get("id").in(heads), root.get("id").in(members));
        };
    }
}
