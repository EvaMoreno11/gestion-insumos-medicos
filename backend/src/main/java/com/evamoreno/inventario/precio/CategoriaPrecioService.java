package com.evamoreno.inventario.precio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.precio.CategoriaPrecioDto.PrecioCategoria;
import com.evamoreno.inventario.precio.CategoriaPrecioDto.Request;
import com.evamoreno.inventario.precio.CategoriaPrecioDto.Response;

@Service
@Transactional
public class CategoriaPrecioService {

    private final CategoriaPrecioRepository categorias;

    public CategoriaPrecioService(CategoriaPrecioRepository categorias) {
        this.categorias = categorias;
    }

    @Transactional(readOnly = true)
    public List<Response> listar() {
        return categorias.findAllByOrderByPorcentajeAsc().stream().map(Response::de).toList();
    }

    /**
     * Letra de cada porcentaje, para mostrar "B" en lugar de "15 %".
     * La clave es el porcentaje sin ceros de más (15.00 → 15) para que la comparación no dependa de la escala.
     */
    @Transactional(readOnly = true)
    public Map<BigDecimal, String> letrasPorPorcentaje() {
        return categorias.findAll().stream()
                .collect(Collectors.toMap(c -> clave(c.getPorcentaje()), CategoriaPrecio::getLetra));
    }

    public static String letraDe(Map<BigDecimal, String> letras, BigDecimal porcentaje) {
        return porcentaje == null ? null : letras.get(clave(porcentaje));
    }

    /** Precio de un producto en cada categoría (A, B, C...). Vacío si el producto no tiene costo. */
    @Transactional(readOnly = true)
    public List<PrecioCategoria> preciosPorCategoria(BigDecimal costo) {
        if (costo == null) {
            return List.of();
        }
        return categorias.findAllByOrderByPorcentajeAsc().stream()
                .map(c -> new PrecioCategoria(c.getLetra(), c.getPorcentaje(),
                        Precios.precio(costo, c.getPorcentaje())))
                .toList();
    }

    public Response crear(Request request) {
        String letra = limpiar(request.letra());
        validarUnicos(letra, request.porcentaje(), null);
        return Response.de(categorias.save(new CategoriaPrecio(letra, request.porcentaje())));
    }

    public Response actualizar(Long id, Request request) {
        CategoriaPrecio categoria = categorias.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la categoría de precio con id " + id));
        String letra = limpiar(request.letra());
        validarUnicos(letra, request.porcentaje(), id);
        categoria.actualizar(letra, request.porcentaje());
        return Response.de(categoria);
    }

    private void validarUnicos(String letra, BigDecimal porcentaje, Long idActual) {
        for (CategoriaPrecio existente : categorias.findAll()) {
            if (existente.getId().equals(idActual)) {
                continue;
            }
            if (existente.getLetra().equalsIgnoreCase(letra)) {
                throw new ConflictoException("Ya existe la categoría de precio " + existente.getLetra());
            }
            if (existente.getPorcentaje().compareTo(porcentaje) == 0) {
                throw new ConflictoException("La categoría " + existente.getLetra() + " ya usa el "
                        + clave(porcentaje).toPlainString() + " %");
            }
        }
    }

    private static String limpiar(String letra) {
        return letra.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal clave(BigDecimal porcentaje) {
        return porcentaje.stripTrailingZeros();
    }
}
