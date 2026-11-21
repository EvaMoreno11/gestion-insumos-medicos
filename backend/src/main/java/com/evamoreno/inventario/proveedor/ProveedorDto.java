package com.evamoreno.inventario.proveedor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.evamoreno.inventario.pedido.PedidoDto.PagoResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos que entran y salen de la API de proveedores. */
public final class ProveedorDto {

    private ProveedorDto() {
    }

    /**
     * Una deuda nueva o corregida.
     *
     * @param proveedor nombre del proveedor; si no existe (sin importar tildes ni mayúsculas), se crea
     */
    public record DeudaRequest(
            @Schema(example = "Distribuidora Médica del Norte")
            @NotBlank(message = "Escribe el nombre del proveedor")
            @Size(max = 120, message = "Máximo 120 caracteres")
            String proveedor,

            @Schema(description = "Día de la compra. Por defecto, hoy")
            LocalDate fecha,

            @Schema(example = "1500.00")
            @NotNull(message = "El monto es obligatorio")
            @DecimalMin(value = "0.01", message = "Debe ser mayor que 0")
            @DecimalMax(value = "100000000", message = "Monto demasiado grande")
            @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales")
            BigDecimal monto,

            @Schema(example = "Factura F001-234")
            @Size(max = 200, message = "Máximo 200 caracteres")
            String nota) {
    }

    public record ProveedorResponse(Long id, String nombre) {
    }

    /**
     * @param saldo    lo que falta pagar (0 si ya está pagada)
     * @param pagadaEl día del último pago, si ya está pagada del todo
     */
    public record DeudaResponse(Long id, Long proveedorId, String proveedor, LocalDate fecha, BigDecimal monto,
            String nota, BigDecimal pagado, BigDecimal saldo, LocalDate pagadaEl, List<PagoResponse> pagos) {
    }

    /** Para la cabecera de la pantalla y el número del botón del inicio. */
    public record Resumen(BigDecimal porPagar, int deudasPorPagar) {
    }
}
