package com.evamoreno.inventario.proveedor;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.evamoreno.inventario.common.TextoBusqueda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un proveedor al que se le anotan deudas y pagos. Solo tiene nombre. */
@Entity
@Table(name = "proveedor")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    /** El nombre sin tildes, mayúsculas ni signos: "Distribuidora Pérez" = "distribuidora perez". */
    @Column(name = "nombre_comparable", nullable = false, length = 120)
    private String nombreComparable;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Proveedor() {
        // requerido por JPA
    }

    Proveedor(String nombre) {
        this.nombre = TextoBusqueda.limpiar(nombre);
        this.nombreComparable = comparable(nombre);
    }

    static String comparable(String nombre) {
        return TextoBusqueda.normalizar(nombre);
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
