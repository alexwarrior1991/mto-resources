package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceRequest;
import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceResponse;
import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceUpdateRequest;
import com.alejandro.mtoresources.application.dto.common.MergePatch;

import java.util.List;
import java.util.UUID;

/**
 * Las ausencias de una persona, que no se solapan. Toda alta, cambio o baja publica el
 * {@code person.updated} de esa persona con su {@code unavailable} entero (los periodos, nunca la clase).
 */
public interface PersonAbsenceService {

    /** Todas, la mas reciente primero. */
    List<PersonAbsenceResponse> findAll(UUID personId);

    PersonAbsenceResponse create(UUID personId, PersonAbsenceRequest request);

    PersonAbsenceResponse patch(UUID personId, UUID absenceId, MergePatch<PersonAbsenceUpdateRequest> patch);

    /** Borrado fisico: la que se dio por error o se anulo. Envers guarda el borrado. */
    void delete(UUID personId, UUID absenceId);
}
