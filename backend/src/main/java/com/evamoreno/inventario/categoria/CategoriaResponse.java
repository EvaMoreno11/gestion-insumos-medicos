package com.evamoreno.inventario.categoria;

public record CategoriaResponse(Long id, String nombre) {

    static CategoriaResponse de(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre());
    }
}
