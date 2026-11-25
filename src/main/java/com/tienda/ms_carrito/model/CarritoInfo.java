package com.tienda.ms_carrito.model;

import jakarta.persistence.*;

/** Datos del carrito que no son items: cupon aplicado y tipo de envio elegido (uno por usuario). */
@Entity
@Table(name = "carritos_info")
public class CarritoInfo {

    @Id
    @Column(name = "usuario_id")
    private String usuarioId;

    private String cupon;

    @Column(name = "tipo_envio")
    private String tipoEnvio;

    public CarritoInfo() {}

    public CarritoInfo(String usuarioId) {
        this.usuarioId = usuarioId;
        this.tipoEnvio = "ESTANDAR";
    }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getCupon() { return cupon; }
    public void setCupon(String cupon) { this.cupon = cupon; }

    public String getTipoEnvio() { return tipoEnvio; }
    public void setTipoEnvio(String tipoEnvio) { this.tipoEnvio = tipoEnvio; }
}
