package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** De donde sale una ubicacion: a mano, de un turno de mto-maintenance, del dispositivo de campo de mto-field (mas adelante) o de un localizador (mas adelante). */
public enum VehicleLocationSource {
    MANUAL, SHIFT, FIELD, TELEMATICS
}
