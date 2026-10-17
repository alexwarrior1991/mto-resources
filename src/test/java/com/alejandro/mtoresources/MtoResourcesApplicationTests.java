package com.alejandro.mtoresources;

import com.alejandro.mtoresources.application.service.BudgetedOperations;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.IncomingEventHandler;
import com.alejandro.mtoresources.application.service.OrganizationUnitService;
import com.alejandro.mtoresources.application.service.ParkingSiteService;
import com.alejandro.mtoresources.application.service.PersonQualificationService;
import com.alejandro.mtoresources.application.service.PersonService;
import com.alejandro.mtoresources.application.service.QualificationTypeService;
import com.alejandro.mtoresources.application.service.RepublishService;
import com.alejandro.mtoresources.application.service.VehicleLocationService;
import com.alejandro.mtoresources.application.service.VehicleService;
import com.alejandro.mtoresources.application.service.VehicleTypeService;
import com.alejandro.mtoresources.configuration.async.AsyncConfiguration;
import com.alejandro.mtoresources.infrastructure.web.actuator.RepublishEndpoint;
import com.alejandro.mtoresources.support.PostgreSQLTestContainer;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El unico arranque entero con Tomcat de verdad: el contexto contra un PostgreSQL real sin sustituir
 * ningun servicio, y la prueba de que las peticiones y lo que corre de fondo van en hilos virtuales.
 * Corre con el perfil por defecto ({@code dev}), asi que apaga a mano lo que necesita un broker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.rabbitmq.enabled=false",
        "app.operations.scheduling-enabled=false"
})
class MtoResourcesApplicationTests extends PostgreSQLTestContainer {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerPostgreSQLProperties(registry);
    }

    @TestConfiguration
    static class Probes {

        static final AtomicReference<Boolean> REQUEST_ON_VIRTUAL_THREAD = new AtomicReference<>();

        @Bean
        Filter virtualThreadProbe() {
            return (request, response, chain) -> {
                REQUEST_ON_VIRTUAL_THREAD.set(Thread.currentThread().isVirtual());
                chain.doFilter(request, response);
            };
        }
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private List<IncomingEventHandler> handlers;

    @Autowired
    @Qualifier(AsyncConfiguration.TASK_EXECUTOR)
    private AsyncTaskExecutor executor;

    @LocalServerPort
    private int port;

    @Test
    void everyBusinessServiceIsABean() {
        for (Class<?> type : List.of(PersonService.class, OrganizationUnitService.class, ParkingSiteService.class, VehicleTypeService.class,
                VehicleService.class, VehicleLocationService.class, RepublishService.class, BudgetedOperations.class,
                DomainEventPublisher.class, RepublishEndpoint.class, QualificationTypeService.class, PersonQualificationService.class)) {
            assertNotNull(context.getBean(type), type.getSimpleName());
        }
        assertTrue(handlers.stream().anyMatch(handler -> handler.sourceService().equals("mto-maintenance")
                && handler.entityName().equals("shift")));
    }

    @Test
    void requestsAndBackgroundWorkRunOnVirtualThreads() throws Exception {
        HttpResponse<String> response = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build()
                .send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health")).build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(Boolean.TRUE, Probes.REQUEST_ON_VIRTUAL_THREAD.get(), "Tomcat on virtual threads");

        CompletableFuture<Boolean> background = new CompletableFuture<>();
        executor.execute(() -> background.complete(Thread.currentThread().isVirtual()));
        assertTrue(background.get(5, TimeUnit.SECONDS), "The background executor on virtual threads");
    }
}
