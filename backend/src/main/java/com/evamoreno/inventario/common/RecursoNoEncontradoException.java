package com.evamoreno.inventario.common;

/** Se lanza cuando no existe lo que se busca (se responde 404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
