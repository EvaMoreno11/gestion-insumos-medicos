package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

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

/** Un pago recibido por un pedido despachado. Un pedido puede tener varios pagos parciales. */
@Entity
@Table(name = "pago")
public class Pago {

    /** Cómo pagó el cliente. */
    public enum Medio {
        EFECTIVO, YAPE, PLIN, TRANSFERENCIA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Medio medio;

    @Column(length = 200)
    private String nota;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Pago() {
        // requerido por JPA
    }

    Pago(Pedido pedido, LocalDate fecha, BigDecimal monto, Medio medio, String nota) {
        this.pedido = pedido;
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

    public Medio getMedio() {
        return medio;
    }

    public String getNota() {
        return nota;
    }
}
