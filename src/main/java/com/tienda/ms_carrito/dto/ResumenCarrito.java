package com.tienda.ms_carrito.dto;

import com.tienda.ms_carrito.model.ItemCarrito;

import java.util.List;

/** Totales del carrito calculados en el BACKEND (el monto que se paga sale de aqui, no del navegador). */
public record ResumenCarrito(
        List<ItemCarrito> items,
        double subtotal,
        double descuento,
        double costoEnvio,
        double total,
        String cupon,
        String descripcionCupon,
        String tipoEnvio) {}
