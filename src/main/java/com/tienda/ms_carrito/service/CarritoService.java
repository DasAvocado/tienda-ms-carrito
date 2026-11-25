package com.tienda.ms_carrito.service;

import com.tienda.ms_carrito.dto.CheckoutEvent;
import com.tienda.ms_carrito.dto.CheckoutResponse;
import com.tienda.ms_carrito.dto.ItemRequest;
import com.tienda.ms_carrito.dto.PagoAprobadoEvent;
import com.tienda.ms_carrito.dto.ResumenCarrito;
import com.tienda.ms_carrito.messaging.CheckoutPublisher;
import com.tienda.ms_carrito.messaging.MensajeInvalidoException;
import com.tienda.ms_carrito.messaging.NavegacionPublisher;
import com.tienda.ms_carrito.messaging.TransaccionPublisher;
import com.tienda.ms_carrito.model.CarritoInfo;
import com.tienda.ms_carrito.model.ItemCarrito;
import com.tienda.ms_carrito.repository.CarritoInfoRepository;
import com.tienda.ms_carrito.repository.ItemCarritoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Logica de negocio del carrito (no conoce RabbitMQ ni Kafka: usa los *Publisher). */
@Service
public class CarritoService {

    private static final Logger log = LoggerFactory.getLogger(CarritoService.class);

    private final ItemCarritoRepository repo;
    private final CarritoInfoRepository infoRepo;
    private final CheckoutPublisher checkoutPublisher;
    private final NavegacionPublisher navegacion;
    private final TransaccionPublisher transacciones;
    private final PreciosCatalogo precios;

    public CarritoService(ItemCarritoRepository repo, CarritoInfoRepository infoRepo, CheckoutPublisher checkoutPublisher,
                          NavegacionPublisher navegacion, TransaccionPublisher transacciones, PreciosCatalogo precios) {
        this.repo = repo;
        this.infoRepo = infoRepo;
        this.checkoutPublisher = checkoutPublisher;
        this.navegacion = navegacion;
        this.transacciones = transacciones;
        this.precios = precios;
    }

    @Transactional(readOnly = true)
    public List<ItemCarrito> listar(String usuarioId) {
        return repo.findByUsuarioIdOrderByIdAsc(usuarioId);
    }

    /**
     * Si el producto ya esta en el carrito, suma la cantidad.
     * Nombre y precio se toman del catalogo OFICIAL (topico Kafka pedidos360.catalogo); los del navegador
     * solo se usan si el catalogo aun no se ha recibido (p.ej. Kafka caido al arrancar).
     */
    @Transactional
    public ItemCarrito agregar(String usuarioId, ItemRequest req) {
        if (req.productoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productoId es obligatorio");
        }
        int cantidad = req.cantidad() == null ? 1 : req.cantidad();
        if (cantidad < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser >= 1");
        }

        var oficial = precios.buscar(req.productoId());
        if (oficial.isPresent() && oficial.get().stock() != null && oficial.get().stock() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, oficial.get().nombre() + " no tiene stock");
        }
        ItemRequest datos = oficial.map(p -> new ItemRequest(req.productoId(), p.nombre(), p.precio(), req.cantidad())).orElse(req);
        if (oficial.isPresent() && req.precio() != null && !req.precio().equals(oficial.get().precio())) {
            log.warn("Precio enviado por el cliente ({}) distinto al del catalogo ({}) para producto {}: se usa el del catalogo",
                    req.precio(), oficial.get().precio(), req.productoId());
        }
        ItemCarrito item = repo.findByUsuarioIdAndProductoId(usuarioId, datos.productoId()).orElseGet(() -> {
            if (datos.nombre() == null || datos.precio() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre y precio son obligatorios");
            }
            ItemCarrito nuevo = new ItemCarrito();
            nuevo.setUsuarioId(usuarioId);
            nuevo.setProductoId(datos.productoId());
            nuevo.setNombre(datos.nombre());
            nuevo.setPrecio(datos.precio());
            nuevo.setCantidad(0);
            return nuevo;
        });
        item.setCantidad(item.getCantidad() + cantidad);
        oficial.ifPresent(p -> item.setPrecio(p.precio()));   // si el precio cambio en el catalogo, se actualiza
        ItemCarrito guardado = repo.save(item);
        navegacion.publicar("PRODUCTO_AGREGADO_CARRITO", usuarioId, guardado.getProductoId(), guardado.getNombre(),
                guardado.getPrecio(), cantidad, null);
        return guardado;
    }

    @Transactional
    public ItemCarrito cambiarCantidad(String usuarioId, Long itemId, int cantidad) {
        if (cantidad < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser >= 1");
        }
        ItemCarrito item = buscar(usuarioId, itemId);
        item.setCantidad(cantidad);
        ItemCarrito guardado = repo.save(item);
        navegacion.publicar("CANTIDAD_ACTUALIZADA", usuarioId, item.getProductoId(), item.getNombre(),
                item.getPrecio(), cantidad, null);
        return guardado;
    }

    @Transactional
    public void eliminar(String usuarioId, Long itemId) {
        ItemCarrito item = buscar(usuarioId, itemId);
        repo.delete(item);
        navegacion.publicar("PRODUCTO_QUITADO_CARRITO", usuarioId, item.getProductoId(), item.getNombre(),
                item.getPrecio(), item.getCantidad(), null);
    }

    @Transactional
    public void vaciar(String usuarioId) {
        repo.deleteByUsuarioId(usuarioId);
    }

    // ------------------------------------------------------------------ cupones y envio

    @Transactional
    public ResumenCarrito aplicarCupon(String usuarioId, String codigo) {
        double subtotal = subtotal(repo.findByUsuarioIdOrderByIdAsc(usuarioId));
        Cupones.Resultado r = Cupones.evaluar(codigo, subtotal);
        navegacion.publicar("CUPON_APLICADO", usuarioId, null, null, null, null,
                Cupones.normalizar(codigo) + (r.valido() ? " (valido)" : " (rechazado: " + r.descripcion() + ")"));
        if (!r.valido()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    r.descripcion() == null ? "Ingresa un codigo de cupon" : r.descripcion());
        }
        CarritoInfo info = info(usuarioId);
        info.setCupon(Cupones.normalizar(codigo));
        infoRepo.save(info);
        return resumen(usuarioId);
    }

    @Transactional
    public ResumenCarrito quitarCupon(String usuarioId) {
        CarritoInfo info = info(usuarioId);
        info.setCupon(null);
        infoRepo.save(info);
        return resumen(usuarioId);
    }

    @Transactional
    public ResumenCarrito elegirEnvio(String usuarioId, String tipo) {
        try {
            Cupones.costoEnvio(tipo);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de envio invalido: usa RETIRO, ESTANDAR o EXPRESS");
        }
        CarritoInfo info = info(usuarioId);
        info.setTipoEnvio(tipo.toUpperCase());
        infoRepo.save(info);
        navegacion.publicar("ENVIO_SELECCIONADO", usuarioId, null, null, Cupones.costoEnvio(tipo), null, tipo.toUpperCase());
        return resumen(usuarioId);
    }

    /** Totales calculados en el servidor: subtotal - descuento + envio. */
    @Transactional(readOnly = true)
    public ResumenCarrito resumen(String usuarioId) {
        List<ItemCarrito> items = repo.findByUsuarioIdOrderByIdAsc(usuarioId);
        CarritoInfo info = infoRepo.findById(usuarioId).orElseGet(() -> new CarritoInfo(usuarioId));
        double subtotal = subtotal(items);
        Cupones.Resultado cupon = Cupones.evaluar(info.getCupon(), subtotal);
        String tipoEnvio = info.getTipoEnvio() == null ? "ESTANDAR" : info.getTipoEnvio();
        double envio = items.isEmpty() || cupon.envioGratis() ? 0 : Cupones.costoEnvio(tipoEnvio);
        double descuento = cupon.valido() ? cupon.descuento() : 0;
        double total = Math.max(0, subtotal - descuento + envio);
        return new ResumenCarrito(items, subtotal, descuento, envio, total,
                cupon.valido() ? info.getCupon() : null, cupon.valido() ? cupon.descripcion() : null, tipoEnvio);
    }

    // ------------------------------------------------------------------ cierre de compra

    /**
     * Comando "pago.aprobado" de ms-pagos: verifica que el monto pagado coincida con el total del carrito,
     * publica "carrito.checkout" (RabbitMQ -> ms-ordenes), registra CHECKOUT_REALIZADO (Kafka) y vacia el carrito.
     */
    @Transactional
    public void cerrarCompraPagada(PagoAprobadoEvent pago) {
        ResumenCarrito r = resumen(pago.usuarioId());
        if (r.items().isEmpty()) {
            // Idempotencia: el mismo pago ya cerro este carrito (mensaje repetido). No es un error.
            log.warn("pago.aprobado {} sin items en el carrito de {}: ya procesado, se ignora", pago.pagoId(), pago.usuarioId());
            return;
        }
        if (Math.abs(r.total() - pago.monto()) > 1) {
            throw new MensajeInvalidoException("Monto pagado " + pago.monto() + " no coincide con el total del carrito "
                    + r.total() + " (pagoId=" + pago.pagoId() + ")");
        }
        publicarCheckout(pago.usuarioId(), pago.email(), pago.nombre(), r, pago.pagoId(), pago.metodo());
    }

    /** Checkout SIN pago (solo pruebas, app.checkout-directo=true). */
    @Transactional
    public CheckoutResponse checkout(String usuarioId, String email, String nombre) {
        ResumenCarrito r = resumen(usuarioId);
        if (r.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El carrito esta vacio");
        }
        publicarCheckout(usuarioId, email, nombre, r, null, "SIN_PAGO");
        return new CheckoutResponse("EN_PROCESO", "Compra recibida. Tu orden se esta procesando.", r.total());
    }

    private void publicarCheckout(String usuarioId, String email, String nombre, ResumenCarrito r, Long pagoId, String metodo) {
        List<CheckoutEvent.Item> itemsEvento = r.items().stream()
                .map(i -> new CheckoutEvent.Item(i.getProductoId(), i.getNombre(), i.getPrecio(), i.getCantidad()))
                .toList();
        checkoutPublisher.publicar(new CheckoutEvent(usuarioId, email, nombre, itemsEvento, pagoId, metodo,
                r.subtotal(), r.descuento(), r.costoEnvio(), r.total(), r.cupon(), r.tipoEnvio()));
        transacciones.checkoutRealizado(usuarioId, pagoId, r.total(), itemsEvento.size(),
                "metodo=" + metodo + (r.cupon() != null ? " cupon=" + r.cupon() : "") + " envio=" + r.tipoEnvio());
        repo.deleteByUsuarioId(usuarioId);
        infoRepo.deleteById(usuarioId);
        log.info("Checkout de {} publicado (pagoId={}, total={})", usuarioId, pagoId, r.total());
    }

    private CarritoInfo info(String usuarioId) {
        return infoRepo.findById(usuarioId).orElseGet(() -> new CarritoInfo(usuarioId));
    }

    private static double subtotal(List<ItemCarrito> items) {
        return items.stream().mapToDouble(i -> i.getPrecio() * i.getCantidad()).sum();
    }

    private ItemCarrito buscar(String usuarioId, Long itemId) {
        return repo.findByIdAndUsuarioId(itemId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));
    }
}
