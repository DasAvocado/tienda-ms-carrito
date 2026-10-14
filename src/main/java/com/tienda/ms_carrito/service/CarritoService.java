package com.tienda.ms_carrito.service;

import com.tienda.ms_carrito.config.RabbitMQConfig;
import com.tienda.ms_carrito.dto.CheckoutEvent;
import com.tienda.ms_carrito.dto.CheckoutResponse;
import com.tienda.ms_carrito.dto.ItemRequest;
import com.tienda.ms_carrito.model.ItemCarrito;
import com.tienda.ms_carrito.repository.ItemCarritoRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CarritoService {

    private final ItemCarritoRepository repo;
    private final RabbitTemplate rabbitTemplate;

    public CarritoService(ItemCarritoRepository repo, RabbitTemplate rabbitTemplate) {
        this.repo = repo;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional(readOnly = true)
    public List<ItemCarrito> listar(String usuarioId) {
        return repo.findByUsuarioIdOrderByIdAsc(usuarioId);
    }

    /** Si el producto ya esta en el carrito, suma la cantidad. */
    @Transactional
    public ItemCarrito agregar(String usuarioId, ItemRequest req) {
        if (req.productoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productoId es obligatorio");
        }
        int cantidad = req.cantidad() == null ? 1 : req.cantidad();
        if (cantidad < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser >= 1");
        }

        ItemCarrito item = repo.findByUsuarioIdAndProductoId(usuarioId, req.productoId()).orElseGet(() -> {
            if (req.nombre() == null || req.precio() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre y precio son obligatorios");
            }
            ItemCarrito nuevo = new ItemCarrito();
            nuevo.setUsuarioId(usuarioId);
            nuevo.setProductoId(req.productoId());
            nuevo.setNombre(req.nombre());
            nuevo.setPrecio(req.precio());
            nuevo.setCantidad(0);
            return nuevo;
        });
        item.setCantidad(item.getCantidad() + cantidad);
        return repo.save(item);
    }

    @Transactional
    public ItemCarrito cambiarCantidad(String usuarioId, Long itemId, int cantidad) {
        if (cantidad < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser >= 1");
        }
        ItemCarrito item = buscar(usuarioId, itemId);
        item.setCantidad(cantidad);
        return repo.save(item);
    }

    @Transactional
    public void eliminar(String usuarioId, Long itemId) {
        repo.delete(buscar(usuarioId, itemId));
    }

    @Transactional
    public void vaciar(String usuarioId) {
        repo.deleteByUsuarioId(usuarioId);
    }

    /**
     * Publica el evento "carrito.checkout" en RabbitMQ y vacia el carrito.
     * La orden formal la crea ms-ordenes de forma asincrona.
     */
    @Transactional
    public CheckoutResponse checkout(String usuarioId, String email, String nombre) {
        List<ItemCarrito> items = repo.findByUsuarioIdOrderByIdAsc(usuarioId);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El carrito esta vacio");
        }

        List<CheckoutEvent.Item> itemsEvento = items.stream()
                .map(i -> new CheckoutEvent.Item(i.getProductoId(), i.getNombre(), i.getPrecio(), i.getCantidad()))
                .toList();
        double total = itemsEvento.stream().mapToDouble(i -> i.precio() * i.cantidad()).sum();

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_CHECKOUT,
                new CheckoutEvent(usuarioId, email, nombre, itemsEvento));

        repo.deleteByUsuarioId(usuarioId);
        return new CheckoutResponse("EN_PROCESO", "Compra recibida. Tu orden se esta procesando.", total);
    }

    private ItemCarrito buscar(String usuarioId, Long itemId) {
        return repo.findByIdAndUsuarioId(itemId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));
    }
}
