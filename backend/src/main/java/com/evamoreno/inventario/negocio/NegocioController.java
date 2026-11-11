package com.evamoreno.inventario.negocio;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/negocio")
@Tag(name = "Negocio", description = "Datos del negocio que aparecen en el encabezado de la nota de pedido")
public class NegocioController {

    private final NegocioService service;

    public NegocioController(NegocioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Ver los datos del negocio")
    public NegocioDto obtener() {
        return service.obtener();
    }

    @PutMapping
    @Operation(summary = "Cambiar los datos del negocio")
    public NegocioDto actualizar(@Valid @RequestBody NegocioDto datos) {
        return service.actualizar(datos);
    }
}
