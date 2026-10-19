package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** {@code PLANNED -> OPEN -> CLOSED}, o {@code CANCELLED} desde las dos primeras ({@code RepairRules}). */
public enum RepairStatus {
    PLANNED, OPEN, CLOSED, CANCELLED
}
