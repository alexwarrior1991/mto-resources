package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationRequest;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationResponse;
import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationUpdateRequest;

import java.util.List;
import java.util.UUID;

/**
 * Las cualificaciones de una persona, una por tipo. Toda alta, cambio o baja publica el
 * {@code person.updated} de esa persona con la lista entera.
 */
public interface PersonQualificationService {

    /** Todas, por el codigo de su tipo. */
    List<PersonQualificationResponse> findAll(UUID personId);

    PersonQualificationResponse create(UUID personId, PersonQualificationRequest request);

    PersonQualificationResponse patch(UUID personId, UUID qualificationId, MergePatch<PersonQualificationUpdateRequest> patch);

    /** Borrado fisico: la que se dio por error. Envers guarda el borrado. */
    void delete(UUID personId, UUID qualificationId);
}
