package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.operation.RepublishResult;

/**
 * Vuelve a publicar el estado de ahora de personas, equipos y vehiculos, con un {@code operationId}
 * nuevo cada uno: es como arranca un consumidor nuevo ({@code mto-maintenance} ahora,
 * {@code mto-construction} manana) o uno que perdio su cola.
 */
public interface RepublishService {

    /**
     * Todo, o solo lo de un EP: sus equipos, las personas que salen en ellos (jefe, linemen,
     * supervisor y jefe de supervisores) y sus vehiculos por defecto. Corre en lotes, cada uno en su
     * transaccion; no se llama dentro de otra.
     */
    RepublishResult republish(Long executionPackageId);
}
