package com.evamoreno.inventario.stock;

import java.math.BigDecimal;
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

    /** Entrada: costo de compra. Salida o ajuste: costo promedio del lote en ese momento. */
    @Column(name = "costo_unitario", precision = 12, scale = 4)
    private BigDecimal costoUnitario;

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
        this.costoUnitario = lote.getCostoPromedio();
    }

    /** Entrada: guarda el costo de compra de esas unidades. */
    static Movimiento entrada(Lote lote, int cantidad, BigDecimal costo, String nota) {
        Movimiento m = new Movimiento(lote, TipoMovimiento.ENTRADA, cantidad, nota);
        m.costoUnitario = costo;
        return m;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
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
