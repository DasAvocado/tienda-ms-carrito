package com.tienda.ms_carrito.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Infraestructura de CONSUMO Kafka (separada de la logica de negocio):
 *  - Un ContainerFactory TIPADO por DTO (EventoJsonDeserializer<T> envuelto en ErrorHandlingDeserializer).
 *  - Consumer group = app.kafka.group-id: las particiones de cada topico se REPARTEN entre los hilos
 *    (concurrency) e instancias del mismo grupo; otro grupo (p.ej. ms-auditoria) recibe TODOS los mensajes.
 *  - Manejo de errores: 2 reintentos con 1 s de espera; si siguen fallando, o el payload es invalido
 *    (JSON corrupto / EventoInvalidoException), el mensaje se publica en "<topico>.dlt" y la particion sigue avanzando.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${app.kafka.bootstrap-servers}")
    private String bootstrap;

    @Value("${app.kafka.group-id}")
    private String groupId;

    @Value("${app.kafka.concurrency:3}")
    private int concurrency;

    @Value("${app.kafka.reintentos:2}")
    private long reintentos;

    public <T> ConcurrentKafkaListenerContainerFactory<String, T> fabrica(Class<T> tipo, KafkaTemplate<String, Object> template) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);   // el contenedor confirma el offset tras procesar
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        DefaultKafkaConsumerFactory<String, T> cf = new DefaultKafkaConsumerFactory<>(cfg,
                new StringDeserializer(), new ErrorHandlingDeserializer<>(new EventoJsonDeserializer<>(tipo)));

        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(manejadorErrores(template));
        return factory;
    }

    private DefaultErrorHandler manejadorErrores(KafkaTemplate<String, Object> template) {
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(template,
                (registro, ex) -> {
                    log.error("[Kafka DLT] {}-{}@{} -> {}.dlt | causa: {}", registro.topic(), registro.partition(),
                            registro.offset(), registro.topic(), ex.getMessage());
                    return new TopicPartition(registro.topic() + ".dlt", -1);
                });
        DefaultErrorHandler handler = new DefaultErrorHandler(dlt, new FixedBackOff(1000L, reintentos));
        handler.addNotRetryableExceptions(EventoInvalidoException.class, IllegalArgumentException.class);
        handler.setRetryListeners((registro, ex, intento) ->
                log.warn("[Kafka REINTENTO {}/{}] {}-{}@{}: {}", intento, reintentos, registro.topic(),
                        registro.partition(), registro.offset(), ex.getMessage()));
        return handler;
    }
}
