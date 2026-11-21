package com.evamoreno.inventario.proveedor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Lo que se le debe a un proveedor por una compra, y los pagos que se le van haciendo. */
@Entity
@Table(name = "deuda_proveedor")
public class DeudaProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(length = 200)
    private String nota;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @OneToMany(mappedBy = "deuda", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha, id")
    private List<PagoProveedor> pagos = new ArrayList<>();

    protected DeudaProveedor() {
        // requerido por JPA
    }

    DeudaProveedor(Proveedor proveedor, LocalDate fecha, BigDecimal monto, String nota) {
        corregir(proveedor, fecha, monto, nota);
    }

    void corregir(Proveedor proveedor, LocalDate fecha, BigDecimal monto, String nota) {
        this.proveedor = proveedor;
        this.fecha = fecha;
        this.monto = monto;
        this.nota = nota;
    }

    BigDecimal pagado() {
        return pagos.stream().map(PagoProveedor::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal saldo() {
        return monto.subtract(pagado()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Día del último pago, si ya está pagada del todo; si no, null. */
    LocalDate pagadaEl() {
        return saldo().signum() > 0 ? null
                : pagos.stream().map(PagoProveedor::getFecha).max(Comparator.naturalOrder()).orElse(fecha);
    }

    void registrarPago(PagoProveedor pago) {
        pagos.add(pago);
    }

    public Long getId() {
        return id;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getNota() {
        return nota;
    }

    public List<PagoProveedor> getPagos() {
        return pagos;
    }
}
