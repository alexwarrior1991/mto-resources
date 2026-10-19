package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationResponse;
import com.alejandro.mtoresources.application.dto.telematics.TelematicsOutcome;
import com.alejandro.mtoresources.application.dto.telematics.TelematicsPosition;
import com.alejandro.mtoresources.application.dto.telematics.TelematicsPositionResult;
import com.alejandro.mtoresources.application.dto.telematics.TelematicsPositionsRequest;
import com.alejandro.mtoresources.application.dto.telematics.TelematicsPositionsResponse;
import com.alejandro.mtoresources.application.exception.BusinessException;
import com.alejandro.mtoresources.application.service.TelematicsService;
import com.alejandro.mtoresources.application.service.VehicleLocationService;
import com.alejandro.mtoresources.configuration.telematics.TelematicsProperties;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationSource;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePosition;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleLocationEventRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehiclePositionRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Cada posicion en su propia transaccion, con la fila de su vehiculo bloqueada: dos lotes del mismo
 * dispositivo a la vez se ponen en fila, y el freno ({@code app.resources.telematics.min-interval}) se mira
 * con lo que el otro ya apunto. Las posiciones del lote se aplican por {@code observedAt}.
 *
 * <p>Se ignora, y se dice por que, lo que el GPS no puede decidir:</p>
 * <ul>
 *   <li>un dispositivo que no es de ningun vehiculo, o el de uno retirado;</li>
 *   <li>un vehiculo de solo via: nunca esta en la carretera, y un GPS no dice en que via ni en que kp;</li>
 *   <li>uno remolcado o transportado ({@code ATTACHED}): esta donde su portador;</li>
 *   <li>uno que esta en la via por un turno ({@code ON_TRACK} con origen {@code SHIFT} o {@code FIELD}):
 *       hasta que el turno se cierre, manda el turno;</li>
 *   <li>una posicion a menos de {@code minInterval} de la ultima apuntada de ese dispositivo, o anterior a
 *       ella;</li>
 *   <li>lo que {@code VehicleLocationService} rechace (una hora en el futuro...).</li>
 * </ul>
 */
@Service
class TelematicsServiceImpl implements TelematicsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelematicsServiceImpl.class);

    /** Lo que sigue en la via mientras su turno no se cierre. */
    private static final Set<VehicleLocationSource> SHIFT_SOURCES = Set.of(VehicleLocationSource.SHIFT, VehicleLocationSource.FIELD);

    private final VehicleRepository vehicleRepository;
    private final VehiclePositionRepository positionRepository;
    private final VehicleLocationEventRepository eventRepository;
    private final VehicleLocationService locationService;
    private final TelematicsProperties properties;
    private final TransactionTemplate transactions;

    TelematicsServiceImpl(VehicleRepository vehicleRepository, VehiclePositionRepository positionRepository,
                          VehicleLocationEventRepository eventRepository, VehicleLocationService locationService,
                          TelematicsProperties properties, PlatformTransactionManager transactionManager) {
        this.vehicleRepository = vehicleRepository;
        this.positionRepository = positionRepository;
        this.eventRepository = eventRepository;
        this.locationService = locationService;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public TelematicsPositionsResponse ingest(TelematicsPositionsRequest request) {
        List<TelematicsPositionResult> results = new ArrayList<>();
        request.positions().stream()
                .sorted(Comparator.comparing(TelematicsPosition::observedAt).thenComparing(TelematicsPosition::deviceId))
                .forEach(position -> results.add(transactions.execute(status -> apply(position))));
        int recorded = (int) results.stream().filter(result -> result.outcome() == TelematicsOutcome.RECORDED).count();
        return new TelematicsPositionsResponse(recorded, results.size() - recorded, results);
    }

    private TelematicsPositionResult apply(TelematicsPosition position) {
        String deviceId = position.deviceId().trim();
        Optional<Vehicle> found = vehicleRepository.findByTelematicsDeviceForUpdate(deviceId);
        if (found.isEmpty()) {
            return ignored(position, null, "Device " + deviceId + " is the GPS of no vehicle");
        }
        Vehicle vehicle = found.get();
        String code = vehicle.getCode();
        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            return ignored(position, code, "Vehicle " + code + " is retired");
        }
        if (vehicle.getType().getMobility() == VehicleMobility.RAIL_ONLY) {
            return ignored(position, code, "Vehicle " + code + " is rail-only: it is never on the road");
        }
        Optional<VehiclePosition> current = positionRepository.findById(vehicle.getId());
        if (current.isPresent() && current.get().getKind() == VehicleLocationKind.ATTACHED) {
            return ignored(position, code, "Vehicle " + code + " is attached to another one: it is where its carrier is");
        }
        if (current.isPresent() && current.get().getKind() == VehicleLocationKind.ON_TRACK && SHIFT_SOURCES.contains(current.get().getSource())) {
            return ignored(position, code, "Vehicle " + code + " is on the track in a shift (" + current.get().getSourceReference()
                    + "): the shift says where it is until it closes");
        }
        String reference = "telematics:" + deviceId;
        Optional<Instant> last = eventRepository.findLastObservedAt(vehicle.getId(), reference, VehicleLocationSource.TELEMATICS);
        if (last.isPresent() && position.observedAt().isBefore(last.get().plus(properties.minInterval()))) {
            return ignored(position, code, "Less than " + properties.minInterval().toMinutes() + " min after the last position of device "
                    + deviceId + " (" + last.get() + ")");
        }
        VehicleLocationRequest location = new VehicleLocationRequest(VehicleLocationKind.ON_ROAD, null, null, null, null,
                position.latitude(), position.longitude(), null, null, null, null, null, position.observedAt());
        try {
            VehicleLocationResponse recorded = locationService.record(vehicle.getId(), location, VehicleLocationSource.TELEMATICS, reference);
            if (recorded == null) {
                return ignored(position, code, "Already recorded");
            }
            return new TelematicsPositionResult(deviceId, position.observedAt(), code, TelematicsOutcome.RECORDED, null);
        } catch (BusinessException rejected) {
            return ignored(position, code, rejected.getMessage());
        }
    }

    private static TelematicsPositionResult ignored(TelematicsPosition position, String vehicleCode, String reason) {
        LOGGER.debug("GPS position of device {} at {} ignored: {}", position.deviceId(), position.observedAt(), reason);
        return new TelematicsPositionResult(position.deviceId(), position.observedAt(), vehicleCode, TelematicsOutcome.IGNORED, reason);
    }
}
