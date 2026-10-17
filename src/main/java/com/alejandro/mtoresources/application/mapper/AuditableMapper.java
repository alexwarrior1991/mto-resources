package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.AuditableEntity;
import org.mapstruct.Mapper;

/**
 * Maps audit metadata shared by persisted resources into response DTO fragments.
 */
@Mapper(config = MapStructCentralConfig.class)
public interface AuditableMapper {

    AuditMetadataResponse toAuditMetadata(AuditableEntity entity);
}