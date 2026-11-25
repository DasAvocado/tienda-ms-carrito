package com.tienda.ms_carrito.service;

import java.util.Locale;
import java.util.Map;

/** Reglas de cupones y costos de envio (logica pura, sin BD ni mensajeria: facil de probar). */
public final class Cupones {

    private Cupones() {}

    public static final Map<String, Double> COSTO_ENVIO = Map.of(
            "RETIRO", 0.0,
            "ESTANDAR", 3990.0,
            "EXPRESS", 6990.0);

    public record Resultado(boolean valido, double descuento, boolean envioGratis, String descripcion) {}

    public static String normalizar(String codigo) {
        return codigo == null ? null : codigo.trim().toUpperCase(Locale.ROOT);
    }

    public static Resultado evaluar(String codigo, double subtotal) {
        String c = normalizar(codigo);
        if (c == null || c.isEmpty()) return new Resultado(false, 0, false, null);
        return switch (c) {
            case "BIENVENIDA10" -> new Resultado(true, redondear(subtotal * 0.10), false, "10% de descuento");
            case "FRENOS15" -> subtotal >= 50000
                    ? new Resultado(true, redondear(subtotal * 0.15), false, "15% en compras sobre $50.000")
                    : new Resultado(false, 0, false, "FRENOS15 requiere un subtotal de $50.000 o mas");
            case "ENVIOGRATIS" -> new Resultado(true, 0, true, "Envio gratis");
            default -> new Resultado(false, 0, false, "Cupon no existe");
        };
    }

    public static double costoEnvio(String tipo) {
        Double costo = COSTO_ENVIO.get(tipo == null ? "ESTANDAR" : tipo.toUpperCase(Locale.ROOT));
        if (costo == null) throw new IllegalArgumentException("Tipo de envio invalido: " + tipo);
        return costo;
    }

    private static double redondear(double v) {
        return Math.round(v);
    }
}
