package com.tienda.ms_carrito.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

    @GetMapping
    public String obtenerCarrito() {
        return "Microservicio de Carrito activo y protegido con Azure.";
    }
}