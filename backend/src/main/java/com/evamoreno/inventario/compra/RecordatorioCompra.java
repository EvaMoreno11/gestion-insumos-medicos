package com.evamoreno.inventario.compra;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.producto.Producto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Un producto agregado a mano a "Por comprar": un recordatorio para comprarlo aunque ningún pedido lo espere.
 * Hay uno como máximo por producto; la cantidad es opcional.
 */
@Entity
@Table(name = "recordatorio_compra")
public class RecordatorioCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, unique = true)
    private Producto producto;

    /** Cuántos comprar; null si todavía no se sabe. */
    private Integer cantidad;

    @Column(length = 300)
    private String nota;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected RecordatorioCompra() {
        // requerido por JPA
    }

    RecordatorioCompra(Producto producto) {
        this.producto = producto;
    }

    void cambiar(Integer cantidad, String nota) {
        this.cantidad = cantidad;
        String limpia = TextoBusqueda.limpiar(nota);
        this.nota = limpia == null || limpia.isEmpty() ? null : limpia;
    }

    public Producto getProducto() {
        return producto;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public String getNota() {
        return nota;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
