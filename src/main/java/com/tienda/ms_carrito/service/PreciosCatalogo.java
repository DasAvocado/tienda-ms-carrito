package com.tienda.ms_carrito.service;

import com.tienda.ms_carrito.dto.ProductoCatalogoEvento;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copia local del precio/stock OFICIAL de cada producto, alimentada por el topico compactado
 * "pedidos360.catalogo" (ms-catalogo). Corrige la observacion anterior: el precio ya no lo decide el navegador.
 */
@Component
public class PreciosCatalogo {

    private final Map<Long, ProductoCatalogoEvento> productos = new ConcurrentHashMap<>();

    public void actualizar(ProductoCatalogoEvento p) {
        productos.put(p.productoId(), p);
    }

    public void eliminar(Long productoId) {
        productos.remove(productoId);
    }

    public Optional<ProductoCatalogoEvento> buscar(Long productoId) {
        return Optional.ofNullable(productos.get(productoId));
    }

    public int conocidos() {
        return productos.size();
    }
}
