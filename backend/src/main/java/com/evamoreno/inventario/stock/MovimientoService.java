package com.evamoreno.inventario.stock;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.producto.ProductoRepository;

/**
 * Entradas, salidas y ajustes de stock. Es el ÚNICO lugar que modifica la cantidad de un lote,
 * y cada cambio queda registrado como un Movimiento.
 */
@Service
@Transactional
public class MovimientoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final ProductoRepository productos;
    private final LoteRepository lotes;
    private final MovimientoRepository movimientos;
    private final StockService stock;

    public MovimientoService(ProductoRepository productos, LoteRepository lotes, MovimientoRepository movimientos,
            StockService stock) {
        this.productos = productos;
        this.lotes = lotes;
        this.movimientos = movimientos;
        this.stock = stock;
    }

    // ------------------------------------------------------------------ entrada

    public ResultadoMovimiento registrarEntrada(EntradaRequest r) {
        Producto producto = productoActivo(r.productoId());
        String numeroLote = normalizarNumeroLote(r.numeroLote());

        Lote lote = lotes.buscarPorNumeroBloqueado(producto.getId(), numeroLote)
                .map(existente -> validarMismoVencimiento(existente, r.fechaVencimiento()))
                .orElseGet(() -> lotes.save(new Lote(producto, numeroLote, r.fechaVencimiento())));

        lote.sumar(r.cantidad());
        Movimiento movimiento = movimientos.save(
                new Movimiento(lote, TipoMovimiento.ENTRADA, r.cantidad(), TextoBusqueda.limpiar(r.nota())));
        return resultado(producto.getId(), List.of(movimiento));
    }

    // ------------------------------------------------------------------ salida

    /**
     * Saca unidades por FEFO (o de un lote específico). Los lotes vencidos nunca se despachan.
     * Si no alcanza el stock, no se saca nada (409).
     */
    public ResultadoMovimiento registrarSalida(SalidaRequest r) {
        Producto producto = productoActivo(r.productoId());
        LocalDate hoy = stock.hoy();

        List<Lote> candidatos = r.loteId() == null
                ? lotes.disponiblesParaSalidaBloqueados(producto.getId(), hoy)
                : List.of(loteDespachable(r.loteId(), producto, hoy));

        PlanFefo plan = PlanFefo.calcular(candidatos, r.cantidad());
        if (!plan.alcanza()) {
            throw new ConflictoException("Stock insuficiente de \"%s\": se pidieron %d y hay %d disponibles%s"
                    .formatted(producto.getNombre(), r.cantidad(), plan.cantidadCubierta(),
                            r.loteId() == null ? "" : " en ese lote"));
        }

        String nota = TextoBusqueda.limpiar(r.nota());
        List<Movimiento> registrados = new ArrayList<>();
        for (PlanFefo.Asignacion asignacion : plan.asignaciones()) {
            asignacion.lote().restar(asignacion.cantidad());
            registrados.add(movimientos.save(
                    new Movimiento(asignacion.lote(), TipoMovimiento.SALIDA, asignacion.cantidad(), nota)));
        }
        return resultado(producto.getId(), registrados);
    }

    // ------------------------------------------------------------------ ajuste

    public ResultadoMovimiento registrarAjuste(AjusteRequest r) {
        Lote lote = lotes.buscarBloqueado(r.loteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el lote con id " + r.loteId()));
        int diferencia = r.cantidadReal() - lote.getCantidadActual();
        if (diferencia == 0) {
            throw new ConflictoException("El lote ya tiene " + lote.getCantidadActual()
                    + " unidades: no hay nada que ajustar");
        }
        lote.fijarCantidad(r.cantidadReal());
        Movimiento movimiento = movimientos.save(
                new Movimiento(lote, TipoMovimiento.AJUSTE, diferencia, TextoBusqueda.limpiar(r.nota())));
        return resultado(lote.getProducto().getId(), List.of(movimiento));
    }

    // ------------------------------------------------------------------ historial

    @Transactional(readOnly = true)
    public Pagina<MovimientoResponse> historial(Long productoId, int pagina, int tamano) {
        if (!productos.existsById(productoId)) {
            throw new RecursoNoEncontradoException("No existe el producto con id " + productoId);
        }
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
        return Pagina.de(movimientos.historialDeProducto(productoId, pageRequest), MovimientoResponse::de);
    }

    // ------------------------------------------------------------------ reglas

    /** Número de lote sin espacios y en mayúsculas ("l230415 " = "L230415"); vacío = sin lote. */
    public static String normalizarNumeroLote(String numeroLote) {
        if (numeroLote == null || numeroLote.isBlank()) {
            return null;
        }
        return TextoBusqueda.limpiar(numeroLote).toUpperCase(Locale.ROOT);
    }

    private Producto productoActivo(Long productoId) {
        Producto producto = productos.findById(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el producto con id " + productoId));
        if (!producto.isActivo()) {
            throw new ConflictoException("El producto " + producto.getCodigo() + " está desactivado");
        }
        return producto;
    }

    private Lote validarMismoVencimiento(Lote existente, LocalDate fechaVencimiento) {
        if (!Objects.equals(existente.getFechaVencimiento(), fechaVencimiento)) {
            throw new ConflictoException("El lote %s ya está registrado con vencimiento %s; revisa la fecha"
                    .formatted(existente.getNumeroLote() == null ? "(sin número)" : existente.getNumeroLote(),
                            formatear(existente.getFechaVencimiento())));
        }
        return existente;
    }

    private Lote loteDespachable(Long loteId, Producto producto, LocalDate hoy) {
        Lote lote = lotes.buscarBloqueado(loteId)
                .filter(l -> l.getProducto().getId().equals(producto.getId()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El lote " + loteId + " no existe o no pertenece al producto " + producto.getCodigo()));
        if (lote.estaVencido(hoy)) {
            throw new ConflictoException("El lote " + lote.getNumeroLote() + " venció el "
                    + formatear(lote.getFechaVencimiento()) + " y no se puede despachar");
        }
        return lote;
    }

    private ResultadoMovimiento resultado(Long productoId, List<Movimiento> registrados) {
        return new ResultadoMovimiento(productoId,
                registrados.stream().map(MovimientoResponse::de).toList(),
                stock.disponible(productoId));
    }

    private static String formatear(LocalDate fecha) {
        return fecha == null ? "(sin fecha)" : fecha.format(FECHA);
    }
}
