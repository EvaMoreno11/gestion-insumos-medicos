package com.evamoreno.inventario.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** Promedio ponderado del costo de las unidades del lote; null si se desconoce. */
    @Column(name = "costo_promedio", precision = 12, scale = 4)
    private BigDecimal costoPromedio;

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

    /** Vencido = ya pasó su mes de vencimiento (la fecha guardada es el último día de ese mes). */
    public boolean estaVencido(LocalDate hoy) {
        return fechaVencimiento != null && fechaVencimiento.isBefore(hoy);
    }

    void sumar(int unidades) {
        cantidadActual += unidades;
    }

    /**
     * Entrada con costo: el promedio se pondera con las unidades que ya hay.
     * 10 a S/ 5.00 + 10 a S/ 5.50 = 20 a S/ 5.25. Si el lote estaba vacío o sin costo, toma el nuevo.
     */
    void sumar(int unidades, BigDecimal costo) {
        if (costo != null) {
            costoPromedio = costoPromedio == null || cantidadActual <= 0
                    ? costo
                    : costoPromedio.multiply(BigDecimal.valueOf(cantidadActual))
                            .add(costo.multiply(BigDecimal.valueOf(unidades)))
                            .divide(BigDecimal.valueOf((long) cantidadActual + unidades), 4, RoundingMode.HALF_UP);
        }
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

    /** Corrige el número o el vencimiento (un error al registrarlo). Las unidades no cambian. */
    void corregir(String numeroLote, LocalDate fechaVencimiento) {
        this.numeroLote = numeroLote;
        this.fechaVencimiento = fechaVencimiento;
    }

    /**
     * Une a este lote otro del mismo producto que en realidad es el mismo (p. ej. se escribió dos veces):
     * suma sus unidades, pondera el costo, y el otro queda vacío.
     */
    void absorber(Lote otro) {
        if (otro.cantidadActual > 0) {
            sumar(otro.cantidadActual, otro.costoPromedio);
        }
        otro.cantidadActual = 0;
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

    public BigDecimal getCostoPromedio() {
        return costoPromedio;
    }

    public int getCantidadActual() {
        return cantidadActual;
    }

    public Instant getFechaIngreso() {
        return fechaIngreso;
    }
}
