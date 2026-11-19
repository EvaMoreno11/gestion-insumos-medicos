package com.evamoreno.inventario.categoria;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param productos cuántos productos tiene (activos o no). Solo se puede eliminar una categoría con 0.
 */
public record CategoriaResponse(
        Long id,
        String nombre,
        @Schema(description = "Cuántos productos tiene; solo se puede eliminar con 0", example = "12")
        long productos) {

    static CategoriaResponse de(Categoria categoria, long productos) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre(), productos);
    }

    static CategoriaResponse de(CategoriaRepository.CategoriaConProductos fila) {
        return new CategoriaResponse(fila.getId(), fila.getNombre(), fila.getProductos());
    }
}
