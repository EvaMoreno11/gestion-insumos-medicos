package com.evamoreno.inventario.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.evamoreno.inventario.common.Pagina;

/**
 * Entradas registradas (compras) que cumplen la búsqueda, de la más reciente a la más antigua.
 *
 * @param entradas cuántas hay en total (no solo en esta página)
 * @param total    cuánto suman (cantidad × costo de cada una); las que no tienen costo no suman
 * @param sinCosto cuántas no tienen costo
 * @param dias     lo mismo por día, del más reciente al más antiguo (para los títulos de cada día)
 */
public record EntradasRegistradas(int entradas, BigDecimal total, int sinCosto, List<Dia> dias,
        Pagina<MovimientoResponse> lista) {

    public record Dia(LocalDate fecha, int entradas, BigDecimal total, int sinCosto) {
    }
}
