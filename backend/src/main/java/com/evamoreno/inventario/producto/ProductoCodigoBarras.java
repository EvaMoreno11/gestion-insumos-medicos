package com.evamoreno.inventario.producto;

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
 * Código de barras (EAN-13 u otro) de un producto. Identifica producto y presentación,
 * NO el lote: dos cajas de distinto lote tienen el mismo código.
 */
@Entity
@Table(name = "producto_codigo_barras")
public class ProductoCodigoBarras {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 50, unique = true)
    private String codigo;

    protected ProductoCodigoBarras() {
        // requerido por JPA
    }

    ProductoCodigoBarras(Producto producto, String codigo) {
        this.producto = producto;
        this.codigo = codigo;
    }

    public Long getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public String getCodigo() {
        return codigo;
    }
}
