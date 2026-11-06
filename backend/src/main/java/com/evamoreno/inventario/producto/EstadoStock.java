package com.evamoreno.inventario.producto;

/** Semáforo del stock, para mostrar colores en la app. */
public enum EstadoStock {
    /** No hay unidades disponibles (rojo). */
    AGOTADO,
    /** Hay unidades, pero igual o menos que el stock mínimo (amarillo). */
    BAJO,
    /** Por encima del stock mínimo (verde). */
    NORMAL;

    public static EstadoStock de(int disponible, int stockMinimo) {
        if (disponible <= 0) {
            return AGOTADO;
        }
        return disponible <= stockMinimo ? BAJO : NORMAL;
    }
}
