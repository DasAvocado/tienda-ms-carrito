package com.tienda.ms_carrito.messaging;

import com.tienda.ms_carrito.dto.ProductoCatalogoEvento;
import com.tienda.ms_carrito.service.PreciosCatalogo;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumidor Kafka del topico COMPACTADO "pedidos360.catalogo" (consumer group "ms-carrito-precios").
 * Al asignarse las particiones vuelve al inicio: el topico funciona como una TABLA (ultimo valor por productoId).
 * Valor null = tombstone (producto eliminado).
 */
@Component
public class CatalogoConsumer implements ConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(CatalogoConsumer.class);

    private final PreciosCatalogo precios;

    public CatalogoConsumer(PreciosCatalogo precios) {
        this.precios = precios;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> asignadas, ConsumerSeekCallback callback) {
        callback.seekToBeginning(asignadas.keySet());
    }

    @KafkaListener(id = "carrito-precios", topics = "${app.kafka.topicos.catalogo.nombre}",
            groupId = "${app.kafka.group-id}", containerFactory = "catalogoFactory")
    public void onProducto(ConsumerRecord<String, ProductoCatalogoEvento> registro) {
        ProductoCatalogoEvento p = registro.value();
        if (p == null) {
            precios.eliminar(Long.valueOf(registro.key()));
            return;
        }
        if (p.productoId() == null || p.precio() == null || p.precio() < 0) {
            throw new com.tienda.ms_carrito.kafka.EventoInvalidoException("Producto de catalogo sin id o precio");
        }
        precios.actualizar(p);
        log.debug("[Kafka] precio oficial {} = {} (stock {})", p.nombre(), p.precio(), p.stock());
    }
}
