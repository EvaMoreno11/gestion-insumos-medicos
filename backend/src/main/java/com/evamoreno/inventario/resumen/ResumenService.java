package com.evamoreno.inventario.resumen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.resumen.ResumenDto.ClienteInactivo;
import com.evamoreno.inventario.resumen.ResumenDto.ClienteTop;
import com.evamoreno.inventario.resumen.ResumenDto.Compras;
import com.evamoreno.inventario.resumen.ResumenDto.Detalles;
import com.evamoreno.inventario.resumen.ResumenDto.Ganancia;
import com.evamoreno.inventario.resumen.ResumenDto.Inventario;
import com.evamoreno.inventario.resumen.ResumenDto.Mes;
import com.evamoreno.inventario.resumen.ResumenDto.Pendiente;
import com.evamoreno.inventario.resumen.ResumenDto.ProductoSinMovimiento;
import com.evamoreno.inventario.resumen.ResumenDto.ProductoVendido;
import com.evamoreno.inventario.resumen.ResumenDto.Resumen;
import com.evamoreno.inventario.resumen.ResumenDto.Ventas;

import jakarta.persistence.EntityManager;

/**
 * Resumen ejecutivo: ventas, ganancia y compras de un periodo, y detalles de clientes, productos e inventario.
 * Son consultas de solo lectura con SQL agregado (suman en la base de datos, no en memoria).
 */
@Service
@Transactional(readOnly = true)
public class ResumenService {

    static final int MESES_GRAFICO = 12;
    static final int DIAS_INACTIVO = 60;
    static final int DIAS_SIN_MOVIMIENTO = 90;
    private static final int TOP = 10;
    private static final int MAXIMO_LISTA = 20;

    /**
     * Cada línea de los pedidos despachados en [inicio, fin), con lo vendido y lo que costó.
     * Una línea "sin costo" (escrita a mano, o con un lote de costo desconocido) no entra en la ganancia.
     */
    private static final String LINEAS_VENDIDAS = """
            SELECT p.id AS pedido_id, p.cliente_id, i.producto_id, i.cantidad,
                   to_char(p.despachado_en AT TIME ZONE :zona, 'YYYY-MM') AS mes,
                   COALESCE(i.cantidad * i.precio_unitario, 0) AS venta,
                   (i.producto_id IS NULL OR EXISTS (
                        SELECT 1 FROM movimiento m
                        WHERE m.pedido_item_id = i.id AND m.costo_unitario IS NULL)) AS sin_costo,
                   COALESCE((SELECT SUM(m.cantidad * m.costo_unitario) FROM movimiento m
                             WHERE m.pedido_item_id = i.id AND m.tipo = 'SALIDA'), 0) AS costo
            FROM pedido p
            JOIN pedido_item i ON i.pedido_id = p.id
            WHERE p.estado = 'DESPACHADO' AND p.despachado_en >= :inicio AND p.despachado_en < :fin
            """;

    private final JdbcClient jdbc;
    private final EntityManager entityManager;
    private final Clock clock;

    public ResumenService(JdbcClient jdbc, EntityManager entityManager, Clock clock) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    // ================================================================== pantalla principal

    public Resumen resumen(Periodo.Tipo tipo, LocalDate desde, LocalDate hasta) {
        entityManager.flush(); // lo pendiente de la transacción también cuenta (las consultas van por JDBC)
        Periodo periodo = Periodo.de(tipo, desde, hasta, hoy());
        Totales actual = totales(periodo.desde(), periodo.hasta());
        Totales anterior = totales(periodo.anteriorDesde(), periodo.anteriorHasta());
        Ventas ventas = new Ventas(actual.ventas, actual.pedidos, anterior.ventas, anterior.pedidos);
        Ganancia ganancia = new Ganancia(actual.ganancia(), actual.margen(), actual.ventasConCosto(),
                actual.costo, actual.ventasSinCosto, anterior.ganancia(), anterior.margen());
        Compras compras = compras(periodo.desde(), periodo.hasta(), compras(periodo.anteriorDesde(),
                periodo.anteriorHasta(), null).total());
        return new Resumen(periodo, ventas, ganancia, compras, ventasPorMes(YearMonth.from(periodo.hasta())));
    }

    /** Lo vendido y su costo entre dos fechas (inclusive). */
    private Totales totales(LocalDate desde, LocalDate hasta) {
        return consulta("""
                SELECT COALESCE(SUM(venta), 0) AS ventas,
                       COALESCE(SUM(venta) FILTER (WHERE sin_costo), 0) AS ventas_sin_costo,
                       COALESCE(SUM(costo) FILTER (WHERE NOT sin_costo), 0) AS costo,
                       COUNT(DISTINCT pedido_id) AS pedidos
                FROM (%s) lineas
                """.formatted(LINEAS_VENDIDAS), desde, hasta)
                .query((rs, n) -> new Totales(dinero(rs, "ventas"), dinero(rs, "ventas_sin_costo"),
                        dinero(rs, "costo"), rs.getLong("pedidos")))
                .single();
    }

    private Compras compras(LocalDate desde, LocalDate hasta, BigDecimal totalAnterior) {
        return consulta("""
                SELECT COALESCE(SUM(m.cantidad * m.costo_unitario), 0) AS total,
                       COUNT(*) AS entradas,
                       COUNT(*) FILTER (WHERE m.costo_unitario IS NULL) AS sin_costo
                FROM movimiento m
                WHERE m.tipo = 'ENTRADA' AND NOT m.inicial AND m.fecha >= :inicio AND m.fecha < :fin
                """, desde, hasta)
                .query((rs, n) -> new Compras(dinero(rs, "total"), rs.getLong("entradas"), rs.getLong("sin_costo"),
                        totalAnterior))
                .single();
    }

    /** Ventas y ganancia de los últimos 12 meses (hasta el mes indicado), incluidos los meses sin ventas. */
    private List<Mes> ventasPorMes(YearMonth ultimo) {
        YearMonth primero = ultimo.minusMonths(MESES_GRAFICO - 1L);
        Map<String, Mes> porMes = new HashMap<>();
        consulta("""
                SELECT mes,
                       COALESCE(SUM(venta), 0) AS ventas,
                       COALESCE(SUM(venta - costo) FILTER (WHERE NOT sin_costo), 0) AS ganancia
                FROM (%s) lineas
                GROUP BY mes
                """.formatted(LINEAS_VENDIDAS), primero.atDay(1), ultimo.atEndOfMonth())
                .query((rs, n) -> new Mes(rs.getString("mes"), dinero(rs, "ventas"), dinero(rs, "ganancia")))
                .list()
                .forEach(m -> porMes.put(m.mes(), m));
        List<Mes> meses = new ArrayList<>();
        for (YearMonth mes = primero; !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            String clave = mes.toString();
            meses.add(porMes.getOrDefault(clave, new Mes(clave, cero(), cero())));
        }
        return meses;
    }

    // ================================================================== más detalles

    public Detalles detalles(Periodo.Tipo tipo, LocalDate desde, LocalDate hasta) {
        entityManager.flush();
        LocalDate hoy = hoy();
        Periodo periodo = Periodo.de(tipo, desde, hasta, hoy);
        return new Detalles(periodo, productosMasVendidos(periodo), mejoresClientes(periodo),
                clientesInactivos(hoy), DIAS_INACTIVO, inventario(hoy), sinMovimiento(hoy), DIAS_SIN_MOVIMIENTO,
                pendiente());
    }

    private List<ProductoVendido> productosMasVendidos(Periodo periodo) {
        return consulta("""
                SELECT pr.id, pr.nombre, pr.presentacion, pr.marca,
                       SUM(lineas.cantidad) AS unidades, SUM(lineas.venta) AS ventas
                FROM (%s) lineas
                JOIN producto pr ON pr.id = lineas.producto_id
                GROUP BY pr.id, pr.nombre, pr.presentacion, pr.marca
                ORDER BY ventas DESC, unidades DESC
                LIMIT %d
                """.formatted(LINEAS_VENDIDAS, TOP), periodo.desde(), periodo.hasta())
                .query((rs, n) -> new ProductoVendido(rs.getLong("id"), rs.getString("nombre"),
                        rs.getString("presentacion"), rs.getString("marca"), rs.getLong("unidades"),
                        dinero(rs, "ventas")))
                .list();
    }

    private List<ClienteTop> mejoresClientes(Periodo periodo) {
        return consulta("""
                SELECT c.id, c.nombre, COUNT(DISTINCT lineas.pedido_id) AS pedidos, SUM(lineas.venta) AS ventas
                FROM (%s) lineas
                JOIN cliente c ON c.id = lineas.cliente_id
                GROUP BY c.id, c.nombre
                ORDER BY ventas DESC
                LIMIT %d
                """.formatted(LINEAS_VENDIDAS, TOP), periodo.desde(), periodo.hasta())
                .query((rs, n) -> new ClienteTop(rs.getLong("id"), rs.getString("nombre"), rs.getLong("pedidos"),
                        dinero(rs, "ventas")))
                .list();
    }

    /** Clientes activos que ya compraron, cuyo último despacho fue hace más de 60 días y no tienen pedidos nuevos. */
    private List<ClienteInactivo> clientesInactivos(LocalDate hoy) {
        OffsetDateTime limite = inicioDelDia(hoy.minusDays(DIAS_INACTIVO));
        return jdbc.sql("""
                SELECT c.id, c.nombre, MAX(p.despachado_en) AS ultimo,
                       SUM(COALESCE(i.cantidad * i.precio_unitario, 0)) AS total
                FROM cliente c
                JOIN pedido p ON p.cliente_id = c.id AND p.estado = 'DESPACHADO'
                JOIN pedido_item i ON i.pedido_id = p.id
                WHERE c.activo
                  AND NOT EXISTS (SELECT 1 FROM pedido b
                                  WHERE b.cliente_id = c.id AND b.estado = 'BORRADOR' AND b.creado_en >= :limite)
                GROUP BY c.id, c.nombre
                HAVING MAX(p.despachado_en) < :limite
                ORDER BY total DESC
                LIMIT %d
                """.formatted(MAXIMO_LISTA))
                .param("limite", limite)
                .query((rs, n) -> {
                    LocalDate ultimo = fecha(rs, "ultimo");
                    return new ClienteInactivo(rs.getLong("id"), rs.getString("nombre"), ultimo,
                            ChronoUnit.DAYS.between(ultimo, hoy), dinero(rs, "total"));
                })
                .list();
    }

    private Inventario inventario(LocalDate hoy) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(l.cantidad_actual * l.costo_promedio) FILTER (WHERE l.vigente), 0) AS valor,
                       COALESCE(SUM(l.cantidad_actual) FILTER (WHERE l.vigente), 0) AS unidades,
                       COUNT(DISTINCT l.producto_id) FILTER (WHERE l.vigente AND l.costo_promedio IS NULL)
                           AS productos_sin_costo,
                       COALESCE(SUM(l.cantidad_actual * l.costo_promedio) FILTER (WHERE NOT l.vigente), 0)
                           AS valor_vencido,
                       COUNT(*) FILTER (WHERE NOT l.vigente) AS lotes_vencidos
                FROM (SELECT l.*, (l.fecha_vencimiento IS NULL OR l.fecha_vencimiento >= :hoy) AS vigente
                      FROM lote l JOIN producto p ON p.id = l.producto_id
                      WHERE l.cantidad_actual > 0 AND p.activo) l
                """)
                .param("hoy", hoy)
                .query((rs, n) -> new Inventario(dinero(rs, "valor"), rs.getLong("unidades"),
                        rs.getLong("productos_sin_costo"), dinero(rs, "valor_vencido"), rs.getLong("lotes_vencidos")))
                .single();
    }

    /** Productos con stock desde hace más de 90 días que no se venden hace 90 días (o nunca). */
    private List<ProductoSinMovimiento> sinMovimiento(LocalDate hoy) {
        OffsetDateTime limite = inicioDelDia(hoy.minusDays(DIAS_SIN_MOVIMIENTO));
        return jdbc.sql("""
                SELECT p.id, p.nombre, p.presentacion, p.marca, s.stock, s.valor, s.ultima_salida
                FROM producto p
                JOIN LATERAL (
                    SELECT SUM(l.cantidad_actual) AS stock,
                           SUM(l.cantidad_actual * l.costo_promedio) AS valor,
                           MIN(l.fecha_ingreso) AS primer_ingreso,
                           (SELECT MAX(m.fecha) FROM movimiento m JOIN lote l2 ON l2.id = m.lote_id
                            WHERE l2.producto_id = p.id AND m.tipo = 'SALIDA') AS ultima_salida
                    FROM lote l
                    WHERE l.producto_id = p.id AND l.cantidad_actual > 0
                ) s ON s.stock > 0
                WHERE p.activo
                  AND s.primer_ingreso < :limite
                  AND (s.ultima_salida IS NULL OR s.ultima_salida < :limite)
                ORDER BY s.valor DESC NULLS LAST, s.stock DESC
                LIMIT %d
                """.formatted(MAXIMO_LISTA))
                .param("limite", limite)
                .query((rs, n) -> new ProductoSinMovimiento(rs.getLong("id"), rs.getString("nombre"),
                        rs.getString("presentacion"), rs.getString("marca"), rs.getLong("stock"),
                        rs.getBigDecimal("valor") == null ? null : dinero(rs, "valor"), fecha(rs, "ultima_salida")))
                .list();
    }

    private Pendiente pendiente() {
        return jdbc.sql("""
                SELECT COALESCE(SUM(i.cantidad * i.precio_unitario), 0) AS total,
                       COUNT(DISTINCT p.id) AS pedidos,
                       COUNT(DISTINCT p.id) FILTER (WHERE p.origen_id IS NOT NULL) AS saldos
                FROM pedido p
                LEFT JOIN pedido_item i ON i.pedido_id = p.id
                WHERE p.estado = 'BORRADOR'
                """)
                .query((rs, n) -> new Pendiente(dinero(rs, "total"), rs.getLong("pedidos"), rs.getLong("saldos")))
                .single();
    }

    // ================================================================== utilidades

    /** Consulta con :zona, :inicio y :fin para un rango de fechas (inclusive) en la zona horaria del negocio. */
    private JdbcClient.StatementSpec consulta(String sql, LocalDate desde, LocalDate hasta) {
        return jdbc.sql(sql)
                .param("zona", clock.getZone().getId())
                .param("inicio", inicioDelDia(desde))
                .param("fin", inicioDelDia(hasta.plusDays(1)));
    }

    private OffsetDateTime inicioDelDia(LocalDate dia) {
        return dia.atStartOfDay(clock.getZone()).toInstant().atOffset(ZoneOffset.UTC);
    }

    private LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.atZoneSameInstant(clock.getZone()).toLocalDate();
    }

    private LocalDate hoy() {
        return LocalDate.now(clock);
    }

    private static BigDecimal dinero(ResultSet rs, String columna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(columna);
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(2);
    }

    /** Lo vendido en un periodo. */
    private record Totales(BigDecimal ventas, BigDecimal ventasSinCosto, BigDecimal costo, long pedidos) {

        BigDecimal ventasConCosto() {
            return ventas.subtract(ventasSinCosto);
        }

        BigDecimal ganancia() {
            return ventasConCosto().subtract(costo);
        }

        /** % de ganancia sobre las ventas con costo, con 1 decimal; null si no las hay. */
        BigDecimal margen() {
            return ventasConCosto().signum() == 0 ? null
                    : ganancia().multiply(BigDecimal.valueOf(100)).divide(ventasConCosto(), 1, RoundingMode.HALF_UP);
        }
    }
}
