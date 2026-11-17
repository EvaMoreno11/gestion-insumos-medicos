package com.evamoreno.inventario.catalogo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.catalogo.Exportaciones.Catalogo;
import com.evamoreno.inventario.catalogo.Exportaciones.Grupo;
import com.evamoreno.inventario.catalogo.Exportaciones.Inventario;
import com.evamoreno.inventario.catalogo.Exportaciones.Item;
import com.evamoreno.inventario.cliente.Cliente;
import com.evamoreno.inventario.cliente.ClienteRepository;
import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.negocio.NegocioDto;
import com.evamoreno.inventario.negocio.NegocioService;
import com.evamoreno.inventario.precio.Precios;
import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.producto.ProductoRepository;
import com.evamoreno.inventario.stock.Lote;
import com.evamoreno.inventario.stock.LoteRepository;
import com.evamoreno.inventario.stock.StockService;

/** Arma el inventario completo (interno) y el catálogo para clientes (con o sin precios). */
@Service
@Transactional(readOnly = true)
public class ExportacionService {

    private static final DateTimeFormatter MES_ANIO = DateTimeFormatter.ofPattern("MM/yyyy");

    /** Orden de las filas: categoría, nombre, marca y presentación. */
    private static final Comparator<Producto> ORDEN = Comparator
            .comparing((Producto p) -> p.getCategoria().getNombre(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Producto::getNombre, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Producto::getMarca, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Producto::getPresentacion, String.CASE_INSENSITIVE_ORDER);

    private final ProductoRepository productos;
    private final LoteRepository lotes;
    private final ClienteRepository clientes;
    private final NegocioService negocio;
    private final StockService stock;

    public ExportacionService(ProductoRepository productos, LoteRepository lotes, ClienteRepository clientes,
            NegocioService negocio, StockService stock) {
        this.productos = productos;
        this.lotes = lotes;
        this.clientes = clientes;
        this.negocio = negocio;
        this.stock = stock;
    }

    // ------------------------------------------------------------------ inventario

    public Inventario inventario() {
        LocalDate hoy = stock.hoy();
        Map<Long, List<Lote>> lotesPorProducto = lotes.findByCantidadActualGreaterThan(0).stream()
                .filter(l -> l.getProducto().isActivo())
                .collect(Collectors.groupingBy(l -> l.getProducto().getId()));

        List<Producto> activos = productos.findByActivoTrue().stream().sorted(ORDEN).toList();
        List<Exportaciones.Producto> filas = activos.stream().map(p -> {
            List<Lote> vigentes = lotesPorProducto.getOrDefault(p.getId(), List.of()).stream()
                    .filter(l -> !l.estaVencido(hoy)).toList();
            int disponible = vigentes.stream().mapToInt(Lote::getCantidadActual).sum();
            BigDecimal valor = vigentes.stream()
                    .map(l -> costoDe(l) == null ? null : costoDe(l).multiply(BigDecimal.valueOf(l.getCantidadActual())))
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            return new Exportaciones.Producto(p.getCodigo(), p.getCategoria().getNombre(), p.getNombre(),
                    p.getMarca(), p.getPresentacion(), p.getUnidadMedida(), disponible, p.getStockMinimo(),
                    p.getCosto(), valor);
        }).toList();

        List<Exportaciones.Lote> filasLotes = activos.stream()
                .flatMap(p -> lotesPorProducto.getOrDefault(p.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(Lote::getFechaVencimiento,
                                Comparator.nullsLast(Comparator.naturalOrder()))))
                .map(l -> new Exportaciones.Lote(l.getProducto().getCodigo(), l.getProducto().getNombre(),
                        l.getProducto().getMarca(), l.getProducto().getPresentacion(), l.getNumeroLote(),
                        l.getFechaVencimiento() == null ? null : l.getFechaVencimiento().format(MES_ANIO),
                        l.getCantidadActual(), costoDe(l), l.estaVencido(hoy)))
                .toList();
        return new Inventario(hoy, filas, filasLotes);
    }

    /** Costo de las unidades de un lote: su promedio o, si no lo tiene, el del producto. */
    private static BigDecimal costoDe(Lote lote) {
        return lote.getCostoPromedio() != null ? lote.getCostoPromedio() : lote.getProducto().getCosto();
    }

    // ------------------------------------------------------------------ catálogo

    /**
     * Productos activos con stock mínimo (los que se venden normalmente), agrupados por categoría.
     * Con un cliente, cada precio sale con su porcentaje (costo + %), igual que en sus pedidos.
     */
    public Catalogo catalogo(Long clienteId) {
        Cliente cliente = null;
        if (clienteId != null) {
            cliente = clientes.findById(clienteId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente con id " + clienteId));
            if (cliente.getPorcentaje() == null) {
                throw new ConflictoException("El cliente \"" + cliente.getNombre()
                        + "\" aún no tiene porcentaje: ponle su categoría de precio para armar su lista");
            }
        }
        BigDecimal porcentaje = cliente == null ? null : cliente.getPorcentaje();

        Map<String, List<Item>> porCategoria = new LinkedHashMap<>();
        productos.findByActivoTrue().stream()
                .filter(p -> p.getStockMinimo() > 0) // sin mínimo: puede estar descontinuado
                .sorted(ORDEN)
                .forEach(p -> porCategoria.computeIfAbsent(p.getCategoria().getNombre(), c -> new java.util.ArrayList<>())
                        .add(new Item(p.getNombre(), p.getPresentacion(), p.getMarca(),
                                porcentaje == null ? null : Precios.precio(p.getCosto(), porcentaje))));
        List<Grupo> grupos = porCategoria.entrySet().stream().map(e -> new Grupo(e.getKey(), e.getValue())).toList();

        NegocioDto datos = negocio.obtener();
        LocalDate hoy = stock.hoy();
        String archivo = (cliente == null ? "catalogo-" : "lista-de-precios-") + hoy;
        return new Catalogo(datos.nombre(), contacto(datos), cliente == null ? null : cliente.getNombre(), hoy,
                grupos, archivo);
    }

    /** "Av. Principal 123 · Tel. 987 654 321" (solo lo que exista). */
    private static String contacto(NegocioDto datos) {
        String telefono = datos.telefono() == null || datos.telefono().isBlank() ? null : "Tel. " + datos.telefono();
        String direccion = datos.direccion() == null || datos.direccion().isBlank() ? null : datos.direccion();
        if (direccion == null) {
            return telefono == null ? "" : telefono;
        }
        return telefono == null ? direccion : direccion + " · " + telefono;
    }
}
