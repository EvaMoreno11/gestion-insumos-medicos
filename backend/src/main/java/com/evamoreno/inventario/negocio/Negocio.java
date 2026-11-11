package com.evamoreno.inventario.negocio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Datos del negocio para el encabezado de la nota de pedido (una sola fila).
 * Viven en la base de datos y se editan desde la app: nunca están en el código.
 */
@Entity
@Table(name = "negocio")
public class Negocio {

    @Id
    private Short id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 200)
    private String direccion;

    @Column(length = 40)
    private String telefono;

    protected Negocio() {
        // requerido por JPA
    }

    void actualizar(String nombre, String direccion, String telefono) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.telefono = telefono;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getTelefono() {
        return telefono;
    }
}
