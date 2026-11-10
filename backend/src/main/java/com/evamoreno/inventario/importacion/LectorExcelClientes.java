package com.evamoreno.inventario.importacion;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.evamoreno.inventario.common.SolicitudInvalidaException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.importacion.ResultadoImportacion.Observacion;

/**
 * Lee la hoja "Clientes" de la plantilla (docs/plantilla_clientes.xlsx): una fila por local.
 * Solo lee y valida el formato; no toca la base de datos.
 */
final class LectorExcelClientes {

    static final String HOJA = "Clientes";
    static final int MAX_FILAS = 5000;

    private static final Pattern RUC_DNI = Pattern.compile("^(\\d{8}|\\d{11})$");

    enum Columna {
        NOMBRE("Nombre o razón social", 200, "nombre o razon social", "nombre", "cliente", "razon social"),
        RUC_DNI("RUC o DNI", 11, "ruc o dni", "ruc", "dni", "ruc dni"),
        LOCAL("Local", 80, "local"),
        DIRECCION("Dirección de entrega", 200, "direccion de entrega", "direccion"),
        CONTACTO("Contacto", 80, "contacto"),
        TELEFONO("Teléfono", 30, "telefono"),
        PORCENTAJE("Porcentaje (%)", 10, "porcentaje"),
        OBSERVACIONES("Observaciones", 300, "observaciones");

        final String visible;
        final int max;
        final List<String> encabezados;

        Columna(String visible, int max, String... encabezados) {
            this.visible = visible;
            this.max = max;
            this.encabezados = List.of(encabezados);
        }
    }

    /** Una fila válida del Excel (un local de un cliente). */
    record Fila(int numero, String nombre, String rucDni, String local, String direccion, String contacto,
            String telefono, BigDecimal porcentaje, String observaciones) {
    }

    record Lectura(List<Fila> filas, List<Observacion> errores) {
    }

    private static final class ErrorCelda extends RuntimeException {
        ErrorCelda(Columna columna, String mensaje) {
            super(columna.visible + ": " + mensaje);
        }
    }

    private LectorExcelClientes() {
    }

    static Lectura leer(InputStream archivo) {
        try (XSSFWorkbook libro = new XSSFWorkbook(archivo)) {
            Sheet hoja = libro.getSheet(HOJA);
            if (hoja == null) {
                hoja = libro.getSheetAt(0);
            }
            return leerHoja(hoja);
        } catch (SolicitudInvalidaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new SolicitudInvalidaException(
                    "No se pudo leer el archivo. Verifica que sea un Excel .xlsx (puedes partir de la plantilla).", e);
        }
    }

    private static Lectura leerHoja(Sheet hoja) {
        Map<Columna, Integer> columnas = ubicarColumnas(hoja.getRow(0));
        if (hoja.getLastRowNum() > MAX_FILAS) {
            throw new SolicitudInvalidaException("El archivo tiene más de " + MAX_FILAS + " filas; divídelo en partes.");
        }
        List<Fila> filas = new ArrayList<>();
        List<Observacion> errores = new ArrayList<>();
        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row row = hoja.getRow(i);
            if (row == null || columnas.values().stream()
                    .allMatch(c -> LectorExcelInventario.textoDeCelda(row.getCell(c)) == null)) {
                continue;
            }
            int numero = i + 1; // número de fila tal como se ve en Excel
            try {
                filas.add(leerFila(row, numero, columnas));
            } catch (ErrorCelda e) {
                errores.add(new Observacion(numero, e.getMessage()));
            }
        }
        return new Lectura(filas, errores);
    }

    private static Map<Columna, Integer> ubicarColumnas(Row encabezado) {
        Map<Columna, Integer> columnas = new EnumMap<>(Columna.class);
        if (encabezado != null) {
            for (Cell cell : encabezado) {
                String texto = TextoBusqueda.normalizar(
                        cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : "");
                Arrays.stream(Columna.values())
                        .filter(c -> c.encabezados.contains(texto) && !columnas.containsKey(c))
                        .findFirst()
                        .ifPresent(c -> columnas.put(c, cell.getColumnIndex()));
            }
        }
        if (!columnas.containsKey(Columna.NOMBRE)) {
            throw new SolicitudInvalidaException("Falta la columna \"" + Columna.NOMBRE.visible
                    + "\" en la primera fila de la hoja. Usa la plantilla de clientes.");
        }
        return columnas;
    }

    private static Fila leerFila(Row row, int numero, Map<Columna, Integer> columnas) {
        String nombre = texto(row, columnas, Columna.NOMBRE);
        if (nombre == null) {
            throw new ErrorCelda(Columna.NOMBRE, "es obligatorio");
        }
        String rucDni = texto(row, columnas, Columna.RUC_DNI);
        if (rucDni != null) {
            rucDni = rucDni.replaceAll("[\\s.-]", "");
            if (!RUC_DNI.matcher(rucDni).matches()) {
                throw new ErrorCelda(Columna.RUC_DNI, "debe tener 8 dígitos (DNI) u 11 dígitos (RUC)");
            }
        }
        return new Fila(numero, nombre, rucDni,
                texto(row, columnas, Columna.LOCAL),
                texto(row, columnas, Columna.DIRECCION),
                texto(row, columnas, Columna.CONTACTO),
                texto(row, columnas, Columna.TELEFONO),
                porcentaje(row, columnas),
                texto(row, columnas, Columna.OBSERVACIONES));
    }

    private static String texto(Row row, Map<Columna, Integer> columnas, Columna columna) {
        Integer indice = columnas.get(columna);
        String valor = indice == null ? null
                : TextoBusqueda.limpiar(LectorExcelInventario.textoDeCelda(row.getCell(indice)));
        if (valor != null && valor.length() > columna.max && columna != Columna.RUC_DNI) {
            throw new ErrorCelda(columna, "máximo " + columna.max + " caracteres");
        }
        return valor;
    }

    /** Número entre 0 y 300 con hasta 2 decimales; acepta "15", "15%" y "12,5". */
    private static BigDecimal porcentaje(Row row, Map<Columna, Integer> columnas) {
        Integer indice = columnas.get(Columna.PORCENTAJE);
        String texto = indice == null ? null : LectorExcelInventario.textoDeCelda(row.getCell(indice));
        if (texto == null) {
            return null;
        }
        try {
            BigDecimal valor = new BigDecimal(texto.replace("%", "").replace(",", ".").trim());
            if (valor.signum() < 0 || valor.compareTo(BigDecimal.valueOf(300)) > 0
                    || valor.stripTrailingZeros().scale() > 2) {
                throw new ErrorCelda(Columna.PORCENTAJE, "debe estar entre 0 y 300, con máximo 2 decimales");
            }
            return valor;
        } catch (NumberFormatException e) {
            throw new ErrorCelda(Columna.PORCENTAJE, "\"" + texto + "\" no es un número (escribe 15 para 15 %)");
        }
    }
}
