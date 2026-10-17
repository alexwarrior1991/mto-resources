package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, UUID>, JpaSpecificationExecutor<VehicleType> {

    boolean existsByCode(String code);
}
