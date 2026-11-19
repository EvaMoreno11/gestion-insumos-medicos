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

/**
 * Un pedido de un cliente para uno de sus locales. Si se entrega incompleto, lo que falta
 * pasa a un pedido nuevo (el "saldo") que apunta al original con {@code origenId}.
 */
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

    /** Observaciones internas: no salen en la nota ni en la tabla. */
    @Column(length = 300)
    private String nota;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DocumentoPedido documento = DocumentoPedido.NOTA;

    /** Plazo de pago: null = aún no se anotó; 0 = contado; N = crédito a N días desde el despacho. */
    @Column(name = "plazo_dias")
    private Integer plazoDias;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha, id")
    private List<Pago> pagos = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha, id")
    private List<CorreccionPedido> correcciones = new ArrayList<>();

    /** Pedido original del que este es un saldo (lo que faltó entregar); null si no es un saldo. */
    @Column(name = "origen_id")
    private Long origenId;

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

    public Pedido(Cliente cliente, ClienteLocal local, BigDecimal porcentaje, String nota, DocumentoPedido documento) {
        this.cliente = cliente;
        this.local = local;
        this.porcentaje = porcentaje;
        this.nota = nota;
        this.documento = documento;
    }

    /**
     * Pedido nuevo para lo que faltó entregar: mismo cliente, local, porcentaje, observaciones y documento.
     * Apunta al pedido original (si este ya era un saldo, al mismo original).
     */
    static Pedido saldoDe(Pedido entregado) {
        Pedido saldo = new Pedido(entregado.cliente, entregado.local, entregado.porcentaje, entregado.nota,
                entregado.documento);
        saldo.origenId = entregado.idOriginal();
        return saldo;
    }

    /** Id del pedido original del grupo: el propio, o aquel del que este es un saldo. */
    public Long idOriginal() {
        return origenId != null ? origenId : id;
    }

    public boolean esSaldo() {
        return origenId != null;
    }

    /** Solo un borrador se puede modificar, despachar o anular. */
    public void exigirBorrador() {
        if (estado != EstadoPedido.BORRADOR) {
            throw new ConflictoException("El pedido ya está " + estado.name().toLowerCase()
                    + " y no se puede modificar");
        }
    }

    public void actualizar(ClienteLocal local, BigDecimal porcentaje, String nota, DocumentoPedido documento) {
        this.local = local;
        this.porcentaje = porcentaje;
        this.nota = nota;
        this.documento = documento;
    }

    /** @param numero número de la nota; null si se entrega "Solo tabla" (no consume la numeración) */
    public void marcarDespachado(String numero, Instant momento) {
        this.estado = EstadoPedido.DESPACHADO;
        this.numero = numero;
        this.despachadoEn = momento;
    }

    /** Los pagos y el plazo son de un pedido ya entregado. */
    public void exigirDespachado() {
        if (estado != EstadoPedido.DESPACHADO) {
            throw new ConflictoException("Solo se registran pagos de pedidos despachados");
        }
    }

    public void definirPlazo(Integer plazoDias) {
        this.plazoDias = plazoDias;
    }

    public Integer getPlazoDias() {
        return plazoDias;
    }

    public List<Pago> getPagos() {
        return pagos;
    }

    public List<CorreccionPedido> getCorrecciones() {
        return correcciones;
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

    public DocumentoPedido getDocumento() {
        return documento;
    }

    public Long getOrigenId() {
        return origenId;
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
