package com.tienda.ms_carrito.service;

import com.tienda.ms_carrito.dto.CheckoutEvent;
import com.tienda.ms_carrito.dto.PagoAprobadoEvent;
import com.tienda.ms_carrito.messaging.CheckoutPublisher;
import com.tienda.ms_carrito.messaging.MensajeInvalidoException;
import com.tienda.ms_carrito.messaging.NavegacionPublisher;
import com.tienda.ms_carrito.messaging.TransaccionPublisher;
import com.tienda.ms_carrito.model.CarritoInfo;
import com.tienda.ms_carrito.model.ItemCarrito;
import com.tienda.ms_carrito.repository.CarritoInfoRepository;
import com.tienda.ms_carrito.repository.ItemCarritoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CarritoServiceTest {

    private ItemCarritoRepository items;
    private CarritoInfoRepository info;
    private CheckoutPublisher checkout;
    private CarritoService service;
    private PreciosCatalogo precios;

    @BeforeEach
    void preparar() {
        items = mock(ItemCarritoRepository.class);
        info = mock(CarritoInfoRepository.class);
        checkout = mock(CheckoutPublisher.class);
        precios = new PreciosCatalogo();
        service = new CarritoService(items, info, checkout, mock(NavegacionPublisher.class), mock(TransaccionPublisher.class), precios);

        ItemCarrito filtro = new ItemCarrito();
        filtro.setUsuarioId("u1");
        filtro.setProductoId(1L);
        filtro.setNombre("Filtro");
        filtro.setPrecio(15000.0);
        filtro.setCantidad(2);
        when(items.findByUsuarioIdOrderByIdAsc("u1")).thenReturn(List.of(filtro));
        CarritoInfo ci = new CarritoInfo("u1");
        ci.setCupon("BIENVENIDA10");
        when(info.findById("u1")).thenReturn(Optional.of(ci));
    }

    @Test
    void resumenAplicaCuponYEnvio() {
        var r = service.resumen("u1");
        assertEquals(30000, r.subtotal(), 0.001);
        assertEquals(3000, r.descuento(), 0.001);
        assertEquals(3990, r.costoEnvio(), 0.001);
        assertEquals(30990, r.total(), 0.001);
    }

    @Test
    void pagoAprobadoPublicaCheckoutConTotales() {
        service.cerrarCompraPagada(new PagoAprobadoEvent(7L, "u1", "a@b.cl", "Ana", 30990, "WEBPAY", "2026-11-24T10:00:00Z"));

        ArgumentCaptor<CheckoutEvent> ev = ArgumentCaptor.forClass(CheckoutEvent.class);
        verify(checkout).publicar(ev.capture());
        assertEquals(7L, ev.getValue().pagoId());
        assertEquals(30990, ev.getValue().total(), 0.001);
        verify(items).deleteByUsuarioId("u1");
    }

    @Test
    void montoDistintoVaALaDlq() {
        assertThrows(MensajeInvalidoException.class, () ->
                service.cerrarCompraPagada(new PagoAprobadoEvent(8L, "u1", "a@b.cl", "Ana", 100, "TARJETA_SIMULADA", "x")));
        verify(checkout, never()).publicar(any());
    }

    @Test
    void pagoRepetidoConCarritoVacioSeIgnora() {
        when(items.findByUsuarioIdOrderByIdAsc("u2")).thenReturn(List.of());
        service.cerrarCompraPagada(new PagoAprobadoEvent(9L, "u2", "a@b.cl", "Ana", 100, "WEBPAY", "x"));
        verify(checkout, never()).publicar(any());
    }

    @Test
    void usaElPrecioOficialDelCatalogoYNoElDelNavegador() {
        precios.actualizar(new com.tienda.ms_carrito.dto.ProductoCatalogoEvento("e1", 5L, "Bateria 12V", "Electrico", 85000.0, 10, "x"));
        when(items.findByUsuarioIdAndProductoId("u1", 5L)).thenReturn(Optional.empty());
        when(items.save(any(ItemCarrito.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemCarrito item = service.agregar("u1", new com.tienda.ms_carrito.dto.ItemRequest(5L, "Bateria", 1.0, 1));

        assertEquals(85000.0, item.getPrecio(), 0.001);
        assertEquals("Bateria 12V", item.getNombre());
    }
}
