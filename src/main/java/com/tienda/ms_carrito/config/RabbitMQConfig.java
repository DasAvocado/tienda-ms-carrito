package com.tienda.ms_carrito.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ms-carrito solo PUBLICA eventos (carrito.checkout) en la exchange topic "pedidos360.events".
 * Los nombres se definen en application.yml (app.rabbit.*).
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    @Bean
    public TopicExchange eventosExchange(@Value("${app.rabbit.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /** RabbitTemplate con confirmaciones del broker: registra en log los mensajes NO entregados. */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlacion, ack, causa) -> {
            if (!ack) log.error("[RabbitMQ] El broker NO confirmo el mensaje: {}", causa);
        });
        template.setReturnsCallback(r -> log.error(
                "[RabbitMQ] Mensaje no enrutable (sin cola destino): exchange={} routingKey={} motivo={}",
                r.getExchange(), r.getRoutingKey(), r.getReplyText()));
        return template;
    }
}
