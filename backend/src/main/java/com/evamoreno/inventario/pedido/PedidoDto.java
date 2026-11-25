package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.evamoreno.inventario.common.Pagina;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas de pedidos. */
public final class PedidoDto {

    private PedidoDto() {
    }

    // ------------------------------------------------------------------ peticiones

    public record CrearRequest(
            @NotNull(message = "El cliente es obligatorio")
            Long clienteId,

            @Schema(description = "Obligatorio solo si el cliente tiene varios locales")
            Long localId,

            @Schema(description = "Opcional: por defecto, el del cliente. Obligatorio si el cliente aún no tiene uno "
                    + "(en ese caso se le guarda).", example = "15")
            @DecimalMin(value = "0", message = "Debe ser 0 o más")
            @DecimalMax(value = "300", message = "Máximo 300")
            @Digits(integer = 3, fraction = 2, message = "Máximo 2 decimales")
            BigDecimal porcentaje,

            @Schema(description = "true: el porcentaje enviado pasa a ser el del cliente para sus próximos pedidos")
            Boolean guardarPorcentajeEnCliente,

            @Schema(description = "Observaciones internas: no salen en la nota", example = "(No se factura)")
            @Size(max = 300, message = "Máximo 300 caracteres")
            String nota,

            @Schema(description = "NOTA (por defecto) o SOLO_TABLA: sin nombres, sin fecha y sin número")
            DocumentoPedido documento) {
    }

    /** Cambios en la cabecera de un borrador. Lo que no se envía (null) se conserva, salvo la nota. */
    public record ActualizarRequest(
            Long localId,

            @Schema(description = "Al cambiarlo se recalculan los precios que no se escribieron a mano", example = "20")
            @DecimalMin(value = "0", message = "Debe ser 0 o más")
            @DecimalMax(value = "300", message = "Máximo 300")
            @Digits(integer = 3, fraction = 2, message = "Máximo 2 decimales")
            BigDecimal porcentaje,

            Boolean guardarPorcentajeEnCliente,

            @Size(max = 300, message = "Máximo 300 caracteres")
            String nota,

            @Schema(description = "NOTA o SOLO_TABLA. Si se omite, se conserva")
            DocumentoPedido documento) {
    }

    /**
     * @param entregarLoDisponible true: si faltan unidades, se entrega lo que hay y lo que falta pasa a un
     *                             pedido nuevo (saldo). Sin esto, un pedido con faltantes no se despacha.
     */
    public record DespacharRequest(Boolean entregarLoDisponible) {
    }

    /** Cómo paga: vacío = sin anotar; 0 = contado; N = crédito a N días desde el despacho. */
    public record PlazoRequest(
            @Min(value = 0, message = "Debe ser 0 o más")
            @Max(value = 365, message = "Máximo 365 días")
            Integer plazoDias) {
    }

    public record PagoRequest(
            @Schema(example = "425.00")
            @NotNull(message = "El monto es obligatorio")
            @DecimalMin(value = "0.01", message = "Debe ser mayor que 0")
            @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales")
            BigDecimal monto,

            @Schema(description = "Día en que pagó. Por defecto, hoy")
            LocalDate fecha,

            @NotNull(message = "Indica cómo pagó")
            Pago.Medio medio,

            @Size(max = 200, message = "Máximo 200 caracteres")
            String nota) {
    }

    /**
     * Corrección de un pedido despachado. Todo se aplica junto o nada.
     *
     * @param motivo  opcional; si no viene, se anota "Cambio de lote" (si solo se cambiaron lotes) o "Corrección"
     * @param cambios ítems existentes con su nueva cantidad (0 = quitarlo) y, si cambia, su nuevo precio
     * @param nuevos  ítems que se agregan (salen del inventario como en un despacho)
     */
    public record CorreccionRequest(
            @Schema(example = "Devolvieron 2 cajas: se entregó de más")
            @Size(max = 300, message = "Máximo 300 caracteres")
            String motivo,

            List<@Valid CambioItem> cambios,

            List<@Valid ItemRequest> nuevos) {
    }

    /**
     * @param lotes   a qué lotes vuelven las unidades devueltas; obligatorio solo si el ítem salió de varios lotes
     * @param reparto de qué lotes salen las unidades entregadas (deben sumar la cantidad). Si viene, reemplaza a
     *                {@code lotes} y a la elección automática: lo que un lote ya no da vuelve a él y lo que da de
     *                más sale de él
     */
    public record CambioItem(
            @NotNull(message = "Falta el ítem")
            Long itemId,

            @NotNull(message = "La cantidad es obligatoria")
            @Min(value = 0, message = "Debe ser 0 o más")
            @Max(value = 1_000_000, message = "Cantidad demasiado grande")
            Integer cantidad,

            @DecimalMin(value = "0", message = "Debe ser 0 o más")
            @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales")
            BigDecimal precio,

            List<@Valid LoteDevuelto> lotes,

            List<@Valid LoteAsignado> reparto) {
    }

    /** Unidades de un ítem que salen de un lote. */
    public record LoteAsignado(
            @NotNull(message = "Falta el lote")
            Long loteId,

            @NotNull(message = "Falta la cantidad")
            @Min(value = 0, message = "Debe ser 0 o más")
            @Max(value = 1_000_000, message = "Cantidad demasiado grande")
            Integer cantidad) {
    }

    public record LoteDevuelto(
            @NotNull(message = "Falta el lote")
            Long loteId,

            @NotNull(message = "Falta la cantidad")
            @Positive(message = "Debe ser mayor que 0")
            Integer cantidad) {
    }

    /** @param detalle una línea por cambio */
    public record CorreccionResponse(Long id, Instant fecha, String motivo, List<String> detalle,
            BigDecimal totalAnterior, BigDecimal totalNuevo) {
    }

    /** Convierte un ítem a mano en un producto del catálogo. */
    public record VincularProductoRequest(
            @NotNull(message = "El producto es obligatorio")
            Long productoId) {
    }

    /** Nuevo ítem: con productoId es del catálogo; sin él, es un ítem a mano y la descripción es obligatoria. */
    public record ItemRequest(
            Long productoId,

            @Schema(description = "Solo para ítems a mano", example = "Tensiómetro digital de brazo")
            @Size(max = 200, message = "Máximo 200 caracteres")
            String descripcion,

            @Schema(description = "Solo para ítems a mano", example = "Unidad")
            @Size(max = 80, message = "Máximo 80 caracteres")
            String presentacion,

            @Schema(description = "Solo para ítems a mano")
            @Size(max = 80, message = "Máximo 80 caracteres")
            String marca,

            @Schema(description = "Se puede pedir más de lo que hay: el pedido queda con faltantes", example = "5")
            @NotNull(message = "La cantidad es obligatoria")
            @Positive(message = "Debe ser mayor que 0")
            @Max(value = 1_000_000, message = "Cantidad demasiado grande")
            Integer cantidad,

            @Schema(description = "Opcional. Si se omite, se calcula: costo + porcentaje del pedido", example = "9.80")
            @DecimalMin(value = "0", message = "Debe ser 0 o más")
            @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales")
            BigDecimal precio) {
    }

    public record ItemActualizarRequest(
            @NotNull(message = "La cantidad es obligatoria")
            @Positive(message = "Debe ser mayor que 0")
            @Max(value = 1_000_000, message = "Cantidad demasiado grande")
            Integer cantidad,

            @Schema(description = "Precio escrito a mano. Si se omite, se conserva el actual")
            @DecimalMin(value = "0", message = "Debe ser 0 o más")
            @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales")
            BigDecimal precio,

            @Schema(description = "true: descarta el precio escrito a mano y vuelve al calculado (costo + porcentaje)")
            Boolean precioAutomatico,

            @Schema(description = "Solo para ítems a mano")
            @Size(max = 200, message = "Máximo 200 caracteres")
            String descripcion,

            @Size(max = 80, message = "Máximo 80 caracteres")
            String presentacion,

            @Size(max = 80, message = "Máximo 80 caracteres")
            String marca) {
    }

    public record NumeracionRequest(
            @Schema(description = "Opcional: 4 dígitos", example = "0001")
            @Pattern(regexp = "^\\d{4}$", message = "Deben ser 4 dígitos")
            String serie,

            @Schema(description = "Número que tendrá la próxima nota de pedido", example = "7323")
            @NotNull(message = "El siguiente número es obligatorio")
            @Positive(message = "Debe ser mayor que 0")
            @Max(value = 999_999, message = "Máximo 999999")
            Long siguiente) {
    }

    // ------------------------------------------------------------------ respuestas

    /** Situación de un ítem frente al stock. */
    public enum Disponibilidad {
        /** Hay todo lo pedido (ya descontando lo separado para pedidos más antiguos). */
        DISPONIBLE,
        /** Hay una parte: ver {@code disponible} y {@code faltante}. */
        PARCIAL,
        /** No hay nada para este pedido. */
        SIN_STOCK,
        /** Ítem a mano: no está en el catálogo, no se controla su stock. */
        NO_REGISTRADO,
        /** El pedido ya se despachó: ver {@code lotes}. */
        DESPACHADO
    }

    /** Lote del que salió (parte de) un ítem al despachar. */
    public record LoteEntregado(Long loteId, String numeroLote, LocalDate fechaVencimiento, int cantidad) {
    }

    /**
     * @param disponible unidades que este pedido tiene aseguradas (null si no aplica)
     * @param faltante   unidades que faltan comprar para completar este ítem (null si no aplica)
     * @param lotes      lotes entregados; solo en pedidos despachados
     */
    /**
     * @param costoUnitario uso interno (nunca en la nota): en un borrador, el costo actual del producto; en un
     *                      despachado, el costo con el que salió de sus lotes (promedio si salió de varios); null si
     *                      se escribió a mano o no se sabe
     */
    public record ItemResponse(Long id, Long productoId, String codigo, String descripcion, String presentacion,
            String marca, int cantidad, BigDecimal precioUnitario, boolean precioManual, BigDecimal total,
            Disponibilidad disponibilidad, Integer disponible, Integer faltante, List<LoteEntregado> lotes,
            BigDecimal costoUnitario) {
    }

    /**
     * @param categoria            letra interna del porcentaje (A, B, C...) o null; no se muestra al cliente
     * @param nota                 observaciones internas (no salen en la nota)
     * @param total                suma de los ítems con precio (incluye lo que aún falta comprar)
     * @param listoParaDespachar   true si es un borrador con ítems, todos con precio y sin faltantes
     * @param puedeEntregarParcial true si tiene faltantes pero hay algo que entregar ya: se puede despachar
     *                             lo disponible y dejar el resto en un saldo
     * @param origenId             pedido original, si este es un saldo
     * @param origenNumero         número de la nota del pedido original (null si aún no tiene o es "Solo tabla")
     * @param grupo                el pedido original y sus saldos; null si el pedido no se dividió
     * @param cobro                pagos y plazo; null si el pedido no está despachado
     */
    public record Detalle(Long id, String numero, EstadoPedido estado, DocumentoPedido documento, Long clienteId,
            String cliente, Long localId, String local, String direccion, String contacto, String telefono,
            BigDecimal porcentaje, String categoria, String nota, List<ItemResponse> items, BigDecimal total,
            int itemsConFaltante, int itemsSinPrecio, boolean listoParaDespachar, boolean puedeEntregarParcial,
            Long origenId, String origenNumero, Grupo grupo, Instant creadoEn, Instant despachadoEn,
            CobroResponse cobro, List<CorreccionResponse> correcciones, CostoPedido costo) {
    }

    public record PagoResponse(Long id, LocalDate fecha, BigDecimal monto, Pago.Medio medio, String nota) {
    }

    /**
     * Cobro de un pedido despachado. Solo para uso interno: no sale en la nota.
     *
     * @param vence null si aún no se anotó el plazo
     * @param dias  días que faltan para que venza (negativo: días de atraso); null si no aplica
     */
    public record CobroResponse(BigDecimal total, BigDecimal pagado, BigDecimal saldo, Integer plazoDias,
            LocalDate vence, Cobro.Estado estado, Long dias, List<PagoResponse> pagos) {
    }

    /**
     * Un pedido entregado por partes: el original y sus saldos. Es solo para uso interno (no sale en la nota).
     *
     * @param pedido    lo que pidió el cliente: entregado + pendiente + no entregado
     * @param entregado suma de los pedidos del grupo ya despachados
     * @param pendiente suma de los saldos que siguen en borrador
     * @param anulado   suma de los saldos anulados (lo que finalmente no se entregó)
     */
    public record Grupo(BigDecimal pedido, BigDecimal entregado, BigDecimal pendiente, BigDecimal anulado,
            List<PedidoDelGrupo> pedidos) {
    }

    public record PedidoDelGrupo(Long id, String numero, EstadoPedido estado, DocumentoPedido documento,
            BigDecimal total, boolean esSaldo, Instant creadoEn, Instant despachadoEn) {
    }

    /** @param costo costo y ganancia (uso interno); null en un pedido anulado */
    public record Resumen(Long id, String numero, EstadoPedido estado, DocumentoPedido documento, Long clienteId,
            String cliente, String local, String nota, BigDecimal total, int cantidadItems, int itemsConFaltante,
            Long origenId, String origenNumero, Instant creadoEn, Instant despachadoEn, CostoPedido costo) {
    }

    /**
     * Cuánto costó un pedido y cuánto se gana. Solo para uso interno: nunca va en la nota.
     *
     * @param margen        % de ganancia sobre lo vendido con costo conocido, con 1 decimal (como en el Resumen);
     *                      null si ningún ítem tiene costo
     * @param itemsSinCosto ítems que no entran en la cuenta porque no se sabe su costo (o no tienen precio)
     */
    public record CostoPedido(BigDecimal costo, BigDecimal ganancia, BigDecimal margen, int itemsSinCosto) {
    }

    /** Precio que se propone para un producto en un pedido: el especial del cliente o costo + porcentaje. */
    public record PrecioSugerido(BigDecimal precio, boolean especial) {
    }

    /**
     * Pedidos despachados que cumplen la búsqueda, del más reciente al más antiguo.
     *
     * @param pedidos cuántos hay en total (no solo en esta página)
     * @param total   cuánto suman todos
     * @param dias    cuántos pedidos y cuánto suma cada día, del más reciente al más antiguo
     */
    public record Despachados(int pedidos, BigDecimal total, List<DiaDespacho> dias, Pagina<Resumen> lista) {
    }

    public record DiaDespacho(LocalDate fecha, int pedidos, BigDecimal total) {
    }

    /** Pedido pendiente que espera unidades de un producto. */
    public record PedidoQueEspera(Long pedidoId, String cliente, String local, int cantidad, int faltante) {
    }

    /**
     * Un producto de "Por comprar": lo que falta para los pedidos pendientes, lo agregado a mano, o las dos cosas
     * (en una sola fila).
     *
     * @param stockMinimo 0 si no tiene mínimo
     * @param pedido      total pedido entre todos los pedidos pendientes (0 si solo se agregó a mano)
     * @param faltante    lo que falta para los pedidos: pedido − stock disponible
     * @param comprar     lo que hay que comprar: el faltante más la cantidad agregada a mano (si se escribió)
     * @param aMano       si se agregó a mano, con su cantidad y nota; null si solo lo esperan pedidos
     */
    public record ProductoPorComprar(Long productoId, String codigo, String nombre, String marca,
            String presentacion, int stockDisponible, int stockMinimo, int pedido, int faltante, int comprar,
            List<PedidoQueEspera> pedidos, AgregadoAMano aMano) {
    }

    /** Lo agregado a mano a "Por comprar": un recordatorio, con la cantidad (opcional) y una nota. */
    public record AgregadoAMano(Integer cantidad, String nota, Instant creadoEn) {
    }

    /** Ítem a mano de un pedido pendiente: hay que conseguirlo y, después, registrarlo en el catálogo. */
    public record ItemNoRegistrado(Long pedidoId, Long itemId, String cliente, String local, String descripcion,
            String presentacion, String marca, int cantidad) {
    }

    /**
     * @param costoTotal        cuánto costaría comprar todo: lo que hay que comprar × último costo de cada producto
     * @param productosSinCosto productos por comprar que no tienen costo, así que no están en {@code costoTotal}
     *                          (los ítems a mano de los pedidos tampoco están: no tienen costo)
     */
    public record PorComprar(List<ProductoPorComprar> productos, List<ItemNoRegistrado> noRegistrados,
            BigDecimal costoTotal, int productosSinCosto) {
    }

    public record Numeracion(String serie, long siguiente, String proximoNumero) {
    }
}
