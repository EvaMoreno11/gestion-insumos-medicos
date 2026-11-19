package com.evamoreno.inventario.catalogo;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.evamoreno.inventario.catalogo.Exportaciones.Catalogo;
import com.evamoreno.inventario.catalogo.Exportaciones.Inventario;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/exportar")
@Tag(name = "Exportar", description = "Inventario completo (uso interno) y catálogo para clientes")
public class ExportacionController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportacionService service;

    public ExportacionController(ExportacionService service) {
        this.service = service;
    }

    @GetMapping("/inventario.xlsx")
    @Operation(summary = "Inventario completo en Excel (uso interno)",
            description = "Todos los productos activos, tengan o no stock, con stock, mínimo, costo y valor; "
                    + "y una hoja con los lotes que tienen unidades.")
    public ResponseEntity<byte[]> inventario() {
        Inventario inventario = service.inventario();
        return archivo(ArchivosExportados.inventarioExcel(inventario), XLSX, "inventario-" + inventario.fecha() + ".xlsx");
    }

    @GetMapping("/catalogo.pdf")
    @Operation(summary = "Catálogo para clientes en PDF",
            description = "Productos activos con stock mínimo, por categoría. Sin costos ni cantidades. "
                    + "Con clienteId, es su lista de precios (costo + su porcentaje).")
    public ResponseEntity<byte[]> catalogoPdf(
            @Parameter(description = "Opcional: para incluir los precios de ese cliente")
            @RequestParam(required = false) Long clienteId) {
        Catalogo catalogo = service.catalogo(clienteId);
        return archivo(ArchivosExportados.catalogoPdf(catalogo), MediaType.APPLICATION_PDF,
                catalogo.nombreArchivo() + ".pdf");
    }

    @GetMapping("/catalogo.xlsx")
    @Operation(summary = "Catálogo para clientes en Excel", description = "El mismo contenido que el PDF.")
    public ResponseEntity<byte[]> catalogoExcel(@RequestParam(required = false) Long clienteId) {
        Catalogo catalogo = service.catalogo(clienteId);
        return archivo(ArchivosExportados.catalogoExcel(catalogo), XLSX, catalogo.nombreArchivo() + ".xlsx");
    }

    private static ResponseEntity<byte[]> archivo(byte[] contenido, MediaType tipo, String nombre) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(contenido);
    }
}
