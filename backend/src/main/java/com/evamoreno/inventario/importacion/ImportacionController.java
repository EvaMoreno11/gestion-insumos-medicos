package com.evamoreno.inventario.importacion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.evamoreno.inventario.common.SolicitudInvalidaException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/importacion")
@Tag(name = "Importación", description = "Carga del inventario desde la plantilla de Excel")
public class ImportacionController {

    private final ImportacionService service;

    public ImportacionController(ImportacionService service) {
        this.service = service;
    }

    @PostMapping(value = "/inventario", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar inventario desde Excel",
            description = "Lee la hoja \"Inventario\" de la plantilla (docs/plantilla_inventario.xlsx). "
                    + "Por defecto SOLO VALIDA y no guarda nada: revisa el reporte y, si no hay errores, "
                    + "vuelve a enviarlo con soloValidar=false. Si alguna fila tiene errores, no se guarda nada.")
    public ResultadoImportacion importar(
            @Parameter(description = "Archivo .xlsx") @RequestPart("archivo") MultipartFile archivo,
            @Parameter(description = "true: solo revisar (no guarda). false: importar de verdad.")
            @RequestParam(defaultValue = "true") boolean soloValidar) throws IOException {
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename();
        if (archivo.isEmpty() || !nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new SolicitudInvalidaException("Sube un archivo Excel .xlsx (puedes partir de la plantilla)");
        }
        try (InputStream contenido = archivo.getInputStream()) {
            return service.importar(contenido, soloValidar);
        }
    }
}
