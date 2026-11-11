package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.evamoreno.inventario.cliente.Cliente;
import com.evamoreno.inventario.cliente.ClienteLocal;
import com.evamoreno.inventario.common.ConflictoException;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Un pedido de un cliente para uno de sus locales. */
@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_local_id", nullable = false)
    private ClienteLocal local;

    /** Porcentaje sobre el costo con el que se calculan los precios de ESTE pedido. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoPedido estado = EstadoPedido.BORRADOR;

    /** Número de la nota de pedido (0001-007323). Se asigna al despachar. */
    @Column(length = 20, unique = true)
    private String numero;

    @Column(length = 300)
    private String nota;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50) // al listar pedidos, carga los ítems de varios pedidos en una sola consulta
    private List<PedidoItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "despachado_en")
    private Instant despachadoEn;

    protected Pedido() {
        // requerido por JPA
    }

    public Pedido(Cliente cliente, ClienteLocal local, BigDecimal porcentaje, String nota) {
        this.cliente = cliente;
        this.local = local;
        this.porcentaje = porcentaje;
        this.nota = nota;
    }

    /** Solo un borrador se puede modificar, despachar o anular. */
    public void exigirBorrador() {
        if (estado != EstadoPedido.BORRADOR) {
            throw new ConflictoException("El pedido ya está " + estado.name().toLowerCase()
                    + " y no se puede modificar");
        }
    }

    public void actualizar(ClienteLocal local, BigDecimal porcentaje, String nota) {
        this.local = local;
        this.porcentaje = porcentaje;
        this.nota = nota;
    }

    public void marcarDespachado(String numero, Instant momento) {
        this.estado = EstadoPedido.DESPACHADO;
        this.numero = numero;
        this.despachadoEn = momento;
    }

    public void anular() {
        this.estado = EstadoPedido.ANULADO;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public ClienteLocal getLocal() {
        return local;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public String getNumero() {
        return numero;
    }

    public String getNota() {
        return nota;
    }

    public List<PedidoItem> getItems() {
        return items;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public Instant getDespachadoEn() {
        return despachadoEn;
    }
}
