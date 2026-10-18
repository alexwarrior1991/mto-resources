package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeRequest;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeResponse;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeUpdateRequest;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** El catalogo de tipos de cualificacion: permisos de conducir, habilitaciones, seguridad. */
public interface QualificationTypeService {

    QualificationTypeResponse create(QualificationTypeRequest request);

    QualificationTypeResponse update(UUID id, QualificationTypeUpdateRequest request);

    /** Cambiar el tipo o el permiso de uno republica a quien lo tiene: su evento los cuenta. */
    QualificationTypeResponse patch(UUID id, MergePatch<QualificationTypeUpdateRequest> patch);

    QualificationTypeResponse findById(UUID id);

    PageResponse<QualificationTypeResponse> search(String search, QualificationKind kind, Boolean active, Pageable pageable);

    /** Lo retira: las que ya tiene cada persona se quedan, pero no se dan nuevas. */
    void deactivate(UUID id);
}
