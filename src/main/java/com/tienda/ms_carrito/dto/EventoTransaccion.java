package com.tienda.ms_carrito.dto;

/** DTO del topico Kafka "pedidos360.transacciones" (CHECKOUT_REALIZADO / ORDEN_CREADA / NOTIFICACION_ENVIADA). */
public record EventoTransaccion(
        String eventoId,
        String tipo,
        String usuarioId,
        Long ordenId,
        Long pagoId,
        Double total,
        Integer items,
        String detalle,
        String fecha) {}
