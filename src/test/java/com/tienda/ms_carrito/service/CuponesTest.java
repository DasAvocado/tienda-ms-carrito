package com.tienda.ms_carrito.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CuponesTest {

    @Test
    void bienvenidaDescuentaDiezPorCiento() {
        Cupones.Resultado r = Cupones.evaluar(" bienvenida10 ", 30000);
        assertTrue(r.valido());
        assertEquals(3000, r.descuento(), 0.001);
    }

    @Test
    void frenosRequiereMinimo() {
        assertFalse(Cupones.evaluar("FRENOS15", 45000).valido());
        assertEquals(7500, Cupones.evaluar("FRENOS15", 50000).descuento(), 0.001);
    }

    @Test
    void envioGratisYCuponInexistente() {
        assertTrue(Cupones.evaluar("ENVIOGRATIS", 1000).envioGratis());
        assertFalse(Cupones.evaluar("NOEXISTE", 1000).valido());
        assertEquals(6990, Cupones.costoEnvio("express"), 0.001);
        assertThrows(IllegalArgumentException.class, () -> Cupones.costoEnvio("DRON"));
    }
}
