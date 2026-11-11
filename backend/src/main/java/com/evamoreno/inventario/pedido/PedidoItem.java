package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;

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
 * Una línea del pedido. Si {@code producto} es null, es un ítem agregado a mano
 * (algo que aún no está en el catálogo): aparece en la nota pero no mueve stock.
 */
@Entity
@Table(name = "pedido_item")
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(length = 80)
    private String presentacion;

    @Column(length = 80)
    private String marca;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario", precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    /** true = el precio lo escribió el usuario; no se recalcula al cambiar el porcentaje. */
    @Column(name = "precio_manual", nullable = false)
    private boolean precioManual;

    protected PedidoItem() {
        // requerido por JPA
    }

    /** Ítem de un producto del catálogo: copia su nombre, presentación y marca. */
    static PedidoItem deProducto(Pedido pedido, Producto producto, int cantidad) {
        PedidoItem item = new PedidoItem();
        item.pedido = pedido;
        item.producto = producto;
        item.descripcion = producto.getNombre();
        item.presentacion = producto.getPresentacion();
        item.marca = producto.getMarca();
        item.cantidad = cantidad;
        return item;
    }

    /** Ítem agregado a mano (no está en el catálogo). */
    static PedidoItem manual(Pedido pedido, String descripcion, String presentacion, String marca, int cantidad) {
        PedidoItem item = new PedidoItem();
        item.pedido = pedido;
        item.descripcion = descripcion;
        item.presentacion = presentacion;
        item.marca = marca;
        item.cantidad = cantidad;
        return item;
    }

    public boolean esManual() {
        return producto == null;
    }

    void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    void setDatosManuales(String descripcion, String presentacion, String marca) {
        this.descripcion = descripcion;
        this.presentacion = presentacion;
        this.marca = marca;
    }

    void fijarPrecio(BigDecimal precio, boolean manual) {
        this.precioUnitario = precio;
        this.precioManual = manual;
    }

    /** cantidad × precio; null si aún no tiene precio. */
    public BigDecimal total() {
        return precioUnitario == null ? null : precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public Producto getProducto() {
        return producto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getPresentacion() {
        return presentacion;
    }

    public String getMarca() {
        return marca;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public boolean isPrecioManual() {
        return precioManual;
    }
}
