package com.tienda.ms_carrito.messaging;

import com.tienda.ms_carrito.dto.CheckoutEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Productor del evento "carrito.checkout". El servicio de negocio no conoce exchanges ni routing keys. */
@Component
public class CheckoutPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String rkCheckout;

    public CheckoutPublisher(RabbitTemplate rabbitTemplate,
                             @Value("${app.rabbit.exchange}") String exchange,
                             @Value("${app.rabbit.routing-keys.checkout}") String rkCheckout) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.rkCheckout = rkCheckout;
    }

    public void publicar(CheckoutEvent evento) {
        rabbitTemplate.convertAndSend(exchange, rkCheckout, evento);
    }
}
