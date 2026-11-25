package com.tienda.ms_carrito.dto;

/** Comando RabbitMQ "pago.aprobado" enviado por ms-pagos: ms-carrito debe cerrar la compra. */
public record PagoAprobadoEvent(
        Long pagoId,
        String usuarioId,
        String email,
        String nombre,
        double monto,
        String metodo,
        String fecha) {}
