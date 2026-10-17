package com.alejandro.mtoresources.application.exception;

/**
 * Raised when a unique business code is already assigned to another aggregate.
 */
public class DuplicateCodeException extends BusinessException {

    private final String aggregate;
    private final String code;

    public DuplicateCodeException(String aggregate, String code) {
        super("%s code '%s' is already in use".formatted(aggregate, code));
        this.aggregate = aggregate;
        this.code = code;
    }

    /** Otro valor unico que no es el codigo: la matricula de un vehiculo, el usuario de una persona. */
    public DuplicateCodeException(String aggregate, String field, String value) {
        super("%s %s '%s' is already in use".formatted(aggregate, field, value));
        this.aggregate = aggregate;
        this.code = value;
    }

    public String getAggregate() {
        return aggregate;
    }

    public String getCode() {
        return code;
    }
}