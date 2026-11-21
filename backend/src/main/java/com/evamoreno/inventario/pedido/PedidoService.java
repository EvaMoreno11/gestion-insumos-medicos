package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.cliente.Cliente;
import com.evamoreno.inventario.cliente.ClienteLocal;
import com.evamoreno.inventario.cliente.ClienteRepository;
import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.common.SolicitudInvalidaException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.negocio.NegocioDto;
import com.evamoreno.inventario.negocio.NegocioService;
import com.evamoreno.inventario.pedido.PedidoDto.ActualizarRequest;
import com.evamoreno.inventario.pedido.PedidoDto.CambioItem;
import com.evamoreno.inventario.pedido.PedidoDto.CobroResponse;
import com.evamoreno.inventario.pedido.PedidoDto.CorreccionRequest;
import com.evamoreno.inventario.pedido.PedidoDto.CorreccionResponse;
import com.evamoreno.inventario.pedido.PedidoDto.CrearRequest;
import com.evamoreno.inventario.pedido.PedidoDto.Detalle;
import com.evamoreno.inventario.pedido.PedidoDto.Disponibilidad;
import com.evamoreno.inventario.pedido.PedidoDto.Grupo;
import com.evamoreno.inventario.pedido.PedidoDto.ItemActualizarRequest;
import com.evamoreno.inventario.pedido.PedidoDto.ItemNoRegistrado;
import com.evamoreno.inventario.pedido.PedidoDto.ItemRequest;
import com.evamoreno.inventario.pedido.PedidoDto.ItemResponse;
import com.evamoreno.inventario.pedido.PedidoDto.LoteAsignado;
import com.evamoreno.inventario.pedido.PedidoDto.LoteDevuelto;
import com.evamoreno.inventario.pedido.PedidoDto.LoteEntregado;
import com.evamoreno.inventario.pedido.PedidoDto.Numeracion;
import com.evamoreno.inventario.pedido.PedidoDto.NumeracionRequest;
import com.evamoreno.inventario.pedido.PedidoDto.PagoRequest;
import com.evamoreno.inventario.pedido.PedidoDto.PagoResponse;
import com.evamoreno.inventario.pedido.PedidoDto.PedidoDelGrupo;
import com.evamoreno.inventario.pedido.PedidoDto.PedidoQueEspera;
import com.evamoreno.inventario.pedido.PedidoDto.PorComprar;
import com.evamoreno.inventario.pedido.PedidoDto.ProductoPorComprar;
import com.evamoreno.inventario.pedido.PedidoDto.Resumen;
import com.evamoreno.inventario.precio.CategoriaPrecioService;
import com.evamoreno.inventario.precio.Precios;
import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.producto.ProductoRepository;
import com.evamoreno.inventario.stock.Lote;
import com.evamoreno.inventario.stock.Movimiento;
import com.evamoreno.inventario.stock.MovimientoRepository;
import com.evamoreno.inventario.stock.MovimientoService;
import com.evamoreno.inventario.stock.StockService;
import com.evamoreno.inventario.stock.TipoMovimiento;

/**
 * Pedidos: se arman como borrador (con precios por cliente y aviso de faltantes),
 * y al despachar se descuentan los lotes por FEFO y se asigna el número de nota.
 * Si faltan unidades, se puede entregar lo que hay: lo que falta pasa a un pedido nuevo (saldo).
 */
@Service
@Transactional
public class PedidoService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final DateTimeFormatter MES_ANIO = DateTimeFormatter.ofPattern("MM/yyyy");

    private final PedidoRepository pedidos;
    private final ClienteRepository clientes;
    private final ProductoRepository productos;
    private final StockService stock;
    private final MovimientoService movimientoService;
    private final MovimientoRepository movimientos;
    private final CategoriaPrecioService categoriasPrecio;
    private final NegocioService negocio;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidos, ClienteRepository clientes, ProductoRepository productos,
            StockService stock, MovimientoService movimientoService, MovimientoRepository movimientos,
            CategoriaPrecioService categoriasPrecio, NegocioService negocio, Clock clock) {
        this.pedidos = pedidos;
        this.clientes = clientes;
        this.productos = productos;
        this.stock = stock;
        this.movimientoService = movimientoService;
        this.movimientos = movimientos;
        this.categoriasPrecio = categoriasPrecio;
        this.negocio = negocio;
        this.clock = clock;
    }

    // ================================================================== consultas

    @Transactional(readOnly = true)
    public Pagina<Resumen> listar(EstadoPedido estado, Long clienteId, int pagina, int tamano) {
        Page<Pedido> page = pedidos.buscar(estado, clienteId,
                PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA)));
        List<PedidoItem> itemsDeBorradores = page.getContent().stream()
                .filter(p -> p.getEstado() == EstadoPedido.BORRADOR)
                .flatMap(p -> p.getItems().stream())
                .toList();
        Map<Long, Integer> asignado = repartoPara(itemsDeBorradores);
        List<Long> origenes = page.getContent().stream().map(Pedido::getOrigenId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> numerosDeOrigen = new LinkedHashMap<>();
        pedidos.findAllById(origenes).forEach(o -> numerosDeOrigen.put(o.getId(), o.getNumero()));
        return Pagina.de(page, p -> resumen(p, asignado, numerosDeOrigen.get(p.getOrigenId())));
    }

    @Transactional(readOnly = true)
    public Detalle obtener(Long id) {
        return detalle(buscar(id));
    }

    /**
     * Lista universal "Por comprar": lo que falta de cada producto sumando todos los pedidos pendientes,
     * con el detalle de qué pedidos lo esperan, más los ítems a mano (que no están en el catálogo).
     */
    @Transactional(readOnly = true)
    public PorComprar porComprar() {
        List<PedidoItem> pendientes = pedidos.itemsPendientes();
        List<PedidoItem> registrados = pendientes.stream().filter(i -> !i.esManual()).toList();
        Map<Long, Integer> disponibles = stock.disponiblePorProducto(idsDeProductos(registrados));
        Map<Long, Integer> asignado = RepartoDeStock.repartir(demandas(registrados), disponibles);

        Map<Producto, List<PedidoItem>> porProducto = registrados.stream()
                .collect(Collectors.groupingBy(PedidoItem::getProducto, LinkedHashMap::new, Collectors.toList()));
        List<ProductoPorComprar> faltantes = new ArrayList<>();
        BigDecimal costoTotal = BigDecimal.ZERO;
        int sinCosto = 0;
        for (Map.Entry<Producto, List<PedidoItem>> grupo : porProducto.entrySet()) {
            Producto producto = grupo.getKey();
            List<PedidoItem> items = grupo.getValue();
            int pedido = items.stream().mapToInt(PedidoItem::getCantidad).sum();
            int faltante = items.stream().mapToInt(i -> faltante(i, asignado)).sum();
            if (faltante == 0) {
                continue;
            }
            // Cuánto costaría comprar lo que falta, con el último costo de compra del producto
            if (producto.getCosto() == null) {
                sinCosto++;
            } else {
                costoTotal = costoTotal.add(producto.getCosto().multiply(BigDecimal.valueOf(faltante)));
            }
            faltantes.add(new ProductoPorComprar(producto.getId(), producto.getCodigo(), producto.getNombre(),
                    producto.getMarca(), producto.getPresentacion(),
                    disponibles.getOrDefault(producto.getId(), 0), pedido, faltante,
                    items.stream().filter(i -> faltante(i, asignado) > 0)
                            .map(i -> new PedidoQueEspera(i.getPedido().getId(),
                                    i.getPedido().getCliente().getNombre(), i.getPedido().getLocal().getNombre(),
                                    i.getCantidad(), faltante(i, asignado)))
                            .toList()));
        }
        faltantes.sort((a, b) -> a.nombre().compareToIgnoreCase(b.nombre()));

        List<ItemNoRegistrado> noRegistrados = pendientes.stream().filter(PedidoItem::esManual)
                .map(i -> new ItemNoRegistrado(i.getPedido().getId(), i.getId(), i.getPedido().getCliente().getNombre(),
                        i.getPedido().getLocal().getNombre(), i.getDescripcion(), i.getPresentacion(), i.getMarca(),
                        i.getCantidad()))
                .toList();
        return new PorComprar(faltantes, noRegistrados, costoTotal.setScale(2, RoundingMode.HALF_UP), sinCosto);
    }

    // ================================================================== cabecera

    public Detalle crear(CrearRequest r) {
        Cliente cliente = clientes.findWithLocalesById(r.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente con id " + r.clienteId()));
        if (!cliente.isActivo()) {
            throw new ConflictoException("El cliente \"" + cliente.getNombre() + "\" está desactivado");
        }
        BigDecimal porcentaje = r.porcentaje() != null ? r.porcentaje() : cliente.getPorcentaje();
        if (porcentaje == null) {
            throw new ConflictoException("El cliente \"" + cliente.getNombre()
                    + "\" aún no tiene porcentaje: elige con cuál se le venderá");
        }
        guardarPorcentajeSiCorresponde(cliente, porcentaje, r.guardarPorcentajeEnCliente());

        Pedido pedido = new Pedido(cliente, elegirLocal(cliente, r.localId()), porcentaje, limpiar(r.nota()),
                r.documento() == null ? DocumentoPedido.NOTA : r.documento());
        return detalle(pedidos.saveAndFlush(pedido));
    }

    public Detalle actualizar(Long id, ActualizarRequest r) {
        Pedido pedido = buscar(id);
        pedido.exigirBorrador();

        ClienteLocal local = r.localId() == null ? pedido.getLocal() : elegirLocal(pedido.getCliente(), r.localId());
        BigDecimal porcentaje = r.porcentaje() == null ? pedido.getPorcentaje() : r.porcentaje();
        boolean cambioPorcentaje = porcentaje.compareTo(pedido.getPorcentaje()) != 0;
        pedido.actualizar(local, porcentaje, limpiar(r.nota()),
                r.documento() == null ? pedido.getDocumento() : r.documento());
        guardarPorcentajeSiCorresponde(pedido.getCliente(), porcentaje, r.guardarPorcentajeEnCliente());

        if (cambioPorcentaje) {
            // Se recalculan los precios automáticos; los escritos a mano se respetan
            pedido.getItems().stream()
                    .filter(item -> !item.esManual() && !item.isPrecioManual())
                    .forEach(item -> item.fijarPrecio(precioAutomatico(pedido, item.getProducto()), false));
        }
        return detalle(pedido);
    }

    public Detalle anular(Long id) {
        Pedido pedido = buscar(id);
        pedido.exigirBorrador();
        pedido.anular();
        return detalle(pedido);
    }

    // ================================================================== ítems

    public Detalle agregarItem(Long pedidoId, ItemRequest r) {
        Pedido pedido = buscar(pedidoId);
        pedido.exigirBorrador();
        pedido.getItems().add(nuevoItem(pedido, r));
        return detalle(pedido);
    }

    /** Ítem nuevo (del catálogo o escrito a mano) con su precio; todavía no se agrega al pedido. */
    private PedidoItem nuevoItem(Pedido pedido, ItemRequest r) {
        PedidoItem item;
        if (r.productoId() != null) {
            Producto producto = productos.findById(r.productoId()).orElseThrow(
                    () -> new RecursoNoEncontradoException("No existe el producto con id " + r.productoId()));
            if (!producto.isActivo()) {
                throw new ConflictoException("El producto " + producto.getCodigo() + " está desactivado");
            }
            if (pedido.getItems().stream()
                    .anyMatch(i -> !i.esManual() && i.getProducto().getId().equals(producto.getId()))) {
                throw new ConflictoException("\"" + producto.getNombre()
                        + "\" ya está en el pedido: cambia su cantidad");
            }
            item = PedidoItem.deProducto(pedido, producto, r.cantidad());
            BigDecimal anterior = r.precio() != null ? null : precioAnteriorDelCliente(pedido, producto);
            if (r.precio() != null) {
                item.fijarPrecio(r.precio(), true);
            } else if (anterior != null) {
                item.fijarPrecio(anterior, true); // el precio que se le puso a mano a este cliente la última vez
            } else {
                item.fijarPrecio(precioAutomatico(pedido, producto), false);
            }
        } else {
            if (r.descripcion() == null || r.descripcion().isBlank()) {
                throw new SolicitudInvalidaException("Indica el producto del catálogo o la descripción del ítem a mano");
            }
            item = PedidoItem.manual(pedido, TextoBusqueda.limpiar(r.descripcion()), limpiar(r.presentacion()),
                    limpiar(r.marca()), r.cantidad());
            item.fijarPrecio(r.precio(), r.precio() != null);
        }
        return item;
    }

    public Detalle actualizarItem(Long pedidoId, Long itemId, ItemActualizarRequest r) {
        Pedido pedido = buscar(pedidoId);
        pedido.exigirBorrador();
        PedidoItem item = buscarItem(pedido, itemId);

        item.setCantidad(r.cantidad());
        if (r.precio() != null) {
            item.fijarPrecio(r.precio(), true);
        } else if (Boolean.TRUE.equals(r.precioAutomatico())) {
            item.fijarPrecio(item.esManual() ? null : precioAutomatico(pedido, item.getProducto()), false);
        }
        if (item.esManual() && r.descripcion() != null && !r.descripcion().isBlank()) {
            item.setDatosManuales(TextoBusqueda.limpiar(r.descripcion()), limpiar(r.presentacion()),
                    limpiar(r.marca()));
        }
        return detalle(pedido);
    }

    public Detalle eliminarItem(Long pedidoId, Long itemId) {
        Pedido pedido = buscar(pedidoId);
        pedido.exigirBorrador();
        pedido.getItems().remove(buscarItem(pedido, itemId));
        return detalle(pedido);
    }

    /**
     * Un ítem escrito a mano pasa a ser un producto del catálogo (recién registrado o que ya existía):
     * conserva la cantidad y el precio que se le había puesto; si no tenía precio, se calcula.
     */
    public Detalle vincularProducto(Long pedidoId, Long itemId, Long productoId) {
        Pedido pedido = buscar(pedidoId);
        pedido.exigirBorrador();
        PedidoItem item = buscarItem(pedido, itemId);
        if (!item.esManual()) {
            throw new ConflictoException("\"" + item.getDescripcion() + "\" ya es un producto del inventario");
        }
        Producto producto = productos.findById(productoId).orElseThrow(
                () -> new RecursoNoEncontradoException("No existe el producto con id " + productoId));
        if (!producto.isActivo()) {
            throw new ConflictoException("El producto " + producto.getCodigo() + " está desactivado");
        }
        if (pedido.getItems().stream().anyMatch(i -> !i.esManual() && i.getProducto().getId().equals(productoId))) {
            throw new ConflictoException("\"" + producto.getNombre()
                    + "\" ya está en el pedido: súmale la cantidad y quita el ítem escrito a mano");
        }
        item.vincular(producto);
        if (item.getPrecioUnitario() == null) {
            item.fijarPrecio(precioAutomatico(pedido, producto), false);
        } else {
            item.fijarPrecio(item.getPrecioUnitario(), true); // se respeta el precio que se le dio al cliente
        }
        return detalle(pedido);
    }

    // ================================================================== despacho

    /**
     * Entrega el pedido: descuenta los lotes por FEFO y, si es una nota de pedido, le asigna su número
     * ("Solo tabla" no consume la numeración). Todo en una transacción: o se despacha o no cambia nada.
     *
     * @param entregarLoDisponible si faltan unidades: false = no se despacha; true = se entrega lo que hay
     *                             y lo que falta pasa a un pedido nuevo (saldo) con la prioridad del original
     */
    public Detalle despachar(Long id, boolean entregarLoDisponible) {
        Pedido pedido = buscar(id);
        pedido.exigirBorrador();
        pedidos.flush();
        if (pedido.getItems().isEmpty()) {
            throw new ConflictoException("El pedido no tiene ítems");
        }
        List<String> sinPrecio = pedido.getItems().stream().filter(i -> i.getPrecioUnitario() == null)
                .map(PedidoItem::getDescripcion).toList();
        if (!sinPrecio.isEmpty()) {
            throw new ConflictoException("Falta el precio de: " + String.join("; ", sinPrecio));
        }
        Map<Long, Integer> asignado = repartoPara(pedido.getItems());
        List<PedidoItem> incompletos = pedido.getItems().stream().filter(i -> faltante(i, asignado) > 0).toList();
        if (!incompletos.isEmpty()) {
            if (!entregarLoDisponible) {
                throw new ConflictoException("No se puede despachar: faltan unidades de " + incompletos.stream()
                        .map(i -> i.getDescripcion() + " (faltan " + faltante(i, asignado) + ")")
                        .collect(Collectors.joining("; "))
                        + ". Para entregar lo que hay y dejar el resto pendiente, confirma la entrega parcial");
            }
            if (!hayAlgoQueEntregar(pedido, asignado)) {
                throw new ConflictoException(
                        "No hay nada que entregar todavía: no queda stock de ningún producto del pedido");
            }
            separarSaldo(pedido, incompletos, asignado);
        }

        String numero = pedido.getDocumento() == DocumentoPedido.NOTA
                ? pedidos.numeracionBloqueada().tomarNumero()
                : null;
        String referencia = numero != null ? "Nota de pedido " + numero : "Pedido " + pedido.getId() + " (solo tabla)";
        for (PedidoItem item : pedido.getItems()) {
            if (!item.esManual()) {
                movimientoService.registrarSalidaDePedido(item.getProducto().getId(), item.getCantidad(),
                        item.getId(), referencia);
            }
        }
        pedido.marcarDespachado(numero, Instant.now(clock));
        pedido.definirPlazo(pedido.getCliente().getPlazoDias()); // plazo habitual del cliente (o sin plazo)
        return detalle(pedido);
    }

    // ================================================================== corrección

    /**
     * Corrige un pedido despachado: cantidades (0 = quitar), precios, nuevos ítems y de qué lotes salió cada
     * ítem, con un motivo opcional. Lo que se devuelve vuelve al lote del que salió; lo que se agrega sale por
     * FEFO, salvo que se indique el reparto por lotes. Todo en una transacción: si algo falla (por ejemplo, no
     * hay stock para agregar), no cambia nada.
     */
    public Detalle corregir(Long id, CorreccionRequest r) {
        Pedido pedido = buscar(id);
        pedido.exigirDespachado();
        pedidos.flush();
        BigDecimal totalAntes = total(pedido);
        String referencia = "Corrección de " + (pedido.getNumero() != null ? "la nota " + pedido.getNumero()
                : "el pedido " + pedido.getId());
        Map<Long, List<LoteNeto>> entregado = entregadoPorItem(pedido);
        List<String> detalle = new ArrayList<>();

        List<CambioItem> cambios = r.cambios() == null ? List.of() : r.cambios();
        if (cambios.stream().map(CambioItem::itemId).distinct().count() < cambios.size()) {
            throw new SolicitudInvalidaException("Un ítem aparece dos veces en la corrección");
        }
        List<PedidoItem> quitadosAMano = new ArrayList<>();
        boolean soloLotes = true; // para el motivo por omisión
        for (CambioItem c : cambios) {
            PedidoItem item = buscarItem(pedido, c.itemId());
            int antes = item.getCantidad();
            int despues = c.cantidad();
            soloLotes &= antes == despues;
            if (c.reparto() != null) {
                if (item.esManual()) {
                    throw new SolicitudInvalidaException("\"" + item.getDescripcion()
                            + "\" se escribió a mano: no tiene lotes");
                }
                String cambio = repartir(item, antes, despues, c.reparto(),
                        entregado.getOrDefault(item.getId(), List.of()), referencia);
                if (cambio != null) {
                    detalle.add(cambio);
                }
            } else if (despues < antes) {
                String lotes = item.esManual() ? ""
                        : ", vuelven " + devolver(item, antes - despues, c.lotes(),
                                entregado.getOrDefault(item.getId(), List.of()), referencia);
                detalle.add(item.getDescripcion() + ": " + antes + " → " + despues
                        + (despues == 0 ? " (quitado)" : "") + lotes);
            } else if (despues > antes) {
                if (!item.esManual()) {
                    movimientoService.registrarSalidaDePedido(item.getProducto().getId(), despues - antes,
                            item.getId(), referencia);
                }
                detalle.add(item.getDescripcion() + ": " + antes + " → " + despues + " (se entregan "
                        + (despues - antes) + " más)");
            }
            item.setCantidad(despues);
            if (c.precio() != null
                    && (item.getPrecioUnitario() == null || c.precio().compareTo(item.getPrecioUnitario()) != 0)) {
                soloLotes = false;
                detalle.add(item.getDescripcion() + ": precio " + soles(item.getPrecioUnitario()) + " → "
                        + soles(c.precio()));
                item.fijarPrecio(c.precio(), true);
            }
            if (despues == 0 && item.esManual()) {
                quitadosAMano.add(item); // no tiene movimientos: se puede borrar
            }
        }
        pedido.getItems().removeAll(quitadosAMano);

        for (ItemRequest nuevo : r.nuevos() == null ? List.<ItemRequest>of() : r.nuevos()) {
            soloLotes = false;
            PedidoItem item = nuevoItem(pedido, nuevo);
            if (item.getPrecioUnitario() == null) {
                throw new ConflictoException("Falta el precio de \"" + item.getDescripcion() + "\"");
            }
            pedido.getItems().add(item);
            pedidos.flush(); // para que el ítem tenga id antes de su salida
            if (!item.esManual()) {
                movimientoService.registrarSalidaDePedido(item.getProducto().getId(), item.getCantidad(),
                        item.getId(), referencia);
            }
            detalle.add("Agregado: " + item.getDescripcion() + " × " + item.getCantidad() + " a "
                    + soles(item.getPrecioUnitario()));
        }
        if (detalle.isEmpty()) {
            throw new ConflictoException("No hay cambios que guardar");
        }
        pedidos.flush();
        String motivo = TextoBusqueda.limpiar(r.motivo());
        if (motivo == null || motivo.isEmpty()) {
            motivo = soloLotes ? "Cambio de lote" : "Corrección";
        }
        pedido.getCorrecciones().add(new CorreccionPedido(pedido, Instant.now(clock), motivo,
                String.join("\n", detalle), totalAntes, total(pedido)));
        return detalle(pedido);
    }

    /**
     * Deja las unidades entregadas de un ítem repartidas entre lotes como se indica: a los lotes que dan menos
     * les vuelven unidades (con el costo con que salieron) y de los que dan más salen. Devuelve la línea del
     * historial ("Guantes: lotes A (3) + B (97) → C (100)"), o null si el reparto es el mismo.
     */
    private String repartir(PedidoItem item, int antes, int despues, List<LoteAsignado> reparto,
            List<LoteNeto> entregados, String referencia) {
        if (reparto.stream().map(LoteAsignado::loteId).distinct().count() < reparto.size()) {
            throw new SolicitudInvalidaException("Un lote aparece dos veces en el reparto");
        }
        int suma = reparto.stream().mapToInt(LoteAsignado::cantidad).sum();
        if (suma != despues) {
            throw new ConflictoException("\"" + item.getDescripcion() + "\": se entregan " + despues
                    + ", pero en los lotes suman " + suma);
        }
        Map<Long, Integer> nuevo = new LinkedHashMap<>();
        reparto.stream().filter(a -> a.cantidad() > 0).forEach(a -> nuevo.put(a.loteId(), a.cantidad()));
        Map<Long, Integer> actual = new LinkedHashMap<>();
        Map<Long, String> nombres = new HashMap<>();
        for (LoteNeto l : entregados) {
            actual.put(l.lote().getId(), l.cantidad());
            nombres.put(l.lote().getId(), nombreDeLote(l.lote()));
        }
        if (nuevo.equals(actual)) {
            return null;
        }

        for (LoteNeto l : entregados) { // primero vuelve lo que sobra en cada lote
            int sobra = l.cantidad() - nuevo.getOrDefault(l.lote().getId(), 0);
            if (sobra > 0) {
                movimientoService.registrarDevolucionDePedido(l.lote().getId(), sobra, l.costo(), item.getId(),
                        "Cambio de lote · " + referencia);
            }
        }
        nuevo.forEach((loteId, cantidad) -> {
            int falta = cantidad - actual.getOrDefault(loteId, 0);
            if (falta > 0) {
                Movimiento salida = movimientoService.registrarSalidaDeLoteDePedido(loteId,
                        item.getProducto().getId(), falta, item.getId(), "Cambio de lote · " + referencia);
                nombres.put(loteId, nombreDeLote(salida.getLote()));
            }
        });
        String cantidad = antes == despues ? "" : antes + " → " + despues + ", ";
        return item.getDescripcion() + ": " + cantidad + "lotes " + textoDeLotes(actual, nombres) + " → "
                + textoDeLotes(nuevo, nombres);
    }

    private static String textoDeLotes(Map<Long, Integer> lotes, Map<Long, String> nombres) {
        return lotes.isEmpty() ? "ninguno" : lotes.entrySet().stream()
                .map(e -> nombres.get(e.getKey()) + " (" + e.getValue() + ")")
                .collect(Collectors.joining(" + "));
    }

    private static String nombreDeLote(Lote lote) {
        return lote.getNumeroLote() == null ? "sin número" : lote.getNumeroLote();
    }

    /**
     * Devuelve unidades de un ítem a los lotes de los que salieron. Si salió de un solo lote, vuelven a ese;
     * si salió de varios, hay que indicar a cuáles. Devuelve el texto para el historial.
     */
    private String devolver(PedidoItem item, int devueltas, List<LoteDevuelto> indicados, List<LoteNeto> entregados,
            String referencia) {
        List<LoteDevuelto> reparto = indicados == null ? List.of() : indicados;
        if (reparto.isEmpty()) {
            if (entregados.size() != 1) {
                throw new ConflictoException("\"" + item.getDescripcion() + "\" se entregó de " + entregados.size()
                        + " lotes: indica a qué lote vuelven las " + devueltas + " unidades");
            }
            reparto = List.of(new LoteDevuelto(entregados.getFirst().lote().getId(), devueltas));
        }
        if (reparto.stream().map(LoteDevuelto::loteId).distinct().count() < reparto.size()) {
            throw new SolicitudInvalidaException("Un lote aparece dos veces en la devolución");
        }
        int suma = reparto.stream().mapToInt(LoteDevuelto::cantidad).sum();
        if (suma != devueltas) {
            throw new ConflictoException("\"" + item.getDescripcion() + "\": se devuelven " + devueltas
                    + " unidades, pero en los lotes suman " + suma);
        }
        List<String> partes = new ArrayList<>();
        for (LoteDevuelto d : reparto) {
            LoteNeto lote = entregados.stream().filter(l -> l.lote().getId().equals(d.loteId())).findFirst()
                    .orElseThrow(() -> new ConflictoException("El lote " + d.loteId() + " no se entregó en \""
                            + item.getDescripcion() + "\""));
            String nombre = lote.lote().getNumeroLote() == null ? "sin número" : lote.lote().getNumeroLote();
            if (d.cantidad() > lote.cantidad()) {
                throw new ConflictoException("Del lote " + nombre + " se entregaron " + lote.cantidad() + " de \""
                        + item.getDescripcion() + "\": no pueden volver " + d.cantidad());
            }
            movimientoService.registrarDevolucionDePedido(d.loteId(), d.cantidad(), lote.costo(), item.getId(),
                    "Devolución · " + referencia);
            partes.add(d.cantidad() + " al lote " + nombre);
        }
        return String.join(" y ", partes);
    }

    private static String soles(BigDecimal valor) {
        return valor == null ? "sin precio" : "S/ " + String.format(Locale.US, "%,.2f", valor);
    }

    // ================================================================== cobro

    /**
     * Anota cómo paga el cliente este pedido (contado o crédito a N días). El plazo queda como el
     * habitual del cliente: sus próximos pedidos lo toman al despacharse.
     */
    public Detalle definirPlazo(Long id, Integer plazoDias) {
        Pedido pedido = buscar(id);
        pedido.exigirDespachado();
        pedido.definirPlazo(plazoDias);
        if (plazoDias != null) {
            pedido.getCliente().definirPlazo(plazoDias);
        }
        return detalle(pedido);
    }

    public Detalle registrarPago(Long id, PagoRequest r) {
        Pedido pedido = buscar(id);
        pedido.exigirDespachado();
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fecha = r.fecha() == null ? hoy : r.fecha();
        if (fecha.isAfter(hoy)) {
            throw new SolicitudInvalidaException("La fecha del pago no puede ser futura");
        }
        BigDecimal saldo = total(pedido).subtract(pagado(pedido));
        if (saldo.signum() <= 0) {
            throw new ConflictoException("Este pedido ya está pagado");
        }
        if (r.monto().compareTo(saldo) > 0) {
            throw new ConflictoException("El pago (S/ " + r.monto().setScale(2, RoundingMode.HALF_UP)
                    + ") es mayor que lo que falta cobrar (S/ " + saldo + ")");
        }
        pedido.getPagos().add(new Pago(pedido, fecha, r.monto().setScale(2, RoundingMode.HALF_UP), r.medio(),
                limpiar(r.nota())));
        return detalle(pedido);
    }

    /** Para corregir un pago mal registrado. */
    public Detalle eliminarPago(Long id, Long pagoId) {
        Pedido pedido = buscar(id);
        Pago pago = pedido.getPagos().stream().filter(p -> pagoId.equals(p.getId())).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El pago " + pagoId + " no pertenece al pedido " + id));
        pedido.getPagos().remove(pago);
        return detalle(pedido);
    }

    private static BigDecimal pagado(Pedido pedido) {
        return pedido.getPagos().stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private CobroResponse cobro(Pedido pedido) {
        if (pedido.getEstado() != EstadoPedido.DESPACHADO) {
            return null;
        }
        Cobro c = Cobro.de(total(pedido), pagado(pedido), pedido.getPlazoDias(),
                LocalDate.ofInstant(pedido.getDespachadoEn(), clock.getZone()), LocalDate.now(clock));
        return new CobroResponse(c.total(), c.pagado(), c.saldo(), c.plazoDias(), c.vence(), c.estado(), c.dias(),
                pedido.getPagos().stream()
                        .map(p -> new PagoResponse(p.getId(), p.getFecha(), p.getMonto(), p.getMedio(), p.getNota()))
                        .toList());
    }

    /** Hay algo para entregar ya: un ítem a mano o unidades aseguradas de algún producto. */
    private static boolean hayAlgoQueEntregar(Pedido pedido, Map<Long, Integer> asignado) {
        return pedido.getItems().stream()
                .anyMatch(i -> i.esManual() || asignado.getOrDefault(i.getId(), 0) > 0);
    }

    /**
     * Lo que falta de cada ítem pasa a un pedido nuevo (saldo), con el mismo precio. En el pedido que se
     * despacha queda solo lo que hay: así su nota y su total son exactamente lo que se entrega.
     */
    private void separarSaldo(Pedido pedido, List<PedidoItem> incompletos, Map<Long, Integer> asignado) {
        Pedido saldo = Pedido.saldoDe(pedido);
        for (PedidoItem item : incompletos) {
            int falta = faltante(item, asignado);
            saldo.getItems().add(PedidoItem.copiaPara(saldo, item, falta));
            int hay = item.getCantidad() - falta;
            if (hay == 0) {
                pedido.getItems().remove(item);
            } else {
                item.setCantidad(hay);
            }
        }
        pedidos.save(saldo);
        pedidos.flush();
    }

    // ================================================================== nota de pedido

    /**
     * Contenido de la nota (para Excel o PDF). En un borrador va sin número ni lotes.
     * Si el pedido es "Solo tabla", lleva únicamente la tabla con el total.
     */
    @Transactional(readOnly = true)
    public NotaPedido nota(Long id) {
        Pedido pedido = buscar(id);
        if (pedido.getEstado() == EstadoPedido.ANULADO) {
            throw new ConflictoException("El pedido está anulado: no tiene nota");
        }
        Map<Long, List<LoteNeto>> entregado = entregadoPorItem(pedido);
        List<NotaPedido.Fila> filas = new ArrayList<>();
        int numero = 0;
        for (PedidoItem item : pedido.getItems()) {
            if (item.getCantidad() == 0) {
                continue; // se quitó en una corrección
            }
            numero++;
            List<LoteNeto> lotes = entregado.getOrDefault(item.getId(), List.of());
            if (lotes.isEmpty()) {
                filas.add(fila(numero, item, null, null, item.getCantidad()));
                continue;
            }
            boolean primera = true;
            for (LoteNeto lote : lotes) { // una fila por lote; solo la primera lleva número
                LocalDate vence = lote.lote().getFechaVencimiento();
                filas.add(fila(primera ? numero : null, item, lote.lote().getNumeroLote(),
                        vence == null ? null : vence.format(MES_ANIO), lote.cantidad()));
                primera = false;
            }
        }

        boolean borrador = pedido.getEstado() == EstadoPedido.BORRADOR;
        if (pedido.getDocumento() == DocumentoPedido.SOLO_TABLA) {
            // Sin nombres, sin fecha y sin número: ni siquiera se leen los datos del negocio
            return NotaPedido.soloTabla(filas, total(pedido), "pedido-" + pedido.getId(), borrador);
        }
        NegocioDto datos = negocio.obtener();
        ClienteLocal local = pedido.getLocal();
        Instant momento = pedido.getDespachadoEn() != null ? pedido.getDespachadoEn() : Instant.now(clock);
        String archivo = pedido.getNumero() != null ? "nota-pedido-" + pedido.getNumero()
                : "pedido-borrador-" + pedido.getId();
        return new NotaPedido(datos.nombre(), datos.direccion(), datos.telefono(), pedido.getNumero(),
                pedido.getCliente().getNombre(), pedido.getCliente().getRucDni(), local.getNombre(), local.getDireccion(),
                NotaPedido.unir(local.getContacto(), local.getTelefono()),
                LocalDate.ofInstant(momento, clock.getZone()), filas, total(pedido), archivo, borrador, false);
    }

    private static NotaPedido.Fila fila(Integer numero, PedidoItem item, String lote, String vencimiento,
            int cantidad) {
        BigDecimal precio = item.getPrecioUnitario();
        BigDecimal total = precio == null ? null : precio.multiply(BigDecimal.valueOf(cantidad));
        return new NotaPedido.Fila(numero, item.getDescripcion(), item.getPresentacion(), item.getMarca(), lote,
                vencimiento, cantidad, precio, total);
    }

    // ================================================================== numeración

    @Transactional(readOnly = true)
    public Numeracion numeracion() {
        return numeracion(pedidos.numeracion());
    }

    public Numeracion configurarNumeracion(NumeracionRequest r) {
        NumeracionNota numeracion = pedidos.numeracionBloqueada();
        numeracion.configurar(r.serie() == null ? numeracion.getSerie() : r.serie(), r.siguiente());
        return numeracion(numeracion);
    }

    private static Numeracion numeracion(NumeracionNota n) {
        return new Numeracion(n.getSerie(), n.getSiguiente(), NumeracionNota.formatear(n.getSerie(), n.getSiguiente()));
    }

    // ================================================================== reglas internas

    private Pedido buscar(Long id) {
        return pedidos.findConTodoById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido con id " + id));
    }

    private static PedidoItem buscarItem(Pedido pedido, Long itemId) {
        return pedido.getItems().stream().filter(i -> itemId.equals(i.getId())).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El ítem " + itemId + " no pertenece al pedido " + pedido.getId()));
    }

    /** Si el cliente tiene un solo local se usa ese; si tiene varios, hay que elegir. */
    private static ClienteLocal elegirLocal(Cliente cliente, Long localId) {
        List<ClienteLocal> locales = cliente.getLocales();
        if (localId == null) {
            if (locales.size() == 1) {
                return locales.getFirst();
            }
            throw new ConflictoException("El cliente \"" + cliente.getNombre() + "\" tiene " + locales.size()
                    + " locales: elige a cuál va el pedido");
        }
        return locales.stream().filter(l -> localId.equals(l.getId())).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El local " + localId + " no pertenece al cliente " + cliente.getNombre()));
    }

    /** El porcentaje se guarda en el cliente la primera vez, o cuando se pide expresamente. */
    private static void guardarPorcentajeSiCorresponde(Cliente cliente, BigDecimal porcentaje, Boolean guardar) {
        if (cliente.getPorcentaje() == null || Boolean.TRUE.equals(guardar)) {
            cliente.definirPorcentaje(porcentaje);
        }
    }

    /**
     * El último precio escrito a mano para este producto en un pedido del mismo cliente (no anulado), o null si
     * nunca se le puso uno. Así se respetan los precios especiales que ya se le dieron.
     */
    private BigDecimal precioAnteriorDelCliente(Pedido pedido, Producto producto) {
        return pedidos.preciosManualesDelCliente(pedido.getCliente().getId(), producto.getId(),
                pedido.getId() == null ? -1L : pedido.getId(), EstadoPedido.ANULADO, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
    }

    /** Precio = costo + porcentaje del pedido. Null si el producto no tiene costo (habrá que escribirlo). */
    private static BigDecimal precioAutomatico(Pedido pedido, Producto producto) {
        return Precios.precio(producto.getCosto(), pedido.getPorcentaje());
    }

    /**
     * Unidades aseguradas para cada uno de esos ítems, repartiendo el stock entre TODOS los pedidos
     * pendientes que piden los mismos productos, del más antiguo al más nuevo.
     */
    private Map<Long, Integer> repartoPara(Collection<PedidoItem> items) {
        List<Long> ids = idsDeProductos(items);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return RepartoDeStock.repartir(demandas(pedidos.itemsPendientesDe(ids)), stock.disponiblePorProducto(ids));
    }

    private static List<Long> idsDeProductos(Collection<PedidoItem> items) {
        return items.stream().filter(i -> !i.esManual()).map(i -> i.getProducto().getId()).distinct().toList();
    }

    private static List<RepartoDeStock.Demanda> demandas(List<PedidoItem> itemsEnOrden) {
        return itemsEnOrden.stream()
                .map(i -> new RepartoDeStock.Demanda(i.getId(), i.getProducto().getId(), i.getCantidad()))
                .toList();
    }

    private static int faltante(PedidoItem item, Map<Long, Integer> asignado) {
        return item.esManual() ? 0 : item.getCantidad() - asignado.getOrDefault(item.getId(), 0);
    }

    /** Lo que se entregó de un ítem en un lote (salió menos lo devuelto), y el costo con que salió. */
    record LoteNeto(Lote lote, int cantidad, BigDecimal costo) {
    }

    /** Suma lo que salió y lo que volvió de un ítem en un lote. */
    private static final class Acumulado {
        private final Lote lote;
        private int salio;
        private int devuelto;
        private BigDecimal costoTotal = BigDecimal.ZERO;
        private boolean costoConocido = true;

        Acumulado(Lote lote) {
            this.lote = lote;
        }

        LoteNeto neto() {
            BigDecimal costo = !costoConocido || salio == 0 ? null
                    : costoTotal.divide(BigDecimal.valueOf(salio), 4, RoundingMode.HALF_UP);
            return new LoteNeto(lote, salio - devuelto, costo);
        }
    }

    /**
     * Lotes entregados de cada ítem de un pedido despachado, en el orden en que salieron: lo que salió menos
     * lo que se devolvió en correcciones. Solo los lotes en que todavía queda algo entregado.
     */
    private Map<Long, List<LoteNeto>> entregadoPorItem(Pedido pedido) {
        if (pedido.getEstado() != EstadoPedido.DESPACHADO) {
            return Map.of();
        }
        List<Long> ids = pedido.getItems().stream().map(PedidoItem::getId).filter(Objects::nonNull).toList();
        Map<Long, Map<Long, Acumulado>> porItem = new LinkedHashMap<>();
        for (Movimiento m : movimientos.findByPedidoItemIdInOrderById(ids)) {
            Acumulado a = porItem.computeIfAbsent(m.getPedidoItemId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(m.getLote().getId(), k -> new Acumulado(m.getLote()));
            if (m.getTipo() == TipoMovimiento.SALIDA) {
                a.salio += m.getCantidad();
                if (m.getCostoUnitario() == null) {
                    a.costoConocido = false;
                } else {
                    a.costoTotal = a.costoTotal.add(m.getCostoUnitario().multiply(BigDecimal.valueOf(m.getCantidad())));
                }
            } else if (m.getTipo() == TipoMovimiento.DEVOLUCION) {
                a.devuelto += m.getCantidad();
            }
        }
        Map<Long, List<LoteNeto>> resultado = new HashMap<>();
        porItem.forEach((item, lotes) -> resultado.put(item,
                lotes.values().stream().map(Acumulado::neto).filter(n -> n.cantidad() > 0).toList()));
        return resultado;
    }

    private static BigDecimal total(Pedido pedido) {
        return pedido.getItems().stream().map(PedidoItem::total).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    // ================================================================== respuestas

    private Detalle detalle(Pedido pedido) {
        pedidos.flush(); // asegura que los ítems nuevos ya tengan id
        boolean borrador = pedido.getEstado() == EstadoPedido.BORRADOR;
        Map<Long, Integer> asignado = borrador ? repartoPara(pedido.getItems()) : Map.of();
        Map<Long, List<LoteNeto>> entregado = entregadoPorItem(pedido);

        List<ItemResponse> items = pedido.getItems().stream()
                .map(item -> item(item, pedido.getEstado(), asignado, entregado.getOrDefault(item.getId(), List.of())))
                .toList();
        int conFaltante = (int) items.stream().filter(i -> i.faltante() != null && i.faltante() > 0).count();
        int sinPrecio = (int) items.stream().filter(i -> i.precioUnitario() == null).count();
        boolean conPrecios = borrador && !items.isEmpty() && sinPrecio == 0;
        ClienteLocal local = pedido.getLocal();

        List<Pedido> saldos = pedidos.findByOrigenIdOrderById(pedido.idOriginal());
        Pedido original = pedido.esSaldo() ? pedidos.findById(pedido.getOrigenId()).orElseThrow() : pedido;
        return new Detalle(pedido.getId(), pedido.getNumero(), pedido.getEstado(), pedido.getDocumento(),
                pedido.getCliente().getId(), pedido.getCliente().getNombre(), local.getId(), local.getNombre(),
                local.getDireccion(), local.getContacto(), local.getTelefono(), pedido.getPorcentaje(),
                CategoriaPrecioService.letraDe(categoriasPrecio.letrasPorPorcentaje(), pedido.getPorcentaje()),
                pedido.getNota(), items, total(pedido), conFaltante, sinPrecio,
                conPrecios && conFaltante == 0,
                conPrecios && conFaltante > 0 && hayAlgoQueEntregar(pedido, asignado),
                pedido.getOrigenId(), pedido.esSaldo() ? original.getNumero() : null,
                saldos.isEmpty() ? null : grupo(original, saldos),
                pedido.getCreadoEn(), pedido.getDespachadoEn(), cobro(pedido),
                pedido.getCorrecciones().stream()
                        .map(c -> new CorreccionResponse(c.getId(), c.getFecha(), c.getMotivo(),
                                List.of(c.getDetalle().split("\n")), c.getTotalAnterior(), c.getTotalNuevo()))
                        .toList());
    }

    /** El pedido original y sus saldos, con lo entregado y lo pendiente (solo para uso interno). */
    private static Grupo grupo(Pedido original, List<Pedido> saldos) {
        List<Pedido> todos = Stream.concat(Stream.of(original), saldos.stream()).toList();
        Function<EstadoPedido, BigDecimal> suma = estado -> todos.stream().filter(p -> p.getEstado() == estado)
                .map(PedidoService::total).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal entregado = suma.apply(EstadoPedido.DESPACHADO);
        BigDecimal pendiente = suma.apply(EstadoPedido.BORRADOR);
        BigDecimal anulado = suma.apply(EstadoPedido.ANULADO);
        return new Grupo(entregado.add(pendiente).add(anulado), entregado, pendiente, anulado,
                todos.stream().map(p -> new PedidoDelGrupo(p.getId(), p.getNumero(), p.getEstado(),
                        p.getDocumento(), total(p), p.esSaldo(), p.getCreadoEn(), p.getDespachadoEn())).toList());
    }

    private static ItemResponse item(PedidoItem item, EstadoPedido estado, Map<Long, Integer> asignado,
            List<LoteNeto> entregado) {
        Disponibilidad disponibilidad = null;
        Integer disponible = null;
        Integer faltante = null;
        if (estado == EstadoPedido.DESPACHADO) {
            disponibilidad = Disponibilidad.DESPACHADO;
        } else if (estado == EstadoPedido.BORRADOR && item.esManual()) {
            disponibilidad = Disponibilidad.NO_REGISTRADO;
        } else if (estado == EstadoPedido.BORRADOR) {
            disponible = asignado.getOrDefault(item.getId(), 0);
            faltante = item.getCantidad() - disponible;
            disponibilidad = faltante == 0 ? Disponibilidad.DISPONIBLE
                    : disponible > 0 ? Disponibilidad.PARCIAL : Disponibilidad.SIN_STOCK;
        }
        Producto producto = item.getProducto();
        return new ItemResponse(item.getId(), producto == null ? null : producto.getId(),
                producto == null ? null : producto.getCodigo(), item.getDescripcion(), item.getPresentacion(),
                item.getMarca(), item.getCantidad(), item.getPrecioUnitario(), item.isPrecioManual(), item.total(),
                disponibilidad, disponible, faltante,
                entregado.stream().map(l -> new LoteEntregado(l.lote().getId(), l.lote().getNumeroLote(),
                        l.lote().getFechaVencimiento(), l.cantidad())).toList());
    }

    static Resumen resumen(Pedido pedido, Map<Long, Integer> asignado, String origenNumero) {
        int conFaltante = pedido.getEstado() != EstadoPedido.BORRADOR ? 0
                : (int) pedido.getItems().stream().filter(i -> faltante(i, asignado) > 0).count();
        return new Resumen(pedido.getId(), pedido.getNumero(), pedido.getEstado(), pedido.getDocumento(),
                pedido.getCliente().getId(), pedido.getCliente().getNombre(), pedido.getLocal().getNombre(),
                pedido.getNota(), total(pedido), (int) pedido.getItems().stream().filter(i -> i.getCantidad() > 0).count(),
                conFaltante, pedido.getOrigenId(),
                origenNumero, pedido.getCreadoEn(), pedido.getDespachadoEn());
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : TextoBusqueda.limpiar(texto);
    }
}
