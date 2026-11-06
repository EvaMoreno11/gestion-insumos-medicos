package com.evamoreno.inventario.stock;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

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
 * Un lote de un producto: número de lote, fecha de vencimiento y cuántas unidades quedan.
 * La cantidad solo cambia a través de MovimientoService (los métodos que la modifican son
 * package-private), así todo cambio de stock queda registrado como un movimiento.
 */
@Entity
@Table(name = "lote")
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "numero_lote", length = 50)
    private String numeroLote;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "cantidad_actual", nullable = false)
    private int cantidadActual;

    @CreationTimestamp
    @Column(name = "fecha_ingreso", nullable = false, updatable = false)
    private Instant fechaIngreso;

    protected Lote() {
        // requerido por JPA
    }

    public Lote(Producto producto, String numeroLote, LocalDate fechaVencimiento) {
        this.producto = producto;
        this.numeroLote = numeroLote;
        this.fechaVencimiento = fechaVencimiento;
    }

    /** Vencido = su fecha de vencimiento ya pasó. Se puede usar HASTA el mismo día de vencimiento. */
    public boolean estaVencido(LocalDate hoy) {
        return fechaVencimiento != null && fechaVencimiento.isBefore(hoy);
    }

    void sumar(int unidades) {
        cantidadActual += unidades;
    }

    void restar(int unidades) {
        if (unidades > cantidadActual) {
            throw new IllegalStateException("El lote " + id + " no tiene " + unidades + " unidades");
        }
        cantidadActual -= unidades;
    }

    void fijarCantidad(int cantidadReal) {
        cantidadActual = cantidadReal;
    }

    public Long getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public String getNumeroLote() {
        return numeroLote;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public int getCantidadActual() {
        return cantidadActual;
    }

    public Instant getFechaIngreso() {
        return fechaIngreso;
    }
}
