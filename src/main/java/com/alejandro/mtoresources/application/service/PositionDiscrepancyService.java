package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.report.PositionDiscrepancyReport;

/** El informe de discrepancias entre el perfil de puesto de Keycloak y la categoria de cada persona. */
public interface PositionDiscrepancyService {

    PositionDiscrepancyReport report();
}
