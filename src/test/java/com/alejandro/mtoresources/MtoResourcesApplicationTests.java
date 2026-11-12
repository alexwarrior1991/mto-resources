package com.alejandro.mtoresources;

import com.alejandro.mtoresources.application.service.BudgetedOperations;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.ExpiryWatchService;
import com.alejandro.mtoresources.application.service.IncomingEventHandler;
import com.alejandro.mtoresources.application.service.InstrumentService;
import com.alejandro.mtoresources.application.service.KeycloakDirectory;
import com.alejandro.mtoresources.application.service.OrganizationUnitService;
import com.alejandro.mtoresources.application.service.ParkingSiteService;
import com.alejandro.mtoresources.application.service.PersonAbsenceService;
import com.alejandro.mtoresources.application.service.PersonQualificationService;
import com.alejandro.mtoresources.application.service.PersonService;
import com.alejandro.mtoresources.application.service.PositionDiscrepancyService;
import com.alejandro.mtoresources.application.service.QualificationTypeService;
import com.alejandro.mtoresources.application.service.RepublishService;
import com.alejandro.mtoresources.application.service.TelematicsService;
import com.alejandro.mtoresources.application.service.TrackAccessPointService;
import com.alejandro.mtoresources.application.service.VehicleDocumentService;
import com.alejandro.mtoresources.application.service.VehicleLocationService;
import com.alejandro.mtoresources.application.service.VehicleRepairService;
import com.alejandro.mtoresources.application.service.VehicleService;
import com.alejandro.mtoresources.application.service.VehicleTypeService;
import com.alejandro.mtoresources.configuration.async.AsyncConfiguration;
import com.alejandro.mtoresources.configuration.scheduling.SchedulingConfiguration;
import com.alejandro.mtoresources.infrastructure.web.actuator.RepublishEndpoint;
import com.alejandro.mtoresources.support.PostgreSQLTestContainer;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
        assertNotNull(context.getBean(SchedulingConfiguration.class), "The pool of the @Scheduled jobs");
        for (Class<?> type : List.of(PersonService.class, OrganizationUnitService.class, ParkingSiteService.class, VehicleTypeService.class,
                VehicleService.class, VehicleLocationService.class, RepublishService.class, BudgetedOperations.class,
                DomainEventPublisher.class, RepublishEndpoint.class, QualificationTypeService.class, PersonQualificationService.class,
                VehicleDocumentService.class, InstrumentService.class, ExpiryWatchService.class, TrackAccessPointService.class,
                PersonAbsenceService.class, VehicleRepairService.class, TelematicsService.class, PositionDiscrepancyService.class,
                KeycloakDirectory.class)) {
            assertNotNull(context.getBean(type), type.getSimpleName());
        }
        assertTrue(handlers.stream().anyMatch(handler -> handler.sourceService().equals("mto-maintenance")
                && handler.entityName().equals("shift")));
        assertTrue(handlers.stream().anyMatch(handler -> handler.sourceService().equals("mto-configuration")
                && handler.entityName().equals("track-access-point")));
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

    /**
     * Una pasada {@code @Scheduled} bloqueada (el relay del outbox esperando al broker) no para a las demas:
     * mientras espera, el latido de las operaciones de fondo sigue corriendo, con hilos virtuales y sin
     * ellos. Con el planificador de Boot las dos comparten un hilo (el unico de planificacion del
     * {@code SimpleAsyncTaskScheduler} para los {@code fixedDelay} con hilos virtuales, el unico del pool sin
     * ellos): el latido se paraba y otra replica daba por abandonadas las operaciones en marcha.
     */
    @Test
    void aBlockedScheduledPassDoesNotStopTheOthersWithVirtualThreadsAndWithout() throws Exception {
        StandardEnvironment defaults = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))
                .forEach(defaults.getPropertySources()::addLast);
        Integer poolSize = defaults.getProperty("spring.task.scheduling.pool.size", Integer.class);
        assertEquals(4, poolSize, "The default of SPRING_TASK_SCHEDULING_POOL_SIZE");

        for (boolean virtual : List.of(true, false)) {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                    .withUserConfiguration(SchedulingConfiguration.class, ABlockedPassAndABeat.class)
                    .withPropertyValues("spring.threads.virtual.enabled=" + virtual, "spring.task.scheduling.pool.size=" + poolSize)
                    .run(started -> {
                        ABlockedPassAndABeat passes = started.getBean(ABlockedPassAndABeat.class);
                        try {
                            assertTrue(passes.blocked.await(10, TimeUnit.SECONDS), "The blocked pass started");
                            assertTrue(passes.beats.await(10, TimeUnit.SECONDS),
                                    "The beat keeps running while the other pass is blocked, with spring.threads.virtual.enabled=" + virtual);
                            assertEquals(Set.of(virtual), passes.onVirtualThreads, "Virtual threads only with the flag");
                        } finally {
                            passes.release.countDown();
                        }
                    });
        }
    }

    /**
     * Sin {@code @Configuration} a proposito: una clase anidada con ella sustituiria la configuracion de este
     * {@code @SpringBootTest}. {@code @EnableScheduling} basta para que el runner la procese.
     */
    @EnableScheduling
    static class ABlockedPassAndABeat {

        final CountDownLatch blocked = new CountDownLatch(1);
        final CountDownLatch beats = new CountDownLatch(3);
        final CountDownLatch release = new CountDownLatch(1);
        final Set<Boolean> onVirtualThreads = ConcurrentHashMap.newKeySet();

        /** El relay que espera a un broker que no contesta. */
        @Scheduled(fixedDelay = 60_000)
        void blockedPass() throws InterruptedException {
            onVirtualThreads.add(Thread.currentThread().isVirtual());
            blocked.countDown();
            release.await(30, TimeUnit.SECONDS);
        }

        /** El latido: empieza despues de la bloqueada y tiene que repetirse mientras ella espera. */
        @Scheduled(initialDelay = 200, fixedDelay = 50)
        void beat() {
            onVirtualThreads.add(Thread.currentThread().isVirtual());
            beats.countDown();
        }
    }
}
