package com.tienda.ms_carrito.repository;

import com.tienda.ms_carrito.model.ItemCarrito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemCarritoRepository extends JpaRepository<ItemCarrito, Long> {

    List<ItemCarrito> findByUsuarioIdOrderByIdAsc(String usuarioId);

    Optional<ItemCarrito> findByUsuarioIdAndProductoId(String usuarioId, Long productoId);

    Optional<ItemCarrito> findByIdAndUsuarioId(Long id, String usuarioId);

    void deleteByUsuarioId(String usuarioId);
}
