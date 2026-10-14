package com.tienda.ms_carrito.dto;

public record ItemRequest(Long productoId, String nombre, Double precio, Integer cantidad) {}
