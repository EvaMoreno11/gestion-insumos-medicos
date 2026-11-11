package com.evamoreno.inventario.negocio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.common.TextoBusqueda;

/** Lectura y edición de la única fila de datos del negocio. */
@Service
@Transactional
public class NegocioService {

    private static final short ID = 1;

    private final NegocioRepository negocios;

    public NegocioService(NegocioRepository negocios) {
        this.negocios = negocios;
    }

    @Transactional(readOnly = true)
    public NegocioDto obtener() {
        return NegocioDto.de(negocios.findById(ID).orElseThrow());
    }

    public NegocioDto actualizar(NegocioDto datos) {
        Negocio negocio = negocios.findById(ID).orElseThrow();
        negocio.actualizar(TextoBusqueda.limpiar(datos.nombre()), limpiar(datos.direccion()),
                limpiar(datos.telefono()));
        return NegocioDto.de(negocio);
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : TextoBusqueda.limpiar(texto);
    }
}
