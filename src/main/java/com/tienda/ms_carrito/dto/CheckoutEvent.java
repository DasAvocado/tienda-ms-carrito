package com.tienda.ms_carrito.dto;

import java.util.List;

/** Evento "carrito.checkout": lo consume ms-ordenes para crear la orden formal. */
public record CheckoutEvent(
        String usuarioId,
        String email,
        String nombre,
        List<Item> items) {

    public record Item(Long productoId, String nombre, double precio, int cantidad) {}
}
