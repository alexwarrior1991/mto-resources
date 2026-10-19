package com.alejandro.mtoresources.application.dto.telematics;

import java.util.List;

/**
 * Lo que se hizo con un lote: cuantas se apuntaron y cuantas se ignoraron, y cada una con su motivo. Un
 * lote nunca falla por una posicion: lo que no se puede aplicar se ignora y se dice.
 */
public record TelematicsPositionsResponse(int recorded, int ignored, List<TelematicsPositionResult> results) {
}
