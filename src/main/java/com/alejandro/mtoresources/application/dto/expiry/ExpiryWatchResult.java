package com.alejandro.mtoresources.application.dto.expiry;

import java.time.LocalDate;

/**
 * Lo que aviso una pasada del vigilante de caducidades: cuantos avisos de cada familia (que caduca o que ha
 * caducado), y de que dia.
 *
 * <p>Ejemplo, la pasada de un dia:</p>
 * <pre>{@code
 * {"date": "2026-10-17", "warningDays": 30, "qualifications": 3, "vehicleDocuments": 1, "calibrations": 2}
 * }</pre>
 */
public record ExpiryWatchResult(LocalDate date, int warningDays, int qualifications, int vehicleDocuments, int calibrations) {
}
