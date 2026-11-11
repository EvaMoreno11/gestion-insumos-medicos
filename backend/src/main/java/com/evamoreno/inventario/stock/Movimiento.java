package com.evamoreno.inventario.stock;

import java.time.Instant;

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

/** Registro inmutable de un cambio de stock en un lote (historial / kárdex). */
@Entity
@Table(name = "movimiento")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "stock_resultante", nullable = false)
    private int stockResultante;

    @Column(length = 300)
    private String nota;

    /** Ítem de pedido que originó esta salida (null si no viene de un pedido). */
    @Column(name = "pedido_item_id")
    private Long pedidoItemId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant fecha;

    protected Movimiento() {
        // requerido por JPA
    }

    Movimiento(Lote lote, TipoMovimiento tipo, int cantidad, String nota, Long pedidoItemId) {
        this(lote, tipo, cantidad, nota);
        this.pedidoItemId = pedidoItemId;
    }

    Movimiento(Lote lote, TipoMovimiento tipo, int cantidad, String nota) {
        this.lote = lote;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.stockResultante = lote.getCantidadActual();
        this.nota = nota;
    }

    public Long getId() {
        return id;
    }

    public Lote getLote() {
        return lote;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getStockResultante() {
        return stockResultante;
    }

    public Long getPedidoItemId() {
        return pedidoItemId;
    }

    public String getNota() {
        return nota;
    }

    public Instant getFecha() {
        return fecha;
    }
}
