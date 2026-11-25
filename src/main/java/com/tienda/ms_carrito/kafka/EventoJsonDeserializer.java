package com.tienda.ms_carrito.kafka;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deserializador Kafka TIPADO: JSON -> DTO concreto (record). Se envuelve en ErrorHandlingDeserializer,
 * asi un JSON corrupto no bloquea la particion: el error llega al DefaultErrorHandler y el mensaje va al .dlt.
 * Ignora campos desconocidos para tolerar productores con versiones mas nuevas del evento.
 */
public class EventoJsonDeserializer<T> implements Deserializer<T> {

    private final Class<T> tipo;
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public EventoJsonDeserializer(Class<T> tipo) {
        this.tipo = tipo;
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            return mapper.readValue(data, tipo);
        } catch (RuntimeException e) {
            throw new SerializationException("JSON invalido en " + topic + " para " + tipo.getSimpleName()
                    + ": " + e.getMessage(), e);
        }
    }
}
