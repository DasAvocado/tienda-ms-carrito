package com.tienda.ms_carrito.repository;

import com.tienda.ms_carrito.model.CarritoInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarritoInfoRepository extends JpaRepository<CarritoInfo, String> {
}
