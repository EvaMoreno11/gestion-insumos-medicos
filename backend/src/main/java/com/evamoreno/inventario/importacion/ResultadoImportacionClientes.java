package com.evamoreno.inventario.importacion;

import java.util.List;

import com.evamoreno.inventario.importacion.ResultadoImportacion.Observacion;

/**
 * Reporte de una importación de clientes.
 *
 * @param importado          true si los datos se guardaron (no era una validación y no hubo errores)
 * @param soloValidar        true si fue una prueba: se revisó todo pero no se guardó nada
 * @param clientesExistentes clientes del archivo que ya estaban registrados (no se modifican)
 * @param errores            problemas que impiden importar; si hay alguno, no se guarda NADA
 */
public record ResultadoImportacionClientes(
        boolean importado,
        boolean soloValidar,
        int filasLeidas,
        int clientesCreados,
        int localesCreados,
        int clientesExistentes,
        List<Observacion> errores,
        List<Observacion> advertencias) {
}
