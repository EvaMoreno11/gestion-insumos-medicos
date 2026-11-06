package com.evamoreno.inventario.common;

/** La petición no se puede procesar tal como llegó, p. ej. un archivo que no es Excel (se responde 400). */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }

    public SolicitudInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
