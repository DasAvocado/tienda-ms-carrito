package com.tienda.ms_carrito.messaging;

import com.rabbitmq.client.Channel;
import com.tienda.ms_carrito.dto.PagoAprobadoEvent;
import com.tienda.ms_carrito.service.CarritoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor RabbitMQ del COMANDO "pago.aprobado" (cola carrito.pago-aprobado, con DLQ).
 * Se mantiene en RabbitMQ porque es una TAREA que debe ejecutarse exactamente una vez, con ACK y DLQ.
 */
@Component
public class PagoAprobadoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PagoAprobadoConsumer.class);

    private final CarritoService carritoService;
    private final ConfirmacionMensajes confirmacion;

    public PagoAprobadoConsumer(CarritoService carritoService, ConfirmacionMensajes confirmacion) {
        this.carritoService = carritoService;
        this.confirmacion = confirmacion;
    }

    @RabbitListener(queues = "${app.rabbit.queues.pago-aprobado}", ackMode = "MANUAL")
    public void onPagoAprobado(@Payload PagoAprobadoEvent evento, Message mensaje, Channel canal) throws IOException {
        log.info("[RabbitMQ] pago.aprobado recibido: pagoId={} usuario={} monto={}", evento.pagoId(), evento.usuarioId(), evento.monto());
        confirmacion.procesar(mensaje, canal, () -> {
            if (evento.pagoId() == null || evento.usuarioId() == null || evento.monto() <= 0) {
                throw new MensajeInvalidoException("pago.aprobado sin pagoId, usuario o monto");
            }
            carritoService.cerrarCompraPagada(evento);
        });
    }
}
