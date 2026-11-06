package com.evamoreno.inventario.common;

/** Se lanza cuando la operación choca con datos existentes, p. ej. un duplicado (se responde 409). */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
