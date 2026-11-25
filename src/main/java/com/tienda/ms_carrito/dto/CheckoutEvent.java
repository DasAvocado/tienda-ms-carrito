package com.tienda.ms_carrito.dto;

import java.util.List;

/** Evento RabbitMQ "carrito.checkout" (lo consume ms-ordenes). Incluye el pago y los totales calculados. */
public record CheckoutEvent(
        String usuarioId,
        String email,
        String nombre,
        List<Item> items,
        Long pagoId,
        String metodoPago,
        double subtotal,
        double descuento,
        double costoEnvio,
        double total,
        String cupon,
        String tipoEnvio) {

    public record Item(Long productoId, String nombre, double precio, int cantidad) {}
}
