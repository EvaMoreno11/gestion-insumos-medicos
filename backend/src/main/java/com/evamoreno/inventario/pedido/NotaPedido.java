package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Contenido de una nota de pedido, listo para dibujarse en Excel o en PDF.
 * No incluye costo, porcentaje ni categoría del cliente: son datos internos.
 *
 * @param numero   número de la nota (0001-007323); null si el pedido aún es un borrador
 * @param contacto persona y teléfono de contacto del local, ya unidos
 */
public record NotaPedido(String negocio, String negocioDireccion, String negocioTelefono, String numero,
        String cliente, String local, String puntoLlegada, String contacto, LocalDate fecha, List<Fila> filas,
        BigDecimal total, String nombreArchivo) {

    /**
     * Una fila de la tabla. Cuando un ítem sale de varios lotes hay una fila por lote:
     * solo la primera lleva número de ítem.
     *
     * @param vencimiento mes y año (03/2027) o null
     */
    public record Fila(Integer numero, String descripcion, String presentacion, String laboratorio, String lote,
            String vencimiento, int cantidad, BigDecimal precio, BigDecimal total) {
    }

    public boolean esBorrador() {
        return numero == null;
    }

    /** "Av. Principal 123 · Tel. 987 654 321" (solo lo que exista). */
    public String negocioContacto() {
        String telefono = negocioTelefono == null ? null : "Tel. " + negocioTelefono;
        return unir(negocioDireccion, telefono);
    }

    static String unir(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null ? "" : b;
        }
        return b == null || b.isBlank() ? a : a + " · " + b;
    }
}
