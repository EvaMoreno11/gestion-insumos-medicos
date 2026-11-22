package com.evamoreno.inventario.producto;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.evamoreno.inventario.common.TextoBusqueda;

import jakarta.persistence.criteria.Predicate;

/** Construye la consulta del buscador "¿tengo esto?" a partir de los filtros. */
final class ProductoEspecificaciones {

    private ProductoEspecificaciones() {
    }

    /**
     * Todas las palabras buscadas deben aparecer en el producto, al inicio de una palabra; los números y las letras
     * sueltas, como palabra completa (ver {@link TextoBusqueda#patronDePalabra}).
     * Ej: "jer 5ml" encuentra "Jeringa 5 ml ..." pero no "Jeringa 20 ml ... Caja x 50".
     */
    static Specification<Producto> filtrar(String buscar, Long categoriaId, boolean incluirInactivos) {
        List<String> tokens = TextoBusqueda.tokens(buscar);
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            for (String token : tokens) {
                // textoBusqueda empieza y termina con espacio, así " palabra " siempre es una palabra entera
                condiciones.add(cb.like(root.get("textoBusqueda"), TextoBusqueda.patronDePalabra(token)));
            }
            if (categoriaId != null) {
                condiciones.add(cb.equal(root.get("categoria").get("id"), categoriaId));
            }
            if (!incluirInactivos) {
                condiciones.add(cb.isTrue(root.get("activo")));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
