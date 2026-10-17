package com.alejandro.mtoresources.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

/**
 * Una persona del dominio, tenga o no usuario en Keycloak: un operario sin login tiene su ficha igual.
 * Su categoria dice lo que es; a quien dirige lo dice el organigrama.
 */
@Entity
@Audited
@Table(name = "person")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Person extends VersionedEntity {

    @NotBlank
    @Size(max = 16)
    @Column(name = "code", nullable = false, updatable = false, length = 16)
    @ToString.Include
    private String code;

    @Size(max = 40)
    @Column(name = "employee_number", length = 40)
    private String employeeNumber;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank
    @Size(max = 150)
    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName;

    @Size(max = 100)
    @Column(name = "username", length = 100)
    @ToString.Include
    private String username;

    @Size(max = 100)
    @Column(name = "keycloak_user_id", length = 100)
    private String keycloakUserId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "category", nullable = false, columnDefinition = "person_category")
    @ToString.Include
    private PersonCategory category;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "employer", nullable = false, columnDefinition = "person_employer")
    private PersonEmployer employer = PersonEmployer.OWN;

    @Size(max = 150)
    @Column(name = "company", length = 150)
    private String company;

    @Size(max = 40)
    @Column(name = "phone", length = 40)
    private String phone;

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
