package com.alejandro.mtoresources.infrastructure.persistence.repository;

import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ParkingSiteRepository extends JpaRepository<ParkingSite, UUID>, JpaSpecificationExecutor<ParkingSite> {

    boolean existsByCode(String code);
}
