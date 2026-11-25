package com.tienda.ms_carrito.messaging;

import com.tienda.ms_carrito.dto.EventoTransaccion;
import com.tienda.ms_carrito.kafka.EventoKafkaPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Productor Kafka del hecho CHECKOUT_REALIZADO (auditoria y metricas lo leen del topico transacciones). */
@Component
public class TransaccionPublisher {

    private final EventoKafkaPublisher kafka;
    private final String topico;

    public TransaccionPublisher(EventoKafkaPublisher kafka, @Value("${app.kafka.topicos.transacciones.nombre}") String topico) {
        this.kafka = kafka;
        this.topico = topico;
    }

    public void checkoutRealizado(String usuarioId, Long pagoId, double total, int items, String detalle) {
        kafka.publicar(topico, usuarioId, new EventoTransaccion(UUID.randomUUID().toString(), "CHECKOUT_REALIZADO",
                usuarioId, null, pagoId, total, items, detalle, Instant.now().toString()));
    }
}
