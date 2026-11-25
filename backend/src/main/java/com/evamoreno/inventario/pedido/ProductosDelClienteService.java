package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.cliente.ClienteRepository;
import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.common.TextoBusqueda;

import jakarta.persistence.EntityManager;

/**
 * Los productos que compra un cliente (para su ficha): cada producto que se le vendió en un pedido despachado, con
 * el último precio, la fecha y la cantidad de esa última venta; y, de un producto, todas las veces que se le vendió.
 * No cuentan los borradores, los anulados, los ítems quitados en una corrección (cantidad 0) ni los escritos a mano.
 */
@Service
@Transactional(readOnly = true)
public class ProductosDelClienteService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final int MAXIMO_VENTAS = 200;

    /** Las líneas vendidas a un cliente: una por ítem de un pedido despachado. */
    private static final String LINEAS = """
            SELECT i.id AS item_id, i.producto_id, p.id AS pedido_id, p.numero, p.despachado_en,
                   i.cantidad, i.precio_unitario
            FROM pedido p
            JOIN pedido_item i ON i.pedido_id = p.id
            WHERE p.cliente_id = :cliente AND p.estado = 'DESPACHADO' AND i.producto_id IS NOT NULL
              AND i.cantidad > 0
            """;

    /**
     * @param ultimoPrecio   precio por unidad de la última venta (null si esa línea no tenía precio)
     * @param ultimaVenta    día del último despacho con este producto
     * @param ultimaCantidad cuánto se llevó esa vez
     * @param pedidos        en cuántos pedidos lo compró
     */
    public record ProductoComprado(Long productoId, String codigo, String nombre, String presentacion, String marca,
            String unidadMedida, BigDecimal ultimoPrecio, LocalDate ultimaVenta, int ultimaCantidad, int pedidos) {
    }

    /** Una vez que se le vendió el producto. */
    public record Venta(Long pedidoId, String numero, LocalDate fecha, int cantidad, BigDecimal precio,
            BigDecimal total) {
    }

    private final JdbcClient jdbc;
    private final ClienteRepository clientes;
    private final EntityManager entityManager;
    private final Clock clock;

    public ProductosDelClienteService(JdbcClient jdbc, ClienteRepository clientes, EntityManager entityManager,
            Clock clock) {
        this.jdbc = jdbc;
        this.clientes = clientes;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Lo que compró el cliente, primero lo que compró más recientemente.
     *
     * @param buscar palabras del producto (nombre, marca, presentación o código), con la regla del buscador
     */
    public Pagina<ProductoComprado> productos(Long clienteId, String buscar, int pagina, int tamano) {
        validarCliente(clienteId);
        Map<String, Object> params = new HashMap<>();
        params.put("cliente", clienteId);
        StringBuilder where = new StringBuilder("WHERE TRUE");
        List<String> tokens = TextoBusqueda.tokens(buscar);
        for (int i = 0; i < tokens.size(); i++) {
            where.append(" AND pr.texto_busqueda LIKE :t").append(i);
            params.put("t" + i, TextoBusqueda.patronDePalabra(tokens.get(i)));
        }
        String consulta = """
                WITH lineas AS (%s),
                ultima AS (
                    SELECT DISTINCT ON (producto_id) producto_id, despachado_en, cantidad, precio_unitario
                    FROM lineas ORDER BY producto_id, despachado_en DESC, pedido_id DESC, item_id DESC
                ),
                veces AS (SELECT producto_id, COUNT(DISTINCT pedido_id) AS pedidos FROM lineas GROUP BY producto_id)
                SELECT pr.id, pr.codigo, pr.nombre, pr.presentacion, pr.marca, pr.unidad_medida,
                       u.despachado_en, u.cantidad, u.precio_unitario, v.pedidos
                FROM ultima u
                JOIN veces v ON v.producto_id = u.producto_id
                JOIN producto pr ON pr.id = u.producto_id
                %s
                """.formatted(LINEAS, where);
        long total = jdbc.sql("SELECT COUNT(*) FROM (" + consulta + ") x").params(params).query(Long.class).single();

        int tamanoPagina = Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA);
        int numeroPagina = Math.max(pagina, 0);
        params.put("limite", tamanoPagina);
        params.put("salto", (long) numeroPagina * tamanoPagina);
        List<ProductoComprado> contenido = jdbc.sql(consulta
                + " ORDER BY u.despachado_en DESC, pr.nombre LIMIT :limite OFFSET :salto")
                .params(params)
                .query((rs, n) -> new ProductoComprado(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("presentacion"), rs.getString("marca"), rs.getString("unidad_medida"),
                        dinero(rs.getBigDecimal("precio_unitario")), dia(rs), rs.getInt("cantidad"),
                        rs.getInt("pedidos")))
                .list();
        return new Pagina<>(contenido, numeroPagina, tamanoPagina, total,
                (int) ((total + tamanoPagina - 1) / tamanoPagina));
    }

    /** Todas las veces que se le vendió un producto al cliente, de la más reciente a la más antigua. */
    public List<Venta> ventas(Long clienteId, Long productoId) {
        validarCliente(clienteId);
        return jdbc.sql(LINEAS + """
                  AND i.producto_id = :producto
                ORDER BY p.despachado_en DESC, p.id DESC, i.id DESC
                LIMIT :limite
                """)
                .param("cliente", clienteId)
                .param("producto", productoId)
                .param("limite", MAXIMO_VENTAS)
                .query((rs, n) -> {
                    BigDecimal precio = dinero(rs.getBigDecimal("precio_unitario"));
                    int cantidad = rs.getInt("cantidad");
                    return new Venta(rs.getLong("pedido_id"), rs.getString("numero"), dia(rs), cantidad, precio,
                            precio == null ? null : dinero(precio.multiply(BigDecimal.valueOf(cantidad))));
                })
                .list();
    }

    private void validarCliente(Long clienteId) {
        entityManager.flush(); // las consultas van por JDBC: lo pendiente de la transacción también cuenta
        if (!clientes.existsById(clienteId)) {
            throw new RecursoNoEncontradoException("No existe el cliente con id " + clienteId);
        }
    }

    private LocalDate dia(ResultSet rs) throws SQLException {
        return rs.getObject("despachado_en", OffsetDateTime.class).atZoneSameInstant(clock.getZone()).toLocalDate();
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
