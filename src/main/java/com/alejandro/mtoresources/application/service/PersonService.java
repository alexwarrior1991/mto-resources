package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.person.MeResponse;
import com.alejandro.mtoresources.application.dto.person.PersonRequest;
import com.alejandro.mtoresources.application.dto.person.PersonResponse;
import com.alejandro.mtoresources.application.dto.person.PersonUpdateRequest;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Las personas del dominio: su ficha, tengan o no usuario en Keycloak. */
public interface PersonService {

    PersonResponse create(PersonRequest request);

    PersonResponse update(UUID id, PersonUpdateRequest request);

    PersonResponse patch(UUID id, MergePatch<PersonUpdateRequest> patch);

    PersonResponse findById(UUID id);

    PageResponse<PersonResponse> search(String search, PersonCategory category, UUID unitId, Boolean active, Pageable pageable);

    /** Desactiva: no se borra nunca, porque los turnos y los equipos la nombran. */
    void deactivate(UUID id);

    /** La persona del token (por su {@code preferred_username}) y su sitio en el organigrama. */
    MeResponse me();

    PageResponse<EntityRevisionResponse<PersonResponse>> findRevisions(UUID id, Pageable pageable);
}
