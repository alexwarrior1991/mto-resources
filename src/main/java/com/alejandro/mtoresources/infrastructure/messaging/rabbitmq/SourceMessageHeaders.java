package com.alejandro.mtoresources.infrastructure.messaging.rabbitmq;

/**
 * AMQP headers that the outbox of every producer of the domain puts on its messages: the one of
 * {@code mto-maintenance}, which this service consumes, is a copy of the one of {@code mto-configuration}.
 *
 * <p>Están aquí porque son parte del contrato igual que el payload, y porque leerlos por su nombre
 * literal repartido por el código es la forma habitual de que un cambio en el emisor pase
 * inadvertido: una cabecera que deja de existir se lee como {@code null}, no como un error.</p>
 */
public final class SourceMessageHeaders {

    /** Tipo de evento, duplicado en cabecera para poder enrutar sin abrir el payload. */
    public static final String EVENT_TYPE = "eventType";

    /** Nombre lógico de la entidad publicada. */
    public static final String AGGREGATE_TYPE = "aggregateType";

    /** Identificador de la entidad publicada. */
    public static final String AGGREGATE_ID = "aggregateId";

    /**
     * Número de secuencia por agregado.
     *
     * <p>El emisor publica en orden, pero la entrega es <i>at-least-once</i> y un redrive puede
     * reenviar algo antiguo. Es global y creciente en el emisor: un número por debajo del último
     * aplicado describe un estado anterior.</p>
     */
    public static final String SEQUENCE_NUMBER = "sequenceNumber";

    /**
     * Firma sobre los bytes que viajan de verdad.
     *
     * <p>Es la única comprobación de integridad que el consumidor puede rehacer sin reserializar
     * nada. Con {@code app.messaging.signature.secret} compartido es un HMAC-SHA256 y protege de
     * manipulación; sin secreto es un SHA-256 simple y solo detecta corrupción. La comprueba
     * {@code MessagePayloadSignatureVerifier} antes de que el mensaje llegue al inbox.</p>
     */
    public static final String SIGNATURE = "messageSignature";

    /** Algoritmo con el que se calculó {@link #SIGNATURE}, para no tener que adivinarlo. */
    public static final String SIGNATURE_ALGORITHM = "messageSignatureAlgorithm";

    private SourceMessageHeaders() {
    }
}
