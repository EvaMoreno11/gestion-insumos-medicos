package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Una corrección hecha a un pedido despachado: cuándo, por qué y qué cambió. Solo para uso interno. */
@Entity
@Table(name = "pedido_correccion")
public class CorreccionPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(nullable = false)
    private Instant fecha;

    @Column(nullable = false, length = 300)
    private String motivo;

    /** Una línea por cambio. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "total_anterior", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAnterior;

    @Column(name = "total_nuevo", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalNuevo;

    protected CorreccionPedido() {
        // requerido por JPA
    }

    CorreccionPedido(Pedido pedido, Instant fecha, String motivo, String detalle, BigDecimal totalAnterior,
            BigDecimal totalNuevo) {
        this.pedido = pedido;
        this.fecha = fecha;
        this.motivo = motivo;
        this.detalle = detalle;
        this.totalAnterior = totalAnterior;
        this.totalNuevo = totalNuevo;
    }

    public Long getId() {
        return id;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getDetalle() {
        return detalle;
    }

    public BigDecimal getTotalAnterior() {
        return totalAnterior;
    }

    public BigDecimal getTotalNuevo() {
        return totalNuevo;
    }
}
