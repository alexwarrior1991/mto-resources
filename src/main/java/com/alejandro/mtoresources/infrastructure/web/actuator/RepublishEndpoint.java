package com.alejandro.mtoresources.infrastructure.web.actuator;

import com.alejandro.mtoresources.application.dto.operation.OperationResponse;
import com.alejandro.mtoresources.application.dto.operation.RepublishResult;
import com.alejandro.mtoresources.application.service.BudgetedOperations;
import com.alejandro.mtoresources.application.service.RepublishService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * {@code POST /actuator/republish} ({@code ops-write}): vuelve a publicar personas, equipos y vehiculos,
 * todo o solo lo de un EP ({@code ?executionPackageId=}). Es como arranca un consumidor nuevo. Siempre
 * en segundo plano: responde 202 con la operacion, y {@code GET /actuator/republish/{id}}
 * ({@code ops-metrics}) dice como va. Dos republicaciones no corren a la vez: comparten {@code scope}.
 */
@Component
@Endpoint(id = "republish")
@RequiredArgsConstructor
public class RepublishEndpoint {

    static final String KIND = "republish";

    private final BudgetedOperations operations;
    private final RepublishService republishService;

    @WriteOperation
    public WebEndpointResponse<OperationResponse> republish(@Nullable Long executionPackageId) {
        BudgetedOperations.Outcome<RepublishResult> outcome = operations.run(KIND, KIND, 200, Duration.ZERO,
                () -> republishService.republish(executionPackageId));
        return new WebEndpointResponse<>(outcome.operation(), 202);
    }

    @ReadOperation
    public OperationResponse operation(@Selector String id) {
        return operations.find(UUID.fromString(id));
    }
}
