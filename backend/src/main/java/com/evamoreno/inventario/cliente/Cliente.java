package com.evamoreno.inventario.cliente;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.evamoreno.inventario.common.TextoBusqueda;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Un cliente (botica, clínica, consultorio...). Tiene uno o más locales de entrega
 * y un porcentaje que se suma al costo para calcular sus precios.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(name = "ruc_dni", length = 11)
    private String rucDni;

    /** Incremento sobre el costo (15 = 15 %). Null = aún no definido. */
    @Column(precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Column(length = 300)
    private String observaciones;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ClienteLocal> locales = new ArrayList<>();

    @Column(name = "clave_unica", nullable = false, length = 250, unique = true)
    private String claveUnica;

    @Column(name = "texto_busqueda", nullable = false, length = 300)
    private String textoBusqueda;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Cliente() {
        // requerido por JPA
    }

    public Cliente(String nombre, String rucDni, BigDecimal porcentaje, String observaciones) {
        actualizarDatos(nombre, rucDni, porcentaje, observaciones);
    }

    public void actualizarDatos(String nombre, String rucDni, BigDecimal porcentaje, String observaciones) {
        this.nombre = nombre;
        this.rucDni = rucDni;
        this.porcentaje = porcentaje;
        this.observaciones = observaciones;
        this.claveUnica = calcularClaveUnica(nombre);
        // Espacios al inicio y al final para buscar por "inicio de palabra" con LIKE '% palabra%'
        this.textoBusqueda = " " + TextoBusqueda.normalizar(nombre + " " + (rucDni == null ? "" : rucDni)) + " ";
    }

    public static String calcularClaveUnica(String nombre) {
        return TextoBusqueda.normalizar(nombre);
    }

    public ClienteLocal agregarLocal(String nombre, String direccion, String contacto, String telefono) {
        ClienteLocal local = new ClienteLocal(this, nombre, direccion, contacto, telefono);
        locales.add(local);
        return local;
    }

    /** Guarda el porcentaje elegido al registrar un pedido (p. ej. en el primer pedido del cliente). */
    public void definirPorcentaje(BigDecimal porcentaje) {
        this.porcentaje = porcentaje;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRucDni() {
        return rucDni;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public boolean isActivo() {
        return activo;
    }

    public List<ClienteLocal> getLocales() {
        return locales;
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
