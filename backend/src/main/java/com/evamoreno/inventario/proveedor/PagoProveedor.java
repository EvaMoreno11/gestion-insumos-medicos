package com.evamoreno.inventario.proveedor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import com.evamoreno.inventario.pedido.Pago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un pago hecho a un proveedor por una deuda. Una deuda puede pagarse en partes. */
@Entity
@Table(name = "pago_proveedor")
public class PagoProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deuda_id", nullable = false)
    private DeudaProveedor deuda;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Pago.Medio medio;

    @Column(length = 200)
    private String nota;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected PagoProveedor() {
        // requerido por JPA
    }

    PagoProveedor(DeudaProveedor deuda, LocalDate fecha, BigDecimal monto, Pago.Medio medio, String nota) {
        this.deuda = deuda;
        this.fecha = fecha;
        this.monto = monto;
        this.medio = medio;
        this.nota = nota;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public Pago.Medio getMedio() {
        return medio;
    }

    public String getNota() {
        return nota;
    }
}
