package com.tienda.ms_carrito.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import com.tienda.ms_carrito.dto.ProductoCatalogoEvento;

/** Topicos que PRODUCE ms-carrito (se crean si no existen; la definicion es la misma en todo el sistema). */
@Configuration
public class KafkaTopicosConfig {

    @Value("${app.kafka.replicas}")
    private int replicas;

    @Bean
    public NewTopic topicoNavegacion(@Value("${app.kafka.topicos.navegacion.nombre}") String nombre,
                                     @Value("${app.kafka.topicos.navegacion.particiones}") int particiones,
                                     @Value("${app.kafka.topicos.navegacion.retencion-ms}") long retencion,
                                     @Value("${app.kafka.topicos.navegacion.min-isr}") int minIsr) {
        return Topicos.crear(nombre, particiones, replicas, retencion, "delete", minIsr);
    }

    @Bean
    public NewTopic topicoTransacciones(@Value("${app.kafka.topicos.transacciones.nombre}") String nombre,
                                        @Value("${app.kafka.topicos.transacciones.particiones}") int particiones,
                                        @Value("${app.kafka.topicos.transacciones.retencion-ms}") long retencion,
                                        @Value("${app.kafka.topicos.transacciones.min-isr}") int minIsr) {
        return Topicos.crear(nombre, particiones, replicas, retencion, "delete", minIsr);
    }

    /** Consumidor tipado del topico compactado pedidos360.catalogo (1 hilo: es una tabla pequena). */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProductoCatalogoEvento> catalogoFactory(
            KafkaConsumerConfig consumidores, KafkaTemplate<String, Object> template) {
        ConcurrentKafkaListenerContainerFactory<String, ProductoCatalogoEvento> f =
                consumidores.fabrica(ProductoCatalogoEvento.class, template);
        f.setConcurrency(1);
        return f;
    }

    /** Destino de los mensajes defectuosos del catalogo (DefaultErrorHandler + DeadLetterPublishingRecoverer). */
    @Bean
    public NewTopic dltCatalogo(@Value("${app.kafka.topicos.catalogo.nombre}") String nombre) {
        return Topicos.crear(nombre + ".dlt", 1, replicas, 1209600000L, "delete", 1);
    }
}
