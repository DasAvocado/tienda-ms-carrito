package com.tienda.ms_carrito.controller;

import com.tienda.ms_carrito.dto.CantidadRequest;
import com.tienda.ms_carrito.dto.CheckoutResponse;
import com.tienda.ms_carrito.dto.CuponRequest;
import com.tienda.ms_carrito.dto.EnvioRequest;
import com.tienda.ms_carrito.dto.ItemRequest;
import com.tienda.ms_carrito.dto.ResumenCarrito;
import com.tienda.ms_carrito.model.ItemCarrito;
import com.tienda.ms_carrito.security.Seguridad;
import com.tienda.ms_carrito.security.UsuarioActual;
import com.tienda.ms_carrito.service.CarritoService;
import org.springframework.beans.factory.annotation.Value;
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
 *   GET    /api/carrito/resumen       -> subtotal, descuento, envio y total (calculados en el servidor)
 *   POST   /api/carrito/cupon         -> aplicar cupon {codigo}  (BIENVENIDA10, FRENOS15, ENVIOGRATIS)
 *   DELETE /api/carrito/cupon         -> quitar cupon
 *   PUT    /api/carrito/envio         -> tipo de envio {tipo: RETIRO|ESTANDAR|EXPRESS}
 *   POST   /api/carrito/checkout      -> compra SIN pago (deshabilitado por defecto: la compra la cierra ms-pagos)
 *
 * Cada interaccion publica un evento en Kafka (pedidos360.navegacion) para ms-recomendaciones.
 */
@RestController
@RequestMapping("/api/carrito")
@PreAuthorize(Seguridad.CLIENTE)
public class CarritoController {

    private final CarritoService service;
    private final boolean checkoutDirecto;

    public CarritoController(CarritoService service, @Value("${app.checkout-directo:false}") boolean checkoutDirecto) {
        this.service = service;
        this.checkoutDirecto = checkoutDirecto;
    }

    @GetMapping("/resumen")
    public ResumenCarrito resumen(@AuthenticationPrincipal Jwt jwt) {
        return service.resumen(UsuarioActual.id(jwt));
    }

    @PostMapping("/cupon")
    public ResumenCarrito aplicarCupon(@AuthenticationPrincipal Jwt jwt, @RequestBody CuponRequest body) {
        return service.aplicarCupon(UsuarioActual.id(jwt), body.codigo());
    }

    @DeleteMapping("/cupon")
    public ResumenCarrito quitarCupon(@AuthenticationPrincipal Jwt jwt) {
        return service.quitarCupon(UsuarioActual.id(jwt));
    }

    @PutMapping("/envio")
    public ResumenCarrito elegirEnvio(@AuthenticationPrincipal Jwt jwt, @RequestBody EnvioRequest body) {
        return service.elegirEnvio(UsuarioActual.id(jwt), body.tipo() == null ? "" : body.tipo());
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
        if (!checkoutDirecto) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new CheckoutResponse("REQUIERE_PAGO",
                    "La compra se confirma desde ms-pagos (POST /api/pagos/tarjeta o Webpay)", 0));
        }
        CheckoutResponse resp = service.checkout(UsuarioActual.id(jwt), UsuarioActual.email(jwt), UsuarioActual.nombre(jwt));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }
}
