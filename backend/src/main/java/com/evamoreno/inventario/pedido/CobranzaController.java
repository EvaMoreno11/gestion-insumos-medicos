package com.evamoreno.inventario.pedido;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.evamoreno.inventario.pedido.CobranzaService.Cobranzas;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/cobranzas")
@Tag(name = "Cobranzas", description = "Lo que se debe de los pedidos despachados")
public class CobranzaController {

    private final CobranzaService service;

    public CobranzaController(CobranzaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Notas con saldo pendiente: vencidas, por cobrar y sin plazo anotado",
            description = "El plazo y los pagos se registran en /api/pedidos/{id}/plazo y /api/pedidos/{id}/pagos.")
    public Cobranzas cobranzas() {
        return service.cobranzas();
    }
}
