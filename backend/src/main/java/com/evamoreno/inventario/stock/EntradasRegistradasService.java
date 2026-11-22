package com.evamoreno.inventario.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.common.SolicitudInvalidaException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.stock.EntradasRegistradas.Dia;

/**
 * Entradas registradas (compras), de la más reciente a la más antigua, con búsqueda por producto o por N° de lote y
 * un rango de fechas opcional. Trae también cuántas entradas hay y cuánto suma cada día de todo el resultado (no solo
 * de la página), para los títulos de cada día. No incluye la carga inicial desde Excel, igual que las compras del
 * Resumen.
 */
@Service
@Transactional(readOnly = true)
public class EntradasRegistradasService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private static final String TABLAS = """
            FROM movimiento m
            JOIN lote l ON l.id = m.lote_id
            JOIN producto p ON p.id = l.producto_id
            """;

    /** N° de lote solo con letras y números, en minúsculas: "L-2304/15" → "l230415". */
    private static final String LOTE_COMPACTO = "lower(regexp_replace(l.numero_lote, '[^A-Za-z0-9]', '', 'g'))";

    private final JdbcClient jdbc;
    private final MovimientoRepository movimientos;
    private final Clock clock;

    public EntradasRegistradasService(JdbcClient jdbc, MovimientoRepository movimientos, Clock clock) {
        this.jdbc = jdbc;
        this.movimientos = movimientos;
        this.clock = clock;
    }

    /**
     * @param buscar palabras del producto (nombre, marca, presentación o código; todas deben coincidir) o parte del
     *               N° de lote (sin importar guiones ni espacios)
     * @param desde  primer día (inclusive), o null para no limitar
     * @param hasta  último día (inclusive), o null para no limitar
     */
    public EntradasRegistradas buscar(String buscar, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("La fecha «desde» es posterior a la fecha «hasta»");
        }
        StringBuilder where = new StringBuilder("WHERE m.tipo = 'ENTRADA' AND NOT m.inicial");
        Map<String, Object> params = new HashMap<>();
        if (desde != null) {
            where.append(" AND m.fecha >= :inicio");
            params.put("inicio", inicioDelDia(desde));
        }
        if (hasta != null) {
            where.append(" AND m.fecha < :fin");
            params.put("fin", inicioDelDia(hasta.plusDays(1)));
        }
        List<String> tokens = TextoBusqueda.tokens(buscar);
        if (!tokens.isEmpty()) {
            List<String> palabras = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i++) {
                palabras.add("p.texto_busqueda LIKE :t" + i);
                params.put("t" + i, TextoBusqueda.patronDePalabra(tokens.get(i)));
            }
            where.append(" AND ((").append(String.join(" AND ", palabras)).append(") OR ")
                    .append(LOTE_COMPACTO).append(" LIKE :lote)");
            params.put("lote", "%" + String.join("", tokens).replaceAll("[^a-z0-9]", "") + "%");
        }

        params.put("zona", clock.getZone().getId());
        List<Dia> dias = jdbc.sql("""
                SELECT to_char(m.fecha AT TIME ZONE :zona, 'YYYY-MM-DD') AS dia, COUNT(*) AS entradas,
                       COALESCE(SUM(m.cantidad * m.costo_unitario), 0) AS total,
                       COUNT(*) FILTER (WHERE m.costo_unitario IS NULL) AS sin_costo
                """ + TABLAS + where + "\nGROUP BY dia ORDER BY dia DESC")
                .params(params)
                .query((rs, n) -> new Dia(LocalDate.parse(rs.getString("dia")), rs.getInt("entradas"),
                        rs.getBigDecimal("total"), rs.getInt("sin_costo")))
                .list();
        int totalEntradas = dias.stream().mapToInt(Dia::entradas).sum();
        int sinCosto = dias.stream().mapToInt(Dia::sinCosto).sum();
        // Se suma sin redondear (como las compras del Resumen) y se redondea al final
        BigDecimal total = dias.stream().map(Dia::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        int tamanoPagina = Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA);
        int numeroPagina = Math.max(pagina, 0);
        params.put("limite", tamanoPagina);
        params.put("salto", (long) numeroPagina * tamanoPagina);
        List<Long> ids = jdbc.sql("SELECT m.id\n" + TABLAS + where
                + "\nORDER BY m.fecha DESC, m.id DESC LIMIT :limite OFFSET :salto")
                .params(params)
                .query(Long.class)
                .list();
        Map<Long, Movimiento> porId = movimientos.conLoteYProducto(ids).stream()
                .collect(Collectors.toMap(Movimiento::getId, Function.identity()));
        List<MovimientoResponse> contenido = ids.stream().map(id -> MovimientoResponse.de(porId.get(id))).toList();

        int totalPaginas = (totalEntradas + tamanoPagina - 1) / tamanoPagina;
        return new EntradasRegistradas(totalEntradas, dinero(total), sinCosto,
                dias.stream().map(d -> new Dia(d.fecha(), d.entradas(), dinero(d.total()), d.sinCosto())).toList(),
                new Pagina<>(contenido, numeroPagina, tamanoPagina, totalEntradas, totalPaginas));
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private OffsetDateTime inicioDelDia(LocalDate dia) {
        return dia.atStartOfDay(clock.getZone()).toInstant().atOffset(ZoneOffset.UTC);
    }
}
