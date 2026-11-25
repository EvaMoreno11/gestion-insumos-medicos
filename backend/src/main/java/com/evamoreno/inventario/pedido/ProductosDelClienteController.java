package com.evamoreno.inventario.pedido;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.pedido.ProductosDelClienteService.ProductoComprado;
import com.evamoreno.inventario.pedido.ProductosDelClienteService.Venta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Lo que compra cada cliente, sacado de sus pedidos despachados. */
@RestController
@RequestMapping("/api/clientes/{clienteId}/productos")
@Tag(name = "Clientes", description = "Productos que compra cada cliente y a qué precio")
public class ProductosDelClienteController {

    private final ProductosDelClienteService service;

    public ProductosDelClienteController(ProductosDelClienteService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Productos que se le vendieron al cliente, primero lo comprado más recientemente",
            description = "Con el último precio, la fecha y la cantidad de la última venta. Solo pedidos despachados.")
    public Pagina<ProductoComprado> productos(@PathVariable Long clienteId,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "30") int tamano) {
        return service.productos(clienteId, buscar, pagina, tamano);
    }

    @GetMapping("/{productoId}/ventas")
    @Operation(summary = "Todas las veces que se le vendió un producto al cliente, de la más reciente a la más antigua")
    public List<Venta> ventas(@PathVariable Long clienteId, @PathVariable Long productoId) {
        return service.ventas(clienteId, productoId);
    }
}
