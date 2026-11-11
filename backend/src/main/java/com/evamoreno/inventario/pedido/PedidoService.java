package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
import com.evamoreno.inventario.pedido.PedidoDto.CrearRequest;
import com.evamoreno.inventario.pedido.PedidoDto.Detalle;
import com.evamoreno.inventario.pedido.PedidoDto.Disponibilidad;
import com.evamoreno.inventario.pedido.PedidoDto.ItemActualizarRequest;
import com.evamoreno.inventario.pedido.PedidoDto.ItemNoRegistrado;
import com.evamoreno.inventario.pedido.PedidoDto.ItemRequest;
import com.evamoreno.inventario.pedido.PedidoDto.ItemResponse;
import com.evamoreno.inventario.pedido.PedidoDto.LoteEntregado;
import com.evamoreno.inventario.pedido.PedidoDto.Numeracion;
import com.evamoreno.inventario.pedido.PedidoDto.NumeracionRequest;
import com.evamoreno.inventario.pedido.PedidoDto.PedidoQueEspera;
import com.evamoreno.inventario.pedido.PedidoDto.PorComprar;
import com.evamoreno.inventario.pedido.PedidoDto.ProductoPorComprar;
import com.evamoreno.inventario.pedido.PedidoDto.Resumen;
import com.evamoreno.inventario.precio.CategoriaPrecioService;
import com.evamoreno.inventario.precio.Precios;
import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.producto.ProductoRepository;
import com.evamoreno.inventario.stock.Movimiento;
import com.evamoreno.inventario.stock.MovimientoRepository;
import com.evamoreno.inventario.stock.MovimientoService;
import com.evamoreno.inventario.stock.StockService;

/**
 * Pedidos: se arman como borrador (con precios por cliente y aviso de faltantes),
 * y al despachar se descuentan los lotes por FEFO y se asigna el número de nota.
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
        return Pagina.de(page, p -> resumen(p, asignado));
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
        porProducto.forEach((producto, items) -> {
            int pedido = items.stream().mapToInt(PedidoItem::getCantidad).sum();
            int faltante = items.stream().mapToInt(i -> faltante(i, asignado)).sum();
            if (faltante > 0) {
                faltantes.add(new ProductoPorComprar(producto.getId(), producto.getCodigo(), producto.getNombre(),
                        producto.getMarca(), producto.getPresentacion(),
                        disponibles.getOrDefault(producto.getId(), 0), pedido, faltante,
                        items.stream().filter(i -> faltante(i, asignado) > 0)
                                .map(i -> new PedidoQueEspera(i.getPedido().getId(),
                                        i.getPedido().getCliente().getNombre(), i.getPedido().getLocal().getNombre(),
                                        i.getCantidad(), faltante(i, asignado)))
                                .toList()));
            }
        });
        faltantes.sort((a, b) -> a.nombre().compareToIgnoreCase(b.nombre()));

        List<ItemNoRegistrado> noRegistrados = pendientes.stream().filter(PedidoItem::esManual)
                .map(i -> new ItemNoRegistrado(i.getPedido().getId(), i.getPedido().getCliente().getNombre(),
                        i.getPedido().getLocal().getNombre(), i.getDescripcion(), i.getPresentacion(), i.getMarca(),
                        i.getCantidad()))
                .toList();
        return new PorComprar(faltantes, noRegistrados);
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

        Pedido pedido = new Pedido(cliente, elegirLocal(cliente, r.localId()), porcentaje, limpiar(r.nota()));
        return detalle(pedidos.saveAndFlush(pedido));
    }

    public Detalle actualizar(Long id, ActualizarRequest r) {
        Pedido pedido = buscar(id);
        pedido.exigirBorrador();

        ClienteLocal local = r.localId() == null ? pedido.getLocal() : elegirLocal(pedido.getCliente(), r.localId());
        BigDecimal porcentaje = r.porcentaje() == null ? pedido.getPorcentaje() : r.porcentaje();
        boolean cambioPorcentaje = porcentaje.compareTo(pedido.getPorcentaje()) != 0;
        pedido.actualizar(local, porcentaje, limpiar(r.nota()));
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
            item.fijarPrecio(r.precio() != null ? r.precio() : precioAutomatico(pedido, producto), r.precio() != null);
        } else {
            if (r.descripcion() == null || r.descripcion().isBlank()) {
                throw new SolicitudInvalidaException("Indica el producto del catálogo o la descripción del ítem a mano");
            }
            item = PedidoItem.manual(pedido, TextoBusqueda.limpiar(r.descripcion()), limpiar(r.presentacion()),
                    limpiar(r.marca()), r.cantidad());
            item.fijarPrecio(r.precio(), r.precio() != null);
        }
        pedido.getItems().add(item);
        return detalle(pedido);
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

    // ================================================================== despacho

    /**
     * Entrega el pedido: exige que esté completo (todo con precio y sin faltantes), descuenta los lotes
     * por FEFO y asigna el número de nota. Todo en una transacción: o se despacha completo o no cambia nada.
     */
    public Detalle despachar(Long id) {
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
        List<String> faltantes = pedido.getItems().stream()
                .filter(i -> !i.esManual() && faltante(i, asignado) > 0)
                .map(i -> i.getDescripcion() + " (faltan " + faltante(i, asignado) + ")").toList();
        if (!faltantes.isEmpty()) {
            throw new ConflictoException("No se puede despachar: faltan unidades de " + String.join("; ", faltantes));
        }

        String numero = pedidos.numeracionBloqueada().tomarNumero();
        for (PedidoItem item : pedido.getItems()) {
            if (!item.esManual()) {
                movimientoService.registrarSalidaDePedido(item.getProducto().getId(), item.getCantidad(),
                        item.getId(), "Nota de pedido " + numero);
            }
        }
        pedido.marcarDespachado(numero, Instant.now(clock));
        return detalle(pedido);
    }

    // ================================================================== nota de pedido

    /** Contenido de la nota (para Excel o PDF). En un borrador va sin número ni lotes. */
    @Transactional(readOnly = true)
    public NotaPedido nota(Long id) {
        Pedido pedido = buscar(id);
        if (pedido.getEstado() == EstadoPedido.ANULADO) {
            throw new ConflictoException("El pedido está anulado: no tiene nota");
        }
        Map<Long, List<Movimiento>> salidas = salidasPorItem(pedido);
        List<NotaPedido.Fila> filas = new ArrayList<>();
        int numero = 0;
        for (PedidoItem item : pedido.getItems()) {
            numero++;
            List<Movimiento> lotes = salidas.getOrDefault(item.getId(), List.of());
            if (lotes.isEmpty()) {
                filas.add(fila(numero, item, null, null, item.getCantidad()));
                continue;
            }
            boolean primera = true;
            for (Movimiento salida : lotes) { // una fila por lote; solo la primera lleva número
                LocalDate vence = salida.getLote().getFechaVencimiento();
                filas.add(fila(primera ? numero : null, item, salida.getLote().getNumeroLote(),
                        vence == null ? null : vence.format(MES_ANIO), salida.getCantidad()));
                primera = false;
            }
        }

        NegocioDto datos = negocio.obtener();
        ClienteLocal local = pedido.getLocal();
        Instant momento = pedido.getDespachadoEn() != null ? pedido.getDespachadoEn() : Instant.now(clock);
        String archivo = pedido.getNumero() != null ? "nota-pedido-" + pedido.getNumero()
                : "pedido-borrador-" + pedido.getId();
        return new NotaPedido(datos.nombre(), datos.direccion(), datos.telefono(), pedido.getNumero(),
                pedido.getCliente().getNombre(), local.getNombre(), local.getDireccion(),
                NotaPedido.unir(local.getContacto(), local.getTelefono()),
                LocalDate.ofInstant(momento, clock.getZone()), filas, total(pedido), archivo);
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

    private Map<Long, List<Movimiento>> salidasPorItem(Pedido pedido) {
        if (pedido.getEstado() != EstadoPedido.DESPACHADO) {
            return Map.of();
        }
        List<Long> ids = pedido.getItems().stream().map(PedidoItem::getId).filter(Objects::nonNull).toList();
        return movimientos.findByPedidoItemIdInOrderById(ids).stream()
                .collect(Collectors.groupingBy(Movimiento::getPedidoItemId, LinkedHashMap::new, Collectors.toList()));
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
        Map<Long, List<Movimiento>> salidas = salidasPorItem(pedido);

        List<ItemResponse> items = pedido.getItems().stream()
                .map(item -> item(item, pedido.getEstado(), asignado, salidas.getOrDefault(item.getId(), List.of())))
                .toList();
        int conFaltante = (int) items.stream().filter(i -> i.faltante() != null && i.faltante() > 0).count();
        int sinPrecio = (int) items.stream().filter(i -> i.precioUnitario() == null).count();
        ClienteLocal local = pedido.getLocal();
        return new Detalle(pedido.getId(), pedido.getNumero(), pedido.getEstado(), pedido.getCliente().getId(),
                pedido.getCliente().getNombre(), local.getId(), local.getNombre(), local.getDireccion(),
                local.getContacto(), local.getTelefono(), pedido.getPorcentaje(),
                CategoriaPrecioService.letraDe(categoriasPrecio.letrasPorPorcentaje(), pedido.getPorcentaje()),
                pedido.getNota(), items, total(pedido), conFaltante, sinPrecio,
                borrador && !items.isEmpty() && conFaltante == 0 && sinPrecio == 0,
                pedido.getCreadoEn(), pedido.getDespachadoEn());
    }

    private static ItemResponse item(PedidoItem item, EstadoPedido estado, Map<Long, Integer> asignado,
            List<Movimiento> salidas) {
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
                salidas.stream().map(s -> new LoteEntregado(s.getLote().getNumeroLote(),
                        s.getLote().getFechaVencimiento(), s.getCantidad())).toList());
    }

    private static Resumen resumen(Pedido pedido, Map<Long, Integer> asignado) {
        int conFaltante = pedido.getEstado() != EstadoPedido.BORRADOR ? 0
                : (int) pedido.getItems().stream().filter(i -> faltante(i, asignado) > 0).count();
        return new Resumen(pedido.getId(), pedido.getNumero(), pedido.getEstado(), pedido.getCliente().getId(),
                pedido.getCliente().getNombre(), pedido.getLocal().getNombre(), total(pedido),
                pedido.getItems().size(), conFaltante, pedido.getCreadoEn(), pedido.getDespachadoEn());
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : TextoBusqueda.limpiar(texto);
    }
}
