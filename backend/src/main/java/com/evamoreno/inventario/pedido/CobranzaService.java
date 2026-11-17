package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Cobranzas: todo lo que se debe de los pedidos despachados, separado en vencido, por cobrar
 * (con plazo vigente) y sin plazo (nadie anotó todavía cómo paga el cliente).
 */
@Service
@Transactional(readOnly = true)
public class CobranzaService {

    /** Una nota con saldo pendiente. @param despachado día del despacho */
    public record Deuda(Long pedidoId, String numero, DocumentoPedido documento, Long clienteId, String cliente,
            String local, LocalDate despachado, BigDecimal total, BigDecimal pagado, BigDecimal saldo,
            Integer plazoDias, LocalDate vence, Cobro.Estado estado, Long dias) {
    }

    /**
     * @param clientesVencidos cuántos clientes tienen al menos un cobro vencido (el aviso del inicio)
     * @param vencidos         primero los más atrasados
     * @param porCobrar        primero los que vencen antes
     * @param sinPlazo         primero los más antiguos
     */
    public record Cobranzas(BigDecimal totalPorCobrar, BigDecimal totalVencido, long clientesVencidos,
            List<Deuda> vencidos, List<Deuda> porCobrar, List<Deuda> sinPlazo) {
    }

    private final JdbcClient jdbc;
    private final EntityManager entityManager;
    private final Clock clock;

    public CobranzaService(JdbcClient jdbc, EntityManager entityManager, Clock clock) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public Cobranzas cobranzas() {
        entityManager.flush(); // la consulta va por JDBC: lo pendiente de la transacción también cuenta
        LocalDate hoy = LocalDate.now(clock);
        List<Deuda> deudas = jdbc.sql("""
                SELECT p.id, p.numero, p.documento, c.id AS cliente_id, c.nombre AS cliente, l.nombre AS local,
                       p.despachado_en, p.plazo_dias, t.total, COALESCE(pg.pagado, 0) AS pagado
                FROM pedido p
                JOIN cliente c ON c.id = p.cliente_id
                JOIN cliente_local l ON l.id = p.cliente_local_id
                JOIN LATERAL (SELECT COALESCE(SUM(i.cantidad * i.precio_unitario), 0) AS total
                              FROM pedido_item i WHERE i.pedido_id = p.id) t ON TRUE
                LEFT JOIN LATERAL (SELECT SUM(g.monto) AS pagado FROM pago g WHERE g.pedido_id = p.id) pg ON TRUE
                WHERE p.estado = 'DESPACHADO' AND t.total > COALESCE(pg.pagado, 0)
                ORDER BY p.despachado_en, p.id
                """)
                .query((rs, n) -> {
                    LocalDate despachado = rs.getObject("despachado_en", OffsetDateTime.class)
                            .atZoneSameInstant(clock.getZone()).toLocalDate();
                    Cobro cobro = Cobro.de(rs.getBigDecimal("total").setScale(2, RoundingMode.HALF_UP),
                            rs.getBigDecimal("pagado").setScale(2, RoundingMode.HALF_UP),
                            (Integer) rs.getObject("plazo_dias"), despachado, hoy);
                    return new Deuda(rs.getLong("id"), rs.getString("numero"),
                            DocumentoPedido.valueOf(rs.getString("documento")), rs.getLong("cliente_id"),
                            rs.getString("cliente"), rs.getString("local"), despachado, cobro.total(), cobro.pagado(),
                            cobro.saldo(), cobro.plazoDias(), cobro.vence(), cobro.estado(), cobro.dias());
                })
                .list();

        List<Deuda> vencidos = de(deudas, Cobro.Estado.VENCIDO).stream()
                .sorted(Comparator.comparing(Deuda::vence)).toList();
        List<Deuda> porCobrar = de(deudas, Cobro.Estado.POR_COBRAR).stream()
                .sorted(Comparator.comparing(Deuda::vence)).toList();
        return new Cobranzas(suma(deudas), suma(vencidos),
                vencidos.stream().map(Deuda::clienteId).distinct().count(),
                vencidos, porCobrar, de(deudas, Cobro.Estado.SIN_PLAZO));
    }

    private static List<Deuda> de(List<Deuda> deudas, Cobro.Estado estado) {
        return deudas.stream().filter(d -> d.estado() == estado).toList();
    }

    private static BigDecimal suma(List<Deuda> deudas) {
        return deudas.stream().map(Deuda::saldo).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
