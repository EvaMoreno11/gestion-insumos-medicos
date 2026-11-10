package com.evamoreno.inventario.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Punto de entrega de un cliente, con su dirección y su persona de contacto.
 * Si el cliente tiene un solo local, el nombre queda vacío.
 */
@Entity
@Table(name = "cliente_local")
public class ClienteLocal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(length = 80)
    private String nombre;

    @Column(length = 200)
    private String direccion;

    @Column(length = 80)
    private String contacto;

    @Column(length = 30)
    private String telefono;

    protected ClienteLocal() {
        // requerido por JPA
    }

    ClienteLocal(Cliente cliente, String nombre, String direccion, String contacto, String telefono) {
        this.cliente = cliente;
        actualizar(nombre, direccion, contacto, telefono);
    }

    void actualizar(String nombre, String direccion, String contacto, String telefono) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.contacto = contacto;
        this.telefono = telefono;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getContacto() {
        return contacto;
    }

    public String getTelefono() {
        return telefono;
    }
}
