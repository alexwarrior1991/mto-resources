package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface QualificationTypeRepository extends JpaRepository<QualificationType, UUID>, JpaSpecificationExecutor<QualificationType> {

    boolean existsByCode(String code);

    Optional<QualificationType> findByCode(String code);
}
