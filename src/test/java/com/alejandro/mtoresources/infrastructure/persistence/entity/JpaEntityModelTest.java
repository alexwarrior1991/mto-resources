package com.alejandro.mtoresources.infrastructure.persistence.entity;

import com.alejandro.mtoresources.infrastructure.messaging.outbox.OutboxMessage;
import jakarta.persistence.Column;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Que se audita y que no, a proposito (docs/07-auditing.md). Una entidad que gana {@code @Audited} sin
 * su gemela {@code _aud} impide arrancar con {@code ddl-auto: validate}; una que lo pierde deja de
 * tener historial sin que nadie lo note.
 */
class JpaEntityModelTest {

    @Test
    void theEntitiesEditedThroughTheApiAreAudited() {
        for (Class<?> type : List.of(Person.class, OrganizationUnit.class, TeamMember.class, Vehicle.class, PersonQualification.class,
                VehicleDocument.class, Instrument.class)) {
            assertTrue(type.isAnnotationPresent(Audited.class), type.getSimpleName());
        }
    }

    @Test
    void cataloguesHistoriesAndNativeSqlTablesAreNotAudited() {
        for (Class<?> type : List.of(VehicleType.class, ParkingSite.class, QualificationType.class, VehicleLocationEvent.class,
                VehiclePosition.class, InboxMessage.class, BackgroundOperation.class, OutboxMessage.class)) {
            assertFalse(type.isAnnotationPresent(Audited.class), type.getSimpleName());
        }
        assertNull(AuditableEntity.class.getAnnotation(Audited.class), "On each entity, never on the base class");
    }

    @Test
    void theModelOfAVehicleIsReadAsItIsNow() throws NoSuchFieldException {
        Audited audited = Vehicle.class.getDeclaredField("type").getAnnotation(Audited.class);
        assertEquals(RelationTargetAuditMode.NOT_AUDITED, audited.targetAuditMode());
    }

    @Test
    void theTypeOfAQualificationIsReadAsItIsNow() throws NoSuchFieldException {
        Audited audited = PersonQualification.class.getDeclaredField("qualificationType").getAnnotation(Audited.class);
        assertEquals(RelationTargetAuditMode.NOT_AUDITED, audited.targetAuditMode());
    }

    /**
     * Las marcas de aviso de caducidad no son historia y solo las escribe el vigilante con SQL nativo:
     * Hibernate las lee y nunca las escribe, para que guardar la fila no devuelva una marca vieja.
     */
    @Test
    void theExpiryMarksAreNotAuditedAndHibernateNeverWritesThem() throws NoSuchFieldException {
        for (Class<?> type : List.of(PersonQualification.class, VehicleDocument.class, Instrument.class)) {
            for (String mark : List.of("expiringNotifiedFor", "expiredNotifiedFor")) {
                Field field = type.getDeclaredField(mark);
                assertTrue(field.isAnnotationPresent(NotAudited.class), type.getSimpleName() + "." + mark);
                Column column = field.getAnnotation(Column.class);
                assertFalse(column.insertable() || column.updatable(), type.getSimpleName() + "." + mark);
            }
        }
    }
}
