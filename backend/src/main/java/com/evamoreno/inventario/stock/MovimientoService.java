package com.evamoreno.inventario.stock;

import java.math.BigDecimal;
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
import com.evamoreno.inventario.compra.RecordatoriosDeCompra;
import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.producto.ProductoRepository;

/**
 * Entradas, salidas y ajustes de stock. Es el ÚNICO lugar que modifica la cantidad de un lote,
 * y cada cambio queda registrado como un Movimiento.
 */
@Service
@Transactional
public class MovimientoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final ProductoRepository productos;
    private final LoteRepository lotes;
    private final MovimientoRepository movimientos;
    private final StockService stock;
    private final RecordatoriosDeCompra recordatoriosDeCompra;

    public MovimientoService(ProductoRepository productos, LoteRepository lotes, MovimientoRepository movimientos,
            StockService stock, RecordatoriosDeCompra recordatoriosDeCompra) {
        this.productos = productos;
        this.lotes = lotes;
        this.movimientos = movimientos;
        this.stock = stock;
        this.recordatoriosDeCompra = recordatoriosDeCompra;
    }

    // ------------------------------------------------------------------ entrada

    /** Una compra que se registra en la app. */
    public ResultadoMovimiento registrarEntrada(EntradaRequest r) {
        return registrarEntrada(r, false);
    }

    /** Stock con el que se empieza (importación del Excel): no cuenta como compra en el resumen. */
    public ResultadoMovimiento registrarEntradaInicial(EntradaRequest r) {
        return registrarEntrada(r, true);
    }

    private ResultadoMovimiento registrarEntrada(EntradaRequest r, boolean inicial) {
        Producto producto = productoActivo(r.productoId());
        String numeroLote = normalizarNumeroLote(r.numeroLote());
        LocalDate vencimiento = Vencimientos.finDeMes(r.fechaVencimiento()); // solo cuentan el mes y el año

        Lote lote = lotes.buscarPorNumeroBloqueado(producto.getId(), numeroLote)
                .map(existente -> validarMismoVencimiento(existente, vencimiento))
                .orElseGet(() -> lotes.save(new Lote(producto, numeroLote, vencimiento)));

        lote.sumar(r.cantidad(), r.costo());
        Movimiento movimiento = movimientos.save(
                Movimiento.entrada(lote, r.cantidad(), r.costo(), TextoBusqueda.limpiar(r.nota()), inicial));
        if (r.costo() != null) {
            producto.setCosto(r.costo()); // el último costo de compra: con él se calculan los precios nuevos
        }
        if (!inicial) {
            recordatoriosDeCompra.quitar(producto.getId()); // si estaba agregado a mano en Por comprar, ya llegó
        }
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

    /**
     * Salida FEFO de un ítem de pedido, al despachar. Devuelve las salidas generadas (una por lote),
     * que quedan vinculadas al ítem para saber qué lotes se entregaron.
     */
    public List<Movimiento> registrarSalidaDePedido(Long productoId, int cantidad, Long pedidoItemId, String nota) {
        Producto producto = productoActivo(productoId);
        PlanFefo plan = PlanFefo.calcular(lotes.disponiblesParaSalidaBloqueados(productoId, stock.hoy()), cantidad);
        if (!plan.alcanza()) {
            throw new ConflictoException("Stock insuficiente de " + producto.getNombre() + ": se pidieron "
                    + cantidad + " y hay " + plan.cantidadCubierta() + " disponibles");
        }
        List<Movimiento> registrados = new ArrayList<>();
        for (PlanFefo.Asignacion asignacion : plan.asignaciones()) {
            asignacion.lote().restar(asignacion.cantidad());
            registrados.add(movimientos.save(new Movimiento(asignacion.lote(), TipoMovimiento.SALIDA,
                    asignacion.cantidad(), nota, pedidoItemId)));
        }
        return registrados;
    }

    /**
     * Salida de un ítem de pedido desde un lote elegido (al corregir de qué lotes salió). El lote debe ser del
     * producto, no estar vencido y tener las unidades.
     */
    public Movimiento registrarSalidaDeLoteDePedido(Long loteId, Long productoId, int cantidad, Long pedidoItemId,
            String nota) {
        Producto producto = productoActivo(productoId);
        Lote lote = loteDespachable(loteId, producto, stock.hoy());
        if (lote.getCantidadActual() < cantidad) {
            throw new ConflictoException("Del lote %s de \"%s\" solo hay %d: no pueden salir %d más"
                    .formatted(nombre(lote), producto.getNombre(), lote.getCantidadActual(), cantidad));
        }
        lote.restar(cantidad);
        return movimientos.save(new Movimiento(lote, TipoMovimiento.SALIDA, cantidad, nota, pedidoItemId));
    }

    /**
     * Unidades de un pedido despachado que el cliente devolvió: vuelven a ese lote con el costo con que
     * habían salido, y quedan vinculadas al ítem del pedido.
     */
    public Movimiento registrarDevolucionDePedido(Long loteId, int cantidad, BigDecimal costo, Long pedidoItemId,
            String nota) {
        Lote lote = lotes.buscarBloqueado(loteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el lote con id " + loteId));
        lote.sumar(cantidad, costo);
        return movimientos.save(Movimiento.devolucion(lote, cantidad, costo, nota, pedidoItemId));
    }

    // ------------------------------------------------------------------ ajuste

    public ResultadoMovimiento registrarAjuste(AjusteRequest r) {
        Lote lote = lotes.buscarBloqueado(r.loteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el lote con id " + r.loteId()));
        if (r.cantidadReal() == lote.getCantidadActual()) {
            throw new ConflictoException("El lote ya tiene " + lote.getCantidadActual()
                    + " unidades: no hay nada que ajustar");
        }
        Movimiento movimiento = ajustar(lote, r.cantidadReal(), TextoBusqueda.limpiar(r.nota()));
        return resultado(lote.getProducto().getId(), List.of(movimiento));
    }

    private Movimiento ajustar(Lote lote, int cantidadReal, String nota) {
        int diferencia = cantidadReal - lote.getCantidadActual();
        lote.fijarCantidad(cantidadReal);
        return movimientos.save(new Movimiento(lote, TipoMovimiento.AJUSTE, diferencia, nota));
    }

    // ------------------------------------------------------------------ corrección de un lote

    /**
     * Corrige un lote mal registrado. Si la cantidad cambia, queda un AJUSTE en el historial; el número y
     * el vencimiento se corrigen sin movimiento (también en las notas ya despachadas, que leen el lote).
     * Si el número nuevo ya es de otro lote del producto, los dos se unen en ese otro (con 409 si tiene
     * unidades y no se confirmó con {@code unir}).
     *
     * @return el lote como quedó (el otro, si se unieron)
     */
    public LoteResponse corregirLote(Long loteId, EdicionLoteRequest r) {
        Lote lote = lotes.buscarBloqueado(loteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el lote con id " + loteId));
        String numeroLote = normalizarNumeroLote(r.numeroLote());
        LocalDate vencimiento = Vencimientos.finDeMes(r.fechaVencimiento());

        Lote otro = Objects.equals(numeroLote, lote.getNumeroLote()) ? null
                : lotes.buscarPorNumeroBloqueado(lote.getProducto().getId(), numeroLote).orElse(null);
        if (otro != null && otro.getCantidadActual() > 0 && !Boolean.TRUE.equals(r.unir())) {
            int hay = otro.getCantidadActual();
            throw new ConflictoException("Ya existe el lote %s en este producto, con %d %s (%s). "
                    .formatted(nombre(otro), hay, hay == 1 ? "unidad" : "unidades",
                            otro.getFechaVencimiento() == null ? "sin vencimiento"
                                    : "vence " + formatear(otro.getFechaVencimiento()))
                    + "Confirma para unir los dos en uno solo.");
        }

        if (r.cantidad() != lote.getCantidadActual()) {
            String nota = TextoBusqueda.limpiar(r.nota());
            ajustar(lote, r.cantidad(), nota == null || nota.isEmpty() ? "Corrección del lote" : nota);
        }
        if (otro == null) {
            lote.corregir(numeroLote, vencimiento);
            return LoteResponse.de(lote, stock.hoy());
        }
        // Eran el mismo lote: queda uno solo, con las unidades y el historial de los dos
        otro.absorber(lote);
        otro.corregir(numeroLote, vencimiento);
        movimientos.findByLoteId(lote.getId()).forEach(m -> m.pasarAlLote(otro));
        lotes.delete(lote);
        return LoteResponse.de(otro, stock.hoy());
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
            throw new ConflictoException("El lote " + lote.getNumeroLote() + " venció en "
                    + formatear(lote.getFechaVencimiento()) + " y no se puede despachar");
        }
        return lote;
    }

    private ResultadoMovimiento resultado(Long productoId, List<Movimiento> registrados) {
        return new ResultadoMovimiento(productoId,
                registrados.stream().map(MovimientoResponse::de).toList(),
                stock.disponible(productoId));
    }

    private static String nombre(Lote lote) {
        return lote.getNumeroLote() == null ? "(sin número)" : lote.getNumeroLote();
    }

    private static String formatear(LocalDate fecha) {
        return fecha == null ? "(sin fecha)" : fecha.format(FECHA);
    }
}
