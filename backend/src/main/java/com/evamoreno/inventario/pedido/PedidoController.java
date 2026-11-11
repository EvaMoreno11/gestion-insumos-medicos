package com.evamoreno.inventario.pedido;

import java.net.URI;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.pedido.PedidoDto.ActualizarRequest;
import com.evamoreno.inventario.pedido.PedidoDto.CrearRequest;
import com.evamoreno.inventario.pedido.PedidoDto.Detalle;
import com.evamoreno.inventario.pedido.PedidoDto.ItemActualizarRequest;
import com.evamoreno.inventario.pedido.PedidoDto.ItemRequest;
import com.evamoreno.inventario.pedido.PedidoDto.Numeracion;
import com.evamoreno.inventario.pedido.PedidoDto.NumeracionRequest;
import com.evamoreno.inventario.pedido.PedidoDto.PorComprar;
import com.evamoreno.inventario.pedido.PedidoDto.Resumen;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pedidos")
@Tag(name = "Pedidos", description = "Pedidos por cliente: borrador con faltantes, despacho por lotes y nota de pedido")
public class PedidoController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ consultas

    @GetMapping
    @Operation(summary = "Listar pedidos (del más reciente al más antiguo)")
    public Pagina<Resumen> listar(
            @Parameter(description = "BORRADOR, DESPACHADO o ANULADO") @RequestParam(required = false) EstadoPedido estado,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(estado, clienteId, pagina, tamano);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un pedido con sus ítems, su total y lo que falta")
    public Detalle obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping("/por-comprar")
    @Operation(summary = "Lista \"Por comprar\"",
            description = "Lo que falta de cada producto sumando todos los pedidos pendientes, con los pedidos que lo "
                    + "esperan, y los ítems a mano que aún no están en el catálogo.")
    public PorComprar porComprar() {
        return service.porComprar();
    }

    // ------------------------------------------------------------------ cabecera

    @PostMapping
    @Operation(summary = "Crear un pedido (borrador)",
            description = "Usa el porcentaje del cliente. Si el cliente aún no tiene uno, hay que enviarlo y se le "
                    + "guarda. Si el cliente tiene varios locales, hay que indicar el local.")
    public ResponseEntity<Detalle> crear(@Valid @RequestBody CrearRequest request) {
        Detalle creado = service.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cambiar el local, el porcentaje o la nota de un borrador",
            description = "Al cambiar el porcentaje se recalculan los precios que no se escribieron a mano.")
    public Detalle actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarRequest request) {
        return service.actualizar(id, request);
    }

    @PostMapping("/{id}/despachar")
    @Operation(summary = "Despachar el pedido",
            description = "Exige que todo tenga precio y que no falten unidades. Descuenta los lotes (primero el que "
                    + "vence antes) y asigna el número de nota. Si algo falla, no cambia nada.")
    public Detalle despachar(@PathVariable Long id) {
        return service.despachar(id);
    }

    @PostMapping("/{id}/anular")
    @Operation(summary = "Anular un borrador (libera el stock que tenía separado)")
    public Detalle anular(@PathVariable Long id) {
        return service.anular(id);
    }

    // ------------------------------------------------------------------ ítems

    @PostMapping("/{id}/items")
    @Operation(summary = "Agregar un ítem",
            description = "Con productoId, del catálogo (precio = costo + porcentaje, salvo que se envíe uno). "
                    + "Sin productoId, ítem a mano con su descripción. Devuelve el pedido con el total actualizado.")
    public Detalle agregarItem(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return service.agregarItem(id, request);
    }

    @PutMapping("/{id}/items/{itemId}")
    @Operation(summary = "Cambiar la cantidad o el precio de un ítem")
    public Detalle actualizarItem(@PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody ItemActualizarRequest request) {
        return service.actualizarItem(id, itemId, request);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Quitar un ítem")
    public Detalle eliminarItem(@PathVariable Long id, @PathVariable Long itemId) {
        return service.eliminarItem(id, itemId);
    }

    // ------------------------------------------------------------------ nota de pedido

    @GetMapping("/{id}/nota.pdf")
    @Operation(summary = "Descargar la nota de pedido en PDF",
            description = "Para imprimir o enviar al cliente. En un borrador sale sin número ni lotes.")
    public ResponseEntity<byte[]> notaPdf(@PathVariable Long id) {
        NotaPedido nota = service.nota(id);
        return archivo(NotaPedidoPdf.generar(nota), MediaType.APPLICATION_PDF, nota.nombreArchivo() + ".pdf");
    }

    @GetMapping("/{id}/nota.xlsx")
    @Operation(summary = "Descargar la nota de pedido en Excel",
            description = "El mismo contenido que el PDF, para tenerlo a la mano al facturar.")
    public ResponseEntity<byte[]> notaExcel(@PathVariable Long id) {
        NotaPedido nota = service.nota(id);
        return archivo(NotaPedidoExcel.generar(nota), XLSX, nota.nombreArchivo() + ".xlsx");
    }

    private static ResponseEntity<byte[]> archivo(byte[] contenido, MediaType tipo, String nombre) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre).build().toString())
                .body(contenido);
    }

    // ------------------------------------------------------------------ numeración

    @GetMapping("/numeracion")
    @Operation(summary = "Ver qué número tendrá la próxima nota de pedido")
    public Numeracion numeracion() {
        return service.numeracion();
    }

    @PutMapping("/numeracion")
    @Operation(summary = "Fijar el número de la próxima nota de pedido",
            description = "A partir de ahí, cada despacho suma 1.")
    public Numeracion configurarNumeracion(@Valid @RequestBody NumeracionRequest request) {
        return service.configurarNumeracion(request);
    }
}
