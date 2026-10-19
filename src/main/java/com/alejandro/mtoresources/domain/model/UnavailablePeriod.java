package com.alejandro.mtoresources.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Un periodo en el que una persona o un vehiculo no esta disponible: una ausencia, una reparacion que lo
 * inmoviliza. Las dos fechas van incluidas; {@code to} nulo no tiene fin conocido (una reparacion abierta
 * sin fecha de salida). Es lo que viaja en el {@code unavailable} de los eventos {@code person.*} y
 * {@code vehicle.*}, sin el motivo. Sin framework.
 */
public record UnavailablePeriod(LocalDate from, LocalDate to) {

    public UnavailablePeriod {
        Objects.requireNonNull(from, "from");
        if (to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("An unavailable period cannot end (" + to + ") before it starts (" + from + ")");
        }
    }

    /** Si aun cuenta hoy o despues: lo que ya termino no viaja. */
    public boolean endsOnOrAfter(LocalDate today) {
        return to == null || !to.isBefore(today);
    }

    /** Si comparte algun dia con otro. */
    public boolean overlaps(UnavailablePeriod other) {
        return (to == null || !to.isBefore(other.from)) && (other.to == null || !other.to.isBefore(from));
    }
}
