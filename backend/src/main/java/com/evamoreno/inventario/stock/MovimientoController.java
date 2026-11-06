package com.evamoreno.inventario.stock;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.evamoreno.inventario.common.Pagina;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/movimientos")
@Tag(name = "Movimientos", description = "Entradas, salidas (FEFO) y ajustes de stock, con su historial")
public class MovimientoController {

    private final MovimientoService service;

    public MovimientoController(MovimientoService service) {
        this.service = service;
    }

    @PostMapping("/entrada")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una entrada",
            description = "Suma unidades a un lote; si el número de lote no existe para ese producto, lo crea.")
    public ResultadoMovimiento entrada(@Valid @RequestBody EntradaRequest request) {
        return service.registrarEntrada(request);
    }

    @PostMapping("/salida")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una salida",
            description = "Saca unidades empezando por el lote que vence antes (FEFO). Los lotes vencidos no se "
                    + "despachan. Si no alcanza el stock responde 409 y no saca nada.")
    public ResultadoMovimiento salida(@Valid @RequestBody SalidaRequest request) {
        return service.registrarSalida(request);
    }

    @PostMapping("/ajuste")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajustar un lote a la cantidad real",
            description = "Para corregir diferencias del conteo físico o dar de baja un lote vencido (cantidad 0).")
    public ResultadoMovimiento ajuste(@Valid @RequestBody AjusteRequest request) {
        return service.registrarAjuste(request);
    }

    @GetMapping
    @Operation(summary = "Historial de movimientos de un producto (más recientes primero)")
    public Pagina<MovimientoResponse> historial(@RequestParam Long productoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.historial(productoId, pagina, tamano);
    }
}
