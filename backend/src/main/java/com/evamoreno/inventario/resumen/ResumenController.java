package com.evamoreno.inventario.resumen;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.evamoreno.inventario.resumen.ResumenDto.Detalles;
import com.evamoreno.inventario.resumen.ResumenDto.Resumen;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/resumen")
@Tag(name = "Resumen", description = "Resumen ejecutivo: ventas, ganancia, compras, clientes e inventario")
public class ResumenController {

    private final ResumenService service;

    public ResumenController(ResumenService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Ventas, ganancia y compras del periodo, comparadas con el anterior, y ventas por mes",
            description = "Montos con IGV. Las ventas son los pedidos despachados; las compras, las entradas "
                    + "registradas en la app (sin el inventario inicial importado).")
    public Resumen resumen(
            @Parameter(description = "MES (este mes), MES_ANTERIOR, ANIO (este año) o RANGO (con desde y hasta)")
            @RequestParam(defaultValue = "MES") Periodo.Tipo periodo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.resumen(periodo, desde, hasta);
    }

    @GetMapping("/detalles")
    @Operation(summary = "Productos más vendidos, mejores clientes, clientes que dejaron de comprar, "
            + "valor del inventario, productos sin movimiento y pendiente por entregar")
    public Detalles detalles(
            @RequestParam(defaultValue = "MES") Periodo.Tipo periodo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.detalles(periodo, desde, hasta);
    }
}
