package com.evamoreno.inventario.proveedor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.common.SolicitudInvalidaException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.pedido.PedidoDto.PagoRequest;
import com.evamoreno.inventario.pedido.PedidoDto.PagoResponse;
import com.evamoreno.inventario.proveedor.ProveedorDto.DeudaRequest;
import com.evamoreno.inventario.proveedor.ProveedorDto.DeudaResponse;
import com.evamoreno.inventario.proveedor.ProveedorDto.ProveedorResponse;
import com.evamoreno.inventario.proveedor.ProveedorDto.Resumen;

/**
 * Pagos a proveedores: lo que se les debe por las compras que no se pagan al contado y los pagos que se les van
 * haciendo. Solo es un control para no olvidar nada: no toca el inventario ni el Resumen.
 */
@Service
@Transactional
public class ProveedorService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    /** Por pagar: primero la deuda más antigua, para que ninguna se olvide. */
    private static final Comparator<DeudaProveedor> POR_PAGAR = Comparator.comparing(DeudaProveedor::getFecha)
            .thenComparing(DeudaProveedor::getId);

    /** Pagadas: primero la que se terminó de pagar más recientemente. */
    private static final Comparator<DeudaProveedor> PAGADAS = Comparator.comparing(DeudaProveedor::pagadaEl)
            .thenComparing(DeudaProveedor::getId).reversed();

    private final ProveedorRepository proveedores;
    private final DeudaProveedorRepository deudas;
    private final Clock clock;

    public ProveedorService(ProveedorRepository proveedores, DeudaProveedorRepository deudas, Clock clock) {
        this.proveedores = proveedores;
        this.deudas = deudas;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<ProveedorResponse> proveedores() {
        return proveedores.findAllByOrderByNombreAsc().stream()
                .map(p -> new ProveedorResponse(p.getId(), p.getNombre()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Resumen resumen() {
        List<DeudaProveedor> porPagar = deudas.todas().stream().filter(d -> d.saldo().signum() > 0).toList();
        BigDecimal total = porPagar.stream().map(DeudaProveedor::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Resumen(total.setScale(2, RoundingMode.HALF_UP), porPagar.size());
    }

    @Transactional(readOnly = true)
    public Pagina<DeudaResponse> deudas(boolean pagadas, int pagina, int tamano) {
        List<DeudaProveedor> lista = deudas.todas().stream()
                .filter(d -> pagadas == (d.saldo().signum() <= 0))
                .sorted(pagadas ? PAGADAS : POR_PAGAR)
                .toList();
        int tamanoPagina = Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA);
        int numero = Math.max(pagina, 0);
        int desde = Math.min(numero * tamanoPagina, lista.size());
        List<DeudaResponse> contenido = lista.subList(desde, Math.min(desde + tamanoPagina, lista.size())).stream()
                .map(ProveedorService::respuesta)
                .toList();
        return new Pagina<>(contenido, numero, tamanoPagina, lista.size(),
                (lista.size() + tamanoPagina - 1) / tamanoPagina);
    }

    // ------------------------------------------------------------------ deudas

    public DeudaResponse crear(DeudaRequest r) {
        DeudaProveedor deuda = new DeudaProveedor(proveedor(r.proveedor()), fecha(r.fecha()), monto(r.monto()),
                limpiar(r.nota()));
        return respuesta(deudas.save(deuda));
    }

    public DeudaResponse corregir(Long id, DeudaRequest r) {
        DeudaProveedor deuda = buscar(id);
        BigDecimal pagado = deuda.pagado();
        if (monto(r.monto()).compareTo(pagado) < 0) {
            throw new ConflictoException("Ya se le pagó " + soles(pagado) + ": el monto no puede ser menor");
        }
        deuda.corregir(proveedor(r.proveedor()), fecha(r.fecha()), monto(r.monto()), limpiar(r.nota()));
        return respuesta(deuda);
    }

    /** Solo una deuda sin pagos (anotada por error); si tiene pagos, primero se borran. */
    public void eliminar(Long id) {
        DeudaProveedor deuda = buscar(id);
        if (!deuda.getPagos().isEmpty()) {
            throw new ConflictoException("Esta deuda tiene pagos: si se anotó por error, borra primero sus pagos");
        }
        deudas.delete(deuda);
    }

    // ------------------------------------------------------------------ pagos

    public DeudaResponse registrarPago(Long id, PagoRequest r) {
        DeudaProveedor deuda = buscar(id);
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fecha = r.fecha() == null ? hoy : r.fecha();
        if (fecha.isAfter(hoy)) {
            throw new SolicitudInvalidaException("La fecha del pago no puede ser futura");
        }
        BigDecimal saldo = deuda.saldo();
        if (saldo.signum() <= 0) {
            throw new ConflictoException("Esta deuda ya está pagada");
        }
        BigDecimal monto = monto(r.monto());
        if (monto.compareTo(saldo) > 0) {
            throw new ConflictoException("El pago (" + soles(monto) + ") es mayor que lo que falta pagar ("
                    + soles(saldo) + ")");
        }
        deuda.registrarPago(new PagoProveedor(deuda, fecha, monto, r.medio(), limpiar(r.nota())));
        deudas.flush(); // para que el pago tenga id en la respuesta
        return respuesta(deuda);
    }

    /** Para corregir un pago mal anotado. */
    public DeudaResponse eliminarPago(Long id, Long pagoId) {
        DeudaProveedor deuda = buscar(id);
        PagoProveedor pago = deuda.getPagos().stream().filter(p -> pagoId.equals(p.getId())).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El pago " + pagoId + " no pertenece a la deuda " + id));
        deuda.getPagos().remove(pago);
        return respuesta(deuda);
    }

    // ------------------------------------------------------------------ ayudas

    private DeudaProveedor buscar(Long id) {
        return deudas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la deuda con id " + id));
    }

    /** El proveedor con ese nombre (sin importar tildes ni mayúsculas) o uno nuevo. */
    private Proveedor proveedor(String nombre) {
        String comparable = Proveedor.comparable(nombre);
        if (comparable.isEmpty()) {
            throw new SolicitudInvalidaException("Escribe el nombre del proveedor");
        }
        return proveedores.findByNombreComparable(comparable)
                .orElseGet(() -> proveedores.save(new Proveedor(nombre)));
    }

    private LocalDate fecha(LocalDate fecha) {
        return fecha == null ? LocalDate.now(clock) : fecha;
    }

    private static BigDecimal monto(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : TextoBusqueda.limpiar(texto);
    }

    private static String soles(BigDecimal valor) {
        return "S/ " + String.format(java.util.Locale.US, "%,.2f", valor);
    }

    private static DeudaResponse respuesta(DeudaProveedor d) {
        return new DeudaResponse(d.getId(), d.getProveedor().getId(), d.getProveedor().getNombre(), d.getFecha(),
                d.getMonto(), d.getNota(), d.pagado(), d.saldo().max(BigDecimal.ZERO), d.pagadaEl(),
                d.getPagos().stream()
                        .map(p -> new PagoResponse(p.getId(), p.getFecha(), p.getMonto(), p.getMedio(), p.getNota()))
                        .toList());
    }
}
