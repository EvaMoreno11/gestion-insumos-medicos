package com.evamoreno.inventario.cliente;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.evamoreno.inventario.precio.CategoriaPrecioService;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas de clientes. */
public final class ClienteDto {

    private ClienteDto() {
    }

    /** Datos para crear o actualizar un cliente. */
    public record Request(
            @Schema(example = "Botica Demo S.A.C.")
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 200, message = "Máximo 200 caracteres")
            String nombre,

            @Schema(description = "Opcional. RUC (11 dígitos) o DNI (8 dígitos)", example = "20123456789")
            @Pattern(regexp = "^\\s*(\\d{8}|\\d{11})?\\s*$", message = "Debe tener 8 dígitos (DNI) u 11 dígitos (RUC)")
            String rucDni,

            @Schema(description = "Opcional. Incremento sobre el costo: 15 significa 15 %", example = "15")
            @DecimalMin(value = "0", message = "Debe ser 0 o más")
            @DecimalMax(value = "300", message = "Máximo 300")
            @Digits(integer = 3, fraction = 2, message = "Máximo 2 decimales")
            BigDecimal porcentaje,

            @Size(max = 300, message = "Máximo 300 caracteres")
            String observaciones,

            @Schema(description = "Locales de entrega. Si se omite al crear, se registra un único local sin nombre. "
                    + "Al actualizar: con id se modifica, sin id se agrega, y los que no se envían se eliminan.")
            List<@Valid LocalRequest> locales) {
    }

    public record LocalRequest(
            @Schema(description = "Solo al actualizar un local existente")
            Long id,

            @Schema(description = "Nombre del local; vacío si el cliente tiene uno solo", example = "Local 1")
            @Size(max = 80, message = "Máximo 80 caracteres")
            String nombre,

            @Schema(example = "Av. Los Olivos 123, Los Olivos")
            @Size(max = 200, message = "Máximo 200 caracteres")
            String direccion,

            @Schema(example = "Dra. Ana")
            @Size(max = 80, message = "Máximo 80 caracteres")
            String contacto,

            @Schema(example = "987654321")
            @Size(max = 30, message = "Máximo 30 caracteres")
            String telefono) {
    }

    public record LocalResponse(Long id, String nombre, String direccion, String contacto, String telefono) {

        static LocalResponse de(ClienteLocal l) {
            return new LocalResponse(l.getId(), l.getNombre(), l.getDireccion(), l.getContacto(), l.getTelefono());
        }
    }

    /**
     * Versión corta, para listas y el buscador.
     *
     * @param categoria          letra de la categoría de precio (A, B, C...) o null si su porcentaje no corresponde
     *                           a ninguna
     * @param localesEncontrados locales cuyo nombre o contacto coincide con la búsqueda, para mostrar por qué
     *                           apareció el cliente; vacío si no se buscó nada o solo coincidió su nombre o RUC
     */
    public record Resumen(Long id, String nombre, String rucDni, BigDecimal porcentaje, String categoria,
            boolean activo, List<LocalEncontrado> localesEncontrados) {

        static Resumen de(Cliente c, Map<BigDecimal, String> letras, List<LocalEncontrado> localesEncontrados) {
            return new Resumen(c.getId(), c.getNombre(), c.getRucDni(), c.getPorcentaje(),
                    CategoriaPrecioService.letraDe(letras, c.getPorcentaje()), c.isActivo(), localesEncontrados);
        }
    }

    /** Local que coincidió con la búsqueda (por su nombre o por su contacto). */
    public record LocalEncontrado(String nombre, String contacto) {

        static LocalEncontrado de(ClienteLocal l) {
            return new LocalEncontrado(l.getNombre(), l.getContacto());
        }
    }

    public record Detalle(Long id, String nombre, String rucDni, BigDecimal porcentaje, String categoria,
            String observaciones, boolean activo, List<LocalResponse> locales, Instant creadoEn,
            Instant actualizadoEn) {

        static Detalle de(Cliente c, Map<BigDecimal, String> letras) {
            return new Detalle(c.getId(), c.getNombre(), c.getRucDni(), c.getPorcentaje(),
                    CategoriaPrecioService.letraDe(letras, c.getPorcentaje()), c.getObservaciones(), c.isActivo(),
                    c.getLocales().stream().map(LocalResponse::de).toList(), c.getCreadoEn(), c.getActualizadoEn());
        }
    }
}
