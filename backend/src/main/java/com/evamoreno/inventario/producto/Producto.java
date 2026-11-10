package com.evamoreno.inventario.producto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.evamoreno.inventario.categoria.Categoria;
import com.evamoreno.inventario.common.TextoBusqueda;

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

/**
 * Un producto del catálogo. Cada marca/presentación es un producto distinto:
 * "Jeringa 5 ml · Marca A · Caja x 100" ≠ "Jeringa 5 ml · Marca B · Caja x 100".
 * El stock se manejará por lotes (Fase 2).
 */
@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String marca;

    @Column(nullable = false, length = 80)
    private String presentacion;

    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;

    @Column(name = "stock_minimo", nullable = false)
    private int stockMinimo;

    /** Costo de compra. El precio de venta se calcula sumándole el porcentaje del cliente. */
    @Column(precision = 12, scale = 4)
    private BigDecimal costo;

    @Column(nullable = false)
    private boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ProductoCodigoBarras> codigosBarras = new ArrayList<>();

    @Column(name = "texto_busqueda", nullable = false, length = 600)
    private String textoBusqueda;

    @Column(name = "clave_unica", nullable = false, length = 400, unique = true)
    private String claveUnica;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Producto() {
        // requerido por JPA
    }

    public Producto(String codigo, String nombre, String marca, String presentacion, String unidadMedida,
            int stockMinimo, Categoria categoria) {
        this.codigo = codigo;
        actualizarDatos(nombre, marca, presentacion, unidadMedida, stockMinimo, categoria);
    }

    public void actualizarDatos(String nombre, String marca, String presentacion, String unidadMedida,
            int stockMinimo, Categoria categoria) {
        this.nombre = nombre;
        this.marca = marca;
        this.presentacion = presentacion;
        this.unidadMedida = unidadMedida;
        this.stockMinimo = stockMinimo;
        this.categoria = categoria;
        recalcularCamposDerivados();
    }

    /**
     * Deja exactamente los códigos de barras indicados: quita los que sobran y agrega los nuevos.
     * (No se borra todo y se vuelve a insertar, porque Hibernate inserta antes de borrar y
     * chocaría con la restricción UNIQUE del código.)
     */
    public void reemplazarCodigosBarras(Collection<String> nuevos) {
        Set<String> deseados = Set.copyOf(nuevos);
        codigosBarras.removeIf(cb -> !deseados.contains(cb.getCodigo()));
        List<String> actuales = codigosBarras.stream().map(ProductoCodigoBarras::getCodigo).toList();
        nuevos.stream()
                .filter(codigoBarras -> !actuales.contains(codigoBarras))
                .forEach(codigoBarras -> codigosBarras.add(new ProductoCodigoBarras(this, codigoBarras)));
    }

    public static String calcularClaveUnica(String nombre, String marca, String presentacion) {
        return TextoBusqueda.normalizar(nombre) + "|" + TextoBusqueda.normalizar(marca) + "|"
                + TextoBusqueda.normalizar(presentacion);
    }

    private void recalcularCamposDerivados() {
        // Espacios al inicio y al final para poder buscar "inicio de palabra" con LIKE '% palabra%'
        this.textoBusqueda = " " + TextoBusqueda.normalizar(
                String.join(" ", codigo, nombre, marca, presentacion)) + " ";
        this.claveUnica = calcularClaveUnica(nombre, marca, presentacion);
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
        recalcularCamposDerivados();
    }

    public void setCosto(BigDecimal costo) {
        this.costo = costo;
    }

    public BigDecimal getCosto() {
        return costo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getMarca() {
        return marca;
    }

    public String getPresentacion() {
        return presentacion;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public int getStockMinimo() {
        return stockMinimo;
    }

    public boolean isActivo() {
        return activo;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public List<ProductoCodigoBarras> getCodigosBarras() {
        return codigosBarras;
    }

    public String getTextoBusqueda() {
        return textoBusqueda;
    }

    public String getClaveUnica() {
        return claveUnica;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
