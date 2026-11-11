package com.evamoreno.inventario.pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Numeración de la nota de pedido: una sola fila con la serie y el siguiente número a usar. */
@Entity
@Table(name = "numeracion_nota")
public class NumeracionNota {

    static final short ID = 1;

    @Id
    private Short id;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(nullable = false)
    private long siguiente;

    protected NumeracionNota() {
        // requerido por JPA
    }

    /** Devuelve el número a usar (0001-007323) y avanza el contador. */
    String tomarNumero() {
        String numero = formatear(serie, siguiente);
        siguiente++;
        return numero;
    }

    void configurar(String serie, long siguiente) {
        this.serie = serie;
        this.siguiente = siguiente;
    }

    static String formatear(String serie, long numero) {
        return "%s-%06d".formatted(serie, numero);
    }

    public String getSerie() {
        return serie;
    }

    public long getSiguiente() {
        return siguiente;
    }
}
