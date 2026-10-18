package com.alejandro.mtoresources.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Cuando caduca algo con fecha (una cualificacion, un documento de un vehiculo, la calibracion de un
 * instrumento) y de que hay que avisar. Sin framework.
 *
 * <p>Una fecha de caducidad es el ultimo dia en que vale: caducado es desde el dia siguiente. «Hoy» es
 * el del reloj del servicio, y lo da quien llama.</p>
 */
public final class ExpiryRules {

    /** De que se avisa. */
    public enum Stage {
        /** Faltan los dias de aviso o menos, y aun vale (tambien el mismo dia de la caducidad). */
        EXPIRING,
        /** Ya no vale: desde el dia siguiente al de la caducidad. */
        EXPIRED
    }

    private ExpiryRules() {
    }

    /** Los dias que faltan: 0 el dia que caduca, negativos cuando ya ha caducado. */
    public static int daysLeft(LocalDate expiresOn, LocalDate today) {
        return Math.toIntExact(ChronoUnit.DAYS.between(today, expiresOn));
    }

    /** Sin caducidad, nunca caduca. */
    public static boolean isExpired(LocalDate expiresOn, LocalDate today) {
        return expiresOn != null && expiresOn.isBefore(today);
    }

    /**
     * De que hay que avisar hoy: de que ha caducado, de que caduca dentro de {@code warningDays} dias (o
     * menos), o de nada (sin caducidad, o lejos todavia).
     */
    public static Optional<Stage> stage(LocalDate expiresOn, LocalDate today, int warningDays) {
        if (expiresOn == null) {
            return Optional.empty();
        }
        if (isExpired(expiresOn, today)) {
            return Optional.of(Stage.EXPIRED);
        }
        return daysLeft(expiresOn, today) <= warningDays ? Optional.of(Stage.EXPIRING) : Optional.empty();
    }

    /** La caducidad no es anterior a la expedicion; las dos pueden faltar. */
    public static Optional<String> datesViolation(String what, LocalDate issuedOn, LocalDate expiresOn) {
        if (issuedOn != null && expiresOn != null && expiresOn.isBefore(issuedOn)) {
            return Optional.of(what + " cannot expire (" + expiresOn + ") before it was issued (" + issuedOn + ")");
        }
        return Optional.empty();
    }

    /**
     * La caducidad que se deduce de la vigencia de su tipo cuando no se dice: la expedicion mas esos meses.
     * Sin expedicion o sin vigencia, ninguna (no caduca hasta que alguien la diga).
     */
    public static Optional<LocalDate> defaultExpiry(LocalDate issuedOn, Integer validityMonths) {
        if (issuedOn == null || validityMonths == null) {
            return Optional.empty();
        }
        return Optional.of(issuedOn.plusMonths(validityMonths));
    }
}
