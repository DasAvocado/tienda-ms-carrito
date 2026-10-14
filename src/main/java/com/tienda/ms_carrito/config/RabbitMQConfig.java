package com.tienda.ms_carrito.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ms-carrito solo PUBLICA eventos en la exchange "pedidos360.events". */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "pedidos360.events";
    public static final String RK_CHECKOUT = "carrito.checkout";

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
