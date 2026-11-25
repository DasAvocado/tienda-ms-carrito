package com.tienda.ms_carrito.messaging;

import com.tienda.ms_carrito.dto.EventoNavegacion;
import com.tienda.ms_carrito.kafka.EventoKafkaPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Productor Kafka: interacciones con el carrito (agregar, quitar, cupon, envio). Clave = usuarioId. */
@Component
public class NavegacionPublisher {

    private final EventoKafkaPublisher kafka;
    private final String topico;

    public NavegacionPublisher(EventoKafkaPublisher kafka, @Value("${app.kafka.topicos.navegacion.nombre}") String topico) {
        this.kafka = kafka;
        this.topico = topico;
    }

    public void publicar(String tipo, String usuarioId, Long productoId, String nombre, Double precio,
                         Integer cantidad, String detalle) {
        kafka.publicar(topico, usuarioId, new EventoNavegacion(UUID.randomUUID().toString(), tipo, usuarioId,
                productoId, nombre, null, precio, cantidad, detalle, Instant.now().toString()));
    }
}
