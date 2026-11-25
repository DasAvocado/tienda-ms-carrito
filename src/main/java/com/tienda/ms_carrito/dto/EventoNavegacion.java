package com.tienda.ms_carrito.dto;

/**
 * DTO del topico Kafka "pedidos360.navegacion" (mismo contrato en ms-catalogo y ms-recomendaciones).
 * tipo: PRODUCTO_AGREGADO_CARRITO | PRODUCTO_QUITADO_CARRITO | CANTIDAD_ACTUALIZADA | CUPON_APLICADO | ENVIO_SELECCIONADO
 */
public record EventoNavegacion(
        String eventoId,
        String tipo,
        String usuarioId,
        Long productoId,
        String productoNombre,
        String categoria,
        Double precio,
        Integer cantidad,
        String detalle,
        String fecha) {}
