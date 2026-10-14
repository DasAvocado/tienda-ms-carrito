package com.tienda.ms_carrito.controller;

import com.tienda.ms_carrito.dto.CantidadRequest;
import com.tienda.ms_carrito.dto.CheckoutResponse;
import com.tienda.ms_carrito.dto.ItemRequest;
import com.tienda.ms_carrito.model.ItemCarrito;
import com.tienda.ms_carrito.security.Seguridad;
import com.tienda.ms_carrito.security.UsuarioActual;
import com.tienda.ms_carrito.service.CarritoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Carrito PRIVADO por usuario (se identifica con el claim "oid" del JWT).
 * Requiere scope Menu-compra y rol CLIENTE o ADMIN.
 *
 *   GET    /api/carrito               -> items del carrito
 *   POST   /api/carrito/items         -> agregar item {productoId, nombre, precio, cantidad}
 *   PUT    /api/carrito/items/{id}    -> cambiar cantidad {cantidad}
 *   DELETE /api/carrito/items/{id}    -> quitar item
 *   DELETE /api/carrito/items         -> vaciar carrito
 *   POST   /api/carrito/checkout      -> confirmar compra (publica evento en RabbitMQ)
 */
@RestController
@RequestMapping("/api/carrito")
@PreAuthorize(Seguridad.CLIENTE)
public class CarritoController {

    private final CarritoService service;

    public CarritoController(CarritoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ItemCarrito> obtenerCarrito(@AuthenticationPrincipal Jwt jwt) {
        return service.listar(UsuarioActual.id(jwt));
    }

    @PostMapping("/items")
    public ResponseEntity<ItemCarrito> agregarItem(@AuthenticationPrincipal Jwt jwt, @RequestBody ItemRequest item) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.agregar(UsuarioActual.id(jwt), item));
    }

    @PutMapping("/items/{id}")
    public ItemCarrito cambiarCantidad(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                       @RequestBody CantidadRequest body) {
        int cantidad = body.cantidad() == null ? 0 : body.cantidad();
        return service.cambiarCantidad(UsuarioActual.id(jwt), id, cantidad);
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> eliminarItem(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.eliminar(UsuarioActual.id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> vaciarCarrito(@AuthenticationPrincipal Jwt jwt) {
        service.vaciar(UsuarioActual.id(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@AuthenticationPrincipal Jwt jwt) {
        CheckoutResponse resp = service.checkout(UsuarioActual.id(jwt), UsuarioActual.email(jwt), UsuarioActual.nombre(jwt));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }
}
