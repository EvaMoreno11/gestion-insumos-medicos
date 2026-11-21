package com.evamoreno.inventario.pedido;

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
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.common.SolicitudInvalidaException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.pedido.PedidoDto.DiaDespacho;
import com.evamoreno.inventario.pedido.PedidoDto.Despachados;
import com.evamoreno.inventario.pedido.PedidoDto.Resumen;

/**
 * Pedidos despachados, del más reciente al más antiguo según la fecha de despacho, con búsqueda por cliente,
 * local o N° de nota y un rango de fechas opcional. Trae también cuántos pedidos y cuánto suma cada día de
 * todo el resultado (no solo de la página), para los títulos de cada día.
 */
@Service
@Transactional(readOnly = true)
public class DespachadosService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private static final String TABLAS = """
            FROM pedido p
            JOIN cliente c ON c.id = p.cliente_id
            JOIN cliente_local l ON l.id = p.cliente_local_id
            """;

    /** Total de cada pedido: lo entregado por su precio (un ítem quitado tiene cantidad 0). */
    private static final String TOTAL_DEL_PEDIDO = """
            LEFT JOIN LATERAL (SELECT SUM(i.cantidad * i.precio_unitario) AS total
                               FROM pedido_item i WHERE i.pedido_id = p.id) t ON TRUE
            """;

    private final JdbcClient jdbc;
    private final PedidoRepository pedidos;
    private final Clock clock;

    public DespachadosService(JdbcClient jdbc, PedidoRepository pedidos, Clock clock) {
        this.jdbc = jdbc;
        this.pedidos = pedidos;
        this.clock = clock;
    }

    /**
     * @param buscar palabras del nombre del cliente o de su local, o parte del N° de nota (cada palabra debe
     *               coincidir con algo)
     * @param desde  primer día (inclusive), o null para no limitar
     * @param hasta  último día (inclusive), o null para no limitar
     */
    public Despachados buscar(String buscar, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("La fecha «desde» es posterior a la fecha «hasta»");
        }
        StringBuilder where = new StringBuilder("WHERE p.estado = 'DESPACHADO'");
        Map<String, Object> params = new HashMap<>();
        if (desde != null) {
            where.append(" AND p.despachado_en >= :inicio");
            params.put("inicio", inicioDelDia(desde));
        }
        if (hasta != null) {
            where.append(" AND p.despachado_en < :fin");
            params.put("fin", inicioDelDia(hasta.plusDays(1)));
        }
        List<String> tokens = TextoBusqueda.tokens(buscar);
        for (int i = 0; i < tokens.size(); i++) {
            where.append(" AND (c.texto_busqueda LIKE :t%1$d OR l.texto_busqueda LIKE :t%1$d OR p.numero LIKE :n%1$d)"
                    .formatted(i));
            params.put("t" + i, "% " + tokens.get(i) + "%"); // inicio de una palabra
            params.put("n" + i, "%" + tokens.get(i) + "%");
        }

        params.put("zona", clock.getZone().getId());
        List<DiaDespacho> dias = jdbc.sql("""
                SELECT to_char(p.despachado_en AT TIME ZONE :zona, 'YYYY-MM-DD') AS dia,
                       COUNT(*) AS pedidos, COALESCE(SUM(t.total), 0) AS total
                """ + TABLAS + TOTAL_DEL_PEDIDO + where + "\nGROUP BY dia ORDER BY dia DESC")
                .params(params)
                .query((rs, n) -> new DiaDespacho(LocalDate.parse(rs.getString("dia")), rs.getInt("pedidos"),
                        rs.getBigDecimal("total").setScale(2, RoundingMode.HALF_UP)))
                .list();
        int totalPedidos = dias.stream().mapToInt(DiaDespacho::pedidos).sum();
        BigDecimal total = dias.stream().map(DiaDespacho::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        int tamanoPagina = Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA);
        int numeroPagina = Math.max(pagina, 0);
        params.put("limite", tamanoPagina);
        params.put("salto", (long) numeroPagina * tamanoPagina);
        List<Long> ids = jdbc.sql("SELECT p.id\n" + TABLAS + where
                + "\nORDER BY p.despachado_en DESC, p.id DESC LIMIT :limite OFFSET :salto")
                .params(params)
                .query(Long.class)
                .list();

        Map<Long, Pedido> porId = pedidos.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Pedido::getId, Function.identity()));
        List<Long> origenes = porId.values().stream().map(Pedido::getOrigenId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> numerosDeOrigen = new HashMap<>();
        pedidos.findAllById(origenes).forEach(o -> numerosDeOrigen.put(o.getId(), o.getNumero()));
        List<Resumen> contenido = new ArrayList<>();
        for (Long id : ids) {
            Pedido p = porId.get(id);
            contenido.add(PedidoService.resumen(p, Map.of(), numerosDeOrigen.get(p.getOrigenId())));
        }
        int totalPaginas = (totalPedidos + tamanoPagina - 1) / tamanoPagina;
        return new Despachados(totalPedidos, total.setScale(2, RoundingMode.HALF_UP), dias,
                new Pagina<>(contenido, numeroPagina, tamanoPagina, totalPedidos, totalPaginas));
    }

    private OffsetDateTime inicioDelDia(LocalDate dia) {
        return dia.atStartOfDay(clock.getZone()).toInstant().atOffset(ZoneOffset.UTC);
    }
}
