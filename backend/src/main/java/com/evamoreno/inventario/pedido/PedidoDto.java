package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
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

            @Size(max = 300, message = "Máximo 300 caracteres")
            String nota) {
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
            String nota) {
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
    public record LoteEntregado(String numeroLote, LocalDate fechaVencimiento, int cantidad) {
    }

    /**
     * @param disponible unidades que este pedido tiene aseguradas (null si no aplica)
     * @param faltante   unidades que faltan comprar para completar este ítem (null si no aplica)
     * @param lotes      lotes entregados; solo en pedidos despachados
     */
    public record ItemResponse(Long id, Long productoId, String codigo, String descripcion, String presentacion,
            String marca, int cantidad, BigDecimal precioUnitario, boolean precioManual, BigDecimal total,
            Disponibilidad disponibilidad, Integer disponible, Integer faltante, List<LoteEntregado> lotes) {
    }

    /**
     * @param categoria          letra interna del porcentaje (A, B, C...) o null; no se muestra al cliente
     * @param total              suma de los ítems con precio (incluye lo que aún falta comprar)
     * @param listoParaDespachar true si es un borrador con ítems, todos con precio y sin faltantes
     */
    public record Detalle(Long id, String numero, EstadoPedido estado, Long clienteId, String cliente, Long localId,
            String local, String direccion, String contacto, String telefono, BigDecimal porcentaje, String categoria,
            String nota, List<ItemResponse> items, BigDecimal total, int itemsConFaltante, int itemsSinPrecio,
            boolean listoParaDespachar, Instant creadoEn, Instant despachadoEn) {
    }

    public record Resumen(Long id, String numero, EstadoPedido estado, Long clienteId, String cliente, String local,
            BigDecimal total, int cantidadItems, int itemsConFaltante, Instant creadoEn, Instant despachadoEn) {
    }

    /** Pedido pendiente que espera unidades de un producto. */
    public record PedidoQueEspera(Long pedidoId, String cliente, String local, int cantidad, int faltante) {
    }

    /**
     * @param pedido   total pedido entre todos los pedidos pendientes
     * @param faltante lo que hay que comprar: pedido − stock disponible
     */
    public record ProductoPorComprar(Long productoId, String codigo, String nombre, String marca,
            String presentacion, int stockDisponible, int pedido, int faltante, List<PedidoQueEspera> pedidos) {
    }

    /** Ítem a mano de un pedido pendiente: hay que conseguirlo y, después, registrarlo en el catálogo. */
    public record ItemNoRegistrado(Long pedidoId, String cliente, String local, String descripcion,
            String presentacion, String marca, int cantidad) {
    }

    public record PorComprar(List<ProductoPorComprar> productos, List<ItemNoRegistrado> noRegistrados) {
    }

    public record Numeracion(String serie, long siguiente, String proximoNumero) {
    }
}
