package com.evamoreno.inventario.importacion;

import java.util.List;

/**
 * Reporte de una importación de inventario.
 *
 * @param importado    true si los datos se guardaron (solo si no es una validación y no hubo errores)
 * @param soloValidar  true si fue una prueba: se revisó todo pero no se guardó nada
 * @param errores      problemas que impiden importar; si hay alguno, no se guarda NADA
 * @param advertencias avisos que no impiden importar (p. ej. el producto ya tenía stock)
 */
public record ResultadoImportacion(
        boolean importado,
        boolean soloValidar,
        int filasLeidas,
        int productosCreados,
        int productosExistentes,
        int categoriasCreadas,
        int entradasRegistradas,
        long unidadesIngresadas,
        List<Observacion> errores,
        List<Observacion> advertencias) {

    /** Un error o advertencia. fila = número de fila en Excel (0 si es del archivo completo). */
    public record Observacion(int fila, String mensaje) {
    }
}
