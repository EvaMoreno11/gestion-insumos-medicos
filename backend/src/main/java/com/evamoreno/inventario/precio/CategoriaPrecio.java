package com.evamoreno.inventario.precio;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Etiqueta interna para un porcentaje de incremento sobre el costo: A = 10 %, B = 15 %, C = 20 %.
 * Es una tabla (y no un valor fijo en el código) para poder agregar categorías sin programar.
 */
@Entity
@Table(name = "categoria_precio")
public class CategoriaPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, unique = true)
    private String letra;

    @Column(nullable = false, precision = 5, scale = 2, unique = true)
    private BigDecimal porcentaje;

    protected CategoriaPrecio() {
        // requerido por JPA
    }

    public CategoriaPrecio(String letra, BigDecimal porcentaje) {
        this.letra = letra;
        this.porcentaje = porcentaje;
    }

    public void actualizar(String letra, BigDecimal porcentaje) {
        this.letra = letra;
        this.porcentaje = porcentaje;
    }

    public Long getId() {
        return id;
    }

    public String getLetra() {
        return letra;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }
}
