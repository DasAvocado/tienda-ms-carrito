package com.tienda.ms_carrito.kafka;

/** Payload de Kafka con datos invalidos: NO se reintenta, va directo al topico .dlt. */
public class EventoInvalidoException extends RuntimeException {
    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
