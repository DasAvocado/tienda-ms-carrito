package com.tienda.ms_carrito.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ de ms-carrito. Los NOMBRES se definen solo en application.yml (app.rabbit.*).
 *
 *   [pedidos360.events] --pago.aprobado--> carrito.pago-aprobado   (consume PagoAprobadoConsumer)
 *   carrito.pago-aprobado --(rechazo/reintentos agotados)--> [pedidos360.dlx] --> carrito.pago-aprobado.dlq
 *   ms-carrito PUBLICA carrito.checkout en [pedidos360.events] (CheckoutPublisher)
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    @Value("${app.rabbit.exchange}") private String exchange;
    @Value("${app.rabbit.dlx}") private String dlx;
    @Value("${app.rabbit.routing-keys.pago-aprobado}") private String rkPagoAprobado;
    @Value("${app.rabbit.queues.pago-aprobado}") private String colaPago;
    @Value("${app.rabbit.queues.pago-aprobado-dlq}") private String colaPagoDlq;

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlx, true, false);
    }

    @Bean
    public Queue colaPagoAprobado() {
        return QueueBuilder.durable(colaPago).deadLetterExchange(dlx).deadLetterRoutingKey(colaPagoDlq).build();
    }

    @Bean
    public Binding bindingPagoAprobado() {
        return BindingBuilder.bind(colaPagoAprobado()).to(eventosExchange()).with(rkPagoAprobado);
    }

    @Bean
    public Queue colaPagoAprobadoDlq() {
        return QueueBuilder.durable(colaPagoDlq).build();
    }

    @Bean
    public Binding bindingPagoAprobadoDlq() {
        return BindingBuilder.bind(colaPagoAprobadoDlq()).to(deadLetterExchange()).with(colaPagoDlq);
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
