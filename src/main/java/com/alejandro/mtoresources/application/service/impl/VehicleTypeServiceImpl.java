package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeUpdateRequest;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.QualificationRuleException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.ValidationException;
import com.alejandro.mtoresources.application.mapper.PageMapper;
import com.alejandro.mtoresources.application.mapper.VehicleTypeMapper;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.application.service.VehicleTypeService;
import com.alejandro.mtoresources.domain.model.FleetRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocument;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleType;
import com.alejandro.mtoresources.infrastructure.persistence.repository.QualificationTypeRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleDocumentRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleTypeRepository;
import com.alejandro.mtoresources.infrastructure.persistence.specification.SpecificationUtils;
import com.alejandro.mtoresources.infrastructure.persistence.specification.VehicleTypeSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class VehicleTypeServiceImpl implements VehicleTypeService {

    /** Todo lo opcional se vacia; lo que define el modelo (nombre, categoria, movilidad) no. */
    private static final Set<String> CLEARABLE = Set.of("manufacturer", "model", "lengthM", "widthM", "heightM", "tareT", "maxGrossT",
            "maxAxleLoadT", "axles", "seats", "payloadT", "power", "trackGaugeMm", "maxSpeedTrackWorkingKmh", "maxSpeedTrackTransportKmh",
            "maxGradientPermille", "minCurveRadiusM", "maxWorkingCantMm", "railGearType", "onTrackingMinutes", "needsPreparedAccess",
            "maxSpeedRoadKmh", "requiredLicence", "capabilities", "craneCapacityT", "craneReachM", "platformHeightM", "platformLoadKg",
            "platformInsulationKv", "towingCapacityT", "notes", "requiredDriverQualificationTypeId");

    private final VehicleTypeRepository repository;
    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentRepository documentRepository;
    private final QualificationTypeRepository qualificationTypeRepository;
    private final VehicleTypeMapper mapper;
    private final DomainEventPublisher events;

    @Override
    @Transactional
    public VehicleTypeResponse create(VehicleTypeRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Vehicle type", code);
        }
        VehicleType type = VehicleType.builder()
                .code(code)
                .name(PatchRules.requireText(request.name(), "name"))
                .manufacturer(SpecificationUtils.normalize(request.manufacturer()))
                .model(SpecificationUtils.normalize(request.model()))
                .mobility(request.mobility())
                .category(request.category())
                .selfPropelled(request.selfPropelled() == null || request.selfPropelled())
                .portable(Boolean.TRUE.equals(request.portable()))
                .lengthM(request.lengthM())
                .widthM(request.widthM())
                .heightM(request.heightM())
                .tareT(request.tareT())
                .maxGrossT(request.maxGrossT())
                .maxAxleLoadT(request.maxAxleLoadT())
                .axles(request.axles())
                .seats(request.seats())
                .payloadT(request.payloadT())
                .power(request.power())
                .trackGaugeMm(request.trackGaugeMm())
                .maxSpeedTrackWorkingKmh(request.maxSpeedTrackWorkingKmh())
                .maxSpeedTrackTransportKmh(request.maxSpeedTrackTransportKmh())
                .maxGradientPermille(request.maxGradientPermille())
                .minCurveRadiusM(request.minCurveRadiusM())
                .maxWorkingCantMm(request.maxWorkingCantMm())
                .transportModeAuthorised(Boolean.TRUE.equals(request.transportModeAuthorised()))
                .railGearType(request.railGearType())
                .onTrackingMinutes(request.onTrackingMinutes())
                .needsPreparedAccess(request.needsPreparedAccess())
                .maxSpeedRoadKmh(request.maxSpeedRoadKmh())
                .requiredLicence(request.requiredLicence())
                .capabilities(capabilities(request.capabilities()))
                .craneCapacityT(request.craneCapacityT())
                .craneReachM(request.craneReachM())
                .platformHeightM(request.platformHeightM())
                .platformLoadKg(request.platformLoadKg())
                .platformInsulationKv(request.platformInsulationKv())
                .towingCapacityT(request.towingCapacityT())
                .notes(SpecificationUtils.normalize(request.notes()))
                .build();
        type.setRequiredDriverQualification(request.requiredDriverQualificationTypeId() != null
                ? activeQualification(request.requiredDriverQualificationTypeId())
                : defaultDriverQualification(type));
        requireConsistent(type);
        return mapper.toResponse(repository.save(type));
    }

    @Override
    @Transactional
    public VehicleTypeResponse update(UUID id, VehicleTypeUpdateRequest request) {
        return patch(id, MergePatch.of(request));
    }

    @Override
    @Transactional
    public VehicleTypeResponse patch(UUID id, MergePatch<VehicleTypeUpdateRequest> patch) {
        PatchRules.requireClearable(patch, CLEARABLE);
        VehicleTypeUpdateRequest values = patch.values();
        VehicleType type = type(id);
        StaleVersionException.check("Vehicle type " + type.getCode(), type.getVersion(), values.version());

        // Sus vehiculos cuentan su modelo en su evento: se bloquean antes de cambiarlo, en orden de id.
        List<Vehicle> vehicles = vehicleRepository.lockAllOfTypeInIdOrder(id);

        if (values.name() != null) {
            type.setName(PatchRules.requireText(values.name(), "name"));
        }
        PatchRules.set(patch, "manufacturer", SpecificationUtils.normalize(values.manufacturer()), type::setManufacturer);
        PatchRules.set(patch, "model", SpecificationUtils.normalize(values.model()), type::setModel);
        PatchRules.set(patch, "category", values.category(), type::setCategory);
        PatchRules.set(patch, "selfPropelled", values.selfPropelled(), type::setSelfPropelled);
        PatchRules.set(patch, "portable", values.portable(), type::setPortable);
        PatchRules.set(patch, "lengthM", values.lengthM(), type::setLengthM);
        PatchRules.set(patch, "widthM", values.widthM(), type::setWidthM);
        PatchRules.set(patch, "heightM", values.heightM(), type::setHeightM);
        PatchRules.set(patch, "tareT", values.tareT(), type::setTareT);
        PatchRules.set(patch, "maxGrossT", values.maxGrossT(), type::setMaxGrossT);
        PatchRules.set(patch, "maxAxleLoadT", values.maxAxleLoadT(), type::setMaxAxleLoadT);
        PatchRules.set(patch, "axles", values.axles(), type::setAxles);
        PatchRules.set(patch, "seats", values.seats(), type::setSeats);
        PatchRules.set(patch, "payloadT", values.payloadT(), type::setPayloadT);
        PatchRules.set(patch, "power", values.power(), type::setPower);
        PatchRules.set(patch, "trackGaugeMm", values.trackGaugeMm(), type::setTrackGaugeMm);
        PatchRules.set(patch, "maxSpeedTrackWorkingKmh", values.maxSpeedTrackWorkingKmh(), type::setMaxSpeedTrackWorkingKmh);
        PatchRules.set(patch, "maxSpeedTrackTransportKmh", values.maxSpeedTrackTransportKmh(), type::setMaxSpeedTrackTransportKmh);
        PatchRules.set(patch, "maxGradientPermille", values.maxGradientPermille(), type::setMaxGradientPermille);
        PatchRules.set(patch, "minCurveRadiusM", values.minCurveRadiusM(), type::setMinCurveRadiusM);
        PatchRules.set(patch, "maxWorkingCantMm", values.maxWorkingCantMm(), type::setMaxWorkingCantMm);
        PatchRules.set(patch, "transportModeAuthorised", values.transportModeAuthorised(), type::setTransportModeAuthorised);
        PatchRules.set(patch, "railGearType", values.railGearType(), type::setRailGearType);
        PatchRules.set(patch, "onTrackingMinutes", values.onTrackingMinutes(), type::setOnTrackingMinutes);
        PatchRules.set(patch, "needsPreparedAccess", values.needsPreparedAccess(), type::setNeedsPreparedAccess);
        PatchRules.set(patch, "maxSpeedRoadKmh", values.maxSpeedRoadKmh(), type::setMaxSpeedRoadKmh);
        PatchRules.set(patch, "requiredLicence", values.requiredLicence(), type::setRequiredLicence);
        if (values.capabilities() != null || patch.clears("capabilities")) {
            type.getCapabilities().clear();
            type.getCapabilities().addAll(capabilities(values.capabilities()));
        }
        PatchRules.set(patch, "craneCapacityT", values.craneCapacityT(), type::setCraneCapacityT);
        PatchRules.set(patch, "craneReachM", values.craneReachM(), type::setCraneReachM);
        PatchRules.set(patch, "platformHeightM", values.platformHeightM(), type::setPlatformHeightM);
        PatchRules.set(patch, "platformLoadKg", values.platformLoadKg(), type::setPlatformLoadKg);
        PatchRules.set(patch, "platformInsulationKv", values.platformInsulationKv(), type::setPlatformInsulationKv);
        PatchRules.set(patch, "towingCapacityT", values.towingCapacityT(), type::setTowingCapacityT);
        PatchRules.set(patch, "notes", SpecificationUtils.normalize(values.notes()), type::setNotes);
        UUID driverId = values.requiredDriverQualificationTypeId();
        QualificationType driver = type.getRequiredDriverQualification();
        if (driverId != null && (driver == null || !driverId.equals(driver.getId()))) {
            type.setRequiredDriverQualification(activeQualification(driverId));
        } else if (patch.clears("requiredDriverQualificationTypeId")) {
            type.setRequiredDriverQualification(null);
        }
        PatchRules.set(patch, "active", values.active(), type::setActive);
        requireConsistent(type);

        VehicleType saved = repository.saveAndFlush(type);
        Map<UUID, List<VehicleDocument>> documents = vehicles.isEmpty() ? Map.of()
                : documentRepository.findAllByVehicleIds(vehicles.stream().map(Vehicle::getId).toList()).stream()
                .collect(Collectors.groupingBy(document -> document.getVehicle().getId()));
        for (Vehicle vehicle : vehicles) {
            events.publish(ResourcesEvents.vehicle(vehicle, documents.getOrDefault(vehicle.getId(), List.of()),
                    Boolean.TRUE.equals(vehicle.getActive()) ? ResourcesEvents.UPDATED : ResourcesEvents.DEACTIVATED));
        }
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleTypeResponse findById(UUID id) {
        return mapper.toResponse(type(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleTypeResponse> search(String search, VehicleMobility mobility, VehicleCategory category,
                                                    VehicleCapability capability, Boolean active, Pageable pageable) {
        return PageMapper.toPageResponse(repository.findAll(VehicleTypeSpecification.filter(search, mobility, category, capability, active),
                pageable), mapper::toResponse);
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        type(id).setActive(false);
    }

    private VehicleType type(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Vehicle type", id));
    }

    /** La que se pide tiene que existir (404 QTY-404) y estar activa: de un tipo retirado no se dan nuevas. */
    private QualificationType activeQualification(UUID id) {
        QualificationType qualification = qualificationTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Qualification type", id));
        if (!Boolean.TRUE.equals(qualification.getActive())) {
            throw new QualificationRuleException("Qualification type " + qualification.getCode() + " is retired: a model cannot ask for it");
        }
        return qualification;
    }

    /** La de su movilidad (FleetRules), si el catalogo la tiene activa; si no, ninguna. */
    private QualificationType defaultDriverQualification(VehicleType type) {
        return FleetRules.defaultDriverQualification(type.getMobility(), Boolean.TRUE.equals(type.getSelfPropelled()),
                        type.getRequiredLicence())
                .flatMap(qualificationTypeRepository::findByCode)
                .filter(qualification -> Boolean.TRUE.equals(qualification.getActive()))
                .orElse(null);
    }

    private static Set<VehicleCapability> capabilities(Set<VehicleCapability> requested) {
        return requested == null || requested.isEmpty() ? EnumSet.noneOf(VehicleCapability.class) : EnumSet.copyOf(requested);
    }

    private static void requireConsistent(VehicleType type) {
        FleetRules.TypeShape shape = new FleetRules.TypeShape(
                type.getMobility(),
                type.getCategory(),
                type.getRailGearType() != null,
                type.getRailGearType() != null || type.getOnTrackingMinutes() != null || type.getNeedsPreparedAccess() != null,
                type.getTrackGaugeMm() != null || type.getMaxSpeedTrackWorkingKmh() != null || type.getMaxSpeedTrackTransportKmh() != null
                        || type.getMaxGradientPermille() != null || type.getMinCurveRadiusM() != null || type.getMaxWorkingCantMm() != null,
                Boolean.TRUE.equals(type.getTransportModeAuthorised()),
                type.getMaxSpeedRoadKmh() != null || type.getRequiredLicence() != null,
                type.getCapabilities(),
                type.getCraneCapacityT() != null || type.getCraneReachM() != null,
                type.getPlatformHeightM() != null || type.getPlatformLoadKg() != null,
                type.getPlatformInsulationKv() != null,
                type.getTowingCapacityT() != null);
        FleetRules.typeViolation(shape).ifPresent(reason -> {
            throw new ValidationException(reason);
        });
    }
}
