package com.evamoreno.inventario.importacion;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.evamoreno.inventario.common.SolicitudInvalidaException;
import com.evamoreno.inventario.common.TextoBusqueda;

/**
 * Lee la hoja "Inventario" de la plantilla (docs/plantilla_inventario.xlsx) y valida cada fila.
 * Solo lee y valida el formato; no toca la base de datos.
 */
final class LectorExcelInventario {

    static final String HOJA = "Inventario";
    static final int MAX_FILAS = 5000;

    private static final Pattern CODIGO = Pattern.compile("^[A-Za-z0-9-]+$");
    private static final Pattern NUMERO_LOTE = Pattern.compile("^[A-Za-z0-9./ -]+$");
    private static final Pattern SEPARADOR_CODIGOS = Pattern.compile("[,;\\s]+");

    /** Columnas de la plantilla. Se reconocen por su encabezado (sin importar tildes, mayúsculas ni "*"). */
    enum Columna {
        CODIGO(false, "codigo"),
        CATEGORIA(true, "categoria"),
        NOMBRE(true, "nombre del producto", "nombre", "producto"),
        MARCA(true, "marca"),
        PRESENTACION(true, "presentacion"),
        UNIDAD(true, "unidad de conteo", "unidad", "unidad de medida"),
        STOCK_MINIMO(false, "stock minimo"),
        CODIGO_BARRAS(false, "codigo de barras", "codigos de barras"),
        NUMERO_LOTE(false, "n de lote", "lote", "numero de lote", "nro de lote"),
        FECHA_VENCIMIENTO(false, "fecha de vencimiento", "vencimiento", "f v"),
        CANTIDAD(true, "cantidad");

        final boolean obligatoria;
        final List<String> encabezados;

        Columna(boolean obligatoria, String... encabezados) {
            this.obligatoria = obligatoria;
            this.encabezados = List.of(encabezados);
        }

        String nombreVisible() {
            return switch (this) {
                case NUMERO_LOTE -> "N° de lote";
                case CODIGO_BARRAS -> "Código de barras";
                case FECHA_VENCIMIENTO -> "Fecha de vencimiento";
                case STOCK_MINIMO -> "Stock mínimo";
                case UNIDAD -> "Unidad de conteo";
                case NOMBRE -> "Nombre del producto";
                case CODIGO -> "Código";
                case CATEGORIA -> "Categoría";
                case PRESENTACION -> "Presentación";
                default -> name().charAt(0) + name().substring(1).toLowerCase();
            };
        }
    }

    /** Una fila válida del Excel. */
    record Fila(int numero, String codigo, String categoria, String nombre, String marca, String presentacion,
            String unidad, int stockMinimo, List<String> codigosBarras, String numeroLote,
            LocalDate fechaVencimiento, int cantidad) {
    }

    record Lectura(List<Fila> filas, List<ResultadoImportacion.Observacion> errores) {
    }

    /** Error en una celda; se convierte en un error de la fila. */
    private static final class ErrorCelda extends RuntimeException {
        ErrorCelda(Columna columna, String mensaje) {
            super(columna.nombreVisible() + ": " + mensaje);
        }
    }

    private LectorExcelInventario() {
    }

    static Lectura leer(InputStream archivo) {
        try (XSSFWorkbook libro = new XSSFWorkbook(archivo)) {
            Sheet hoja = libro.getSheet(HOJA);
            if (hoja == null) {
                hoja = libro.getSheetAt(0);
            }
            return leerHoja(hoja);
        } catch (IOException | RuntimeException e) {
            if (e instanceof SolicitudInvalidaException sie) {
                throw sie;
            }
            throw new SolicitudInvalidaException(
                    "No se pudo leer el archivo. Verifica que sea un Excel .xlsx (puedes partir de la plantilla).", e);
        }
    }

    private static Lectura leerHoja(Sheet hoja) {
        Map<Columna, Integer> columnas = ubicarColumnas(hoja.getRow(0));
        List<Fila> filas = new ArrayList<>();
        List<ResultadoImportacion.Observacion> errores = new ArrayList<>();

        int ultima = hoja.getLastRowNum();
        if (ultima > MAX_FILAS) {
            throw new SolicitudInvalidaException("El archivo tiene más de " + MAX_FILAS + " filas; divídelo en partes.");
        }
        for (int i = 1; i <= ultima; i++) {
            Row row = hoja.getRow(i);
            if (row == null || filaVacia(row, columnas)) {
                continue;
            }
            int numero = i + 1; // número de fila tal como lo ve el usuario en Excel
            try {
                filas.add(leerFila(row, numero, columnas));
            } catch (ErrorCelda e) {
                errores.add(new ResultadoImportacion.Observacion(numero, e.getMessage()));
            }
        }
        return new Lectura(filas, errores);
    }

    private static Map<Columna, Integer> ubicarColumnas(Row encabezado) {
        Map<Columna, Integer> columnas = new EnumMap<>(Columna.class);
        if (encabezado != null) {
            for (Cell cell : encabezado) {
                String texto = TextoBusqueda.normalizar(cell.getCellType() == CellType.STRING
                        ? cell.getStringCellValue() : "");
                Arrays.stream(Columna.values())
                        .filter(c -> c.encabezados.contains(texto) && !columnas.containsKey(c))
                        .findFirst()
                        .ifPresent(c -> columnas.put(c, cell.getColumnIndex()));
            }
        }
        List<String> faltantes = Arrays.stream(Columna.values())
                .filter(c -> c.obligatoria && !columnas.containsKey(c))
                .map(Columna::nombreVisible)
                .toList();
        if (!faltantes.isEmpty()) {
            throw new SolicitudInvalidaException("Faltan columnas en la primera fila de la hoja: "
                    + String.join(", ", faltantes) + ". Usa la plantilla de inventario.");
        }
        return columnas;
    }

    private static boolean filaVacia(Row row, Map<Columna, Integer> columnas) {
        return columnas.values().stream().allMatch(i -> textoDeCelda(row.getCell(i)) == null);
    }

    private static Fila leerFila(Row row, int numero, Map<Columna, Integer> columnas) {
        String codigo = texto(row, columnas, Columna.CODIGO, 40);
        if (codigo != null && !CODIGO.matcher(codigo).matches()) {
            throw new ErrorCelda(Columna.CODIGO, "solo letras, números y guiones");
        }
        String numeroLote = texto(row, columnas, Columna.NUMERO_LOTE, 50);
        if (numeroLote != null && !NUMERO_LOTE.matcher(numeroLote).matches()) {
            throw new ErrorCelda(Columna.NUMERO_LOTE, "solo letras, números, puntos, barras y guiones");
        }
        Integer stockMinimo = entero(row, columnas, Columna.STOCK_MINIMO);
        return new Fila(numero,
                codigo,
                obligatorio(row, columnas, Columna.CATEGORIA, 80),
                obligatorio(row, columnas, Columna.NOMBRE, 200),
                obligatorio(row, columnas, Columna.MARCA, 80),
                obligatorio(row, columnas, Columna.PRESENTACION, 80),
                obligatorio(row, columnas, Columna.UNIDAD, 20),
                stockMinimo == null ? 0 : stockMinimo,
                codigosBarras(row, columnas),
                numeroLote,
                fecha(row, columnas, Columna.FECHA_VENCIMIENTO),
                cantidad(row, columnas));
    }

    // ------------------------------------------------------------------ celdas

    private static String obligatorio(Row row, Map<Columna, Integer> columnas, Columna columna, int max) {
        String valor = texto(row, columnas, columna, max);
        if (valor == null) {
            throw new ErrorCelda(columna, "es obligatorio");
        }
        return valor;
    }

    private static String texto(Row row, Map<Columna, Integer> columnas, Columna columna, int max) {
        Integer indice = columnas.get(columna);
        String valor = indice == null ? null : TextoBusqueda.limpiar(textoDeCelda(row.getCell(indice)));
        if (valor != null && valor.length() > max) {
            throw new ErrorCelda(columna, "máximo " + max + " caracteres");
        }
        return valor;
    }

    private static List<String> codigosBarras(Row row, Map<Columna, Integer> columnas) {
        String valor = texto(row, columnas, Columna.CODIGO_BARRAS, 500);
        if (valor == null) {
            return List.of();
        }
        List<String> codigos = Arrays.stream(SEPARADOR_CODIGOS.split(valor)).filter(s -> !s.isBlank()).toList();
        for (String codigo : codigos) {
            if (codigo.length() > 50 || !CODIGO.matcher(codigo).matches()) {
                throw new ErrorCelda(Columna.CODIGO_BARRAS, "\"" + codigo + "\" no es un código válido");
            }
        }
        return codigos;
    }

    private static int cantidad(Row row, Map<Columna, Integer> columnas) {
        Integer cantidad = entero(row, columnas, Columna.CANTIDAD);
        if (cantidad == null) {
            throw new ErrorCelda(Columna.CANTIDAD, "es obligatoria (pon 0 si no hay unidades)");
        }
        return cantidad;
    }

    /** Número entero >= 0; acepta celdas numéricas o texto. */
    private static Integer entero(Row row, Map<Columna, Integer> columnas, Columna columna) {
        Integer indice = columnas.get(columna);
        Cell cell = indice == null ? null : row.getCell(indice);
        String texto = textoDeCelda(cell);
        if (texto == null) {
            return null;
        }
        try {
            int valor = new BigDecimal(texto.replace(",", ".").trim()).intValueExact();
            if (valor < 0 || valor > 1_000_000) {
                throw new ErrorCelda(columna, "debe estar entre 0 y 1 000 000");
            }
            return valor;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ErrorCelda(columna, "\"" + texto + "\" no es un número entero");
        }
    }

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT));
    private static final List<DateTimeFormatter> FORMATOS_MES = List.of(
            DateTimeFormatter.ofPattern("M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M-uuuu").withResolverStyle(ResolverStyle.STRICT));

    /**
     * Fecha de Excel o texto dd/mm/aaaa. Si solo trae mes y año (mm/aaaa), se toma el ÚLTIMO día del mes,
     * porque así se interpreta el vencimiento de los insumos médicos.
     */
    private static LocalDate fecha(Row row, Map<Columna, Integer> columnas, Columna columna) {
        Integer indice = columnas.get(columna);
        Cell cell = indice == null ? null : row.getCell(indice);
        if (cell == null) {
            return null;
        }
        if (tipo(cell) == CellType.NUMERIC) {
            return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
        }
        String texto = textoDeCelda(cell);
        if (texto == null) {
            return null;
        }
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException ignorada) {
                // probar el siguiente formato
            }
        }
        for (DateTimeFormatter formato : FORMATOS_MES) {
            try {
                return YearMonth.parse(texto, formato).atEndOfMonth();
            } catch (DateTimeParseException ignorada) {
                // probar el siguiente formato
            }
        }
        throw new ErrorCelda(columna, "\"" + texto + "\" no es una fecha válida (usa dd/mm/aaaa)");
    }

    /** Tipo real de la celda (si es fórmula, el tipo de su resultado). */
    private static CellType tipo(Cell cell) {
        return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    }

    /**
     * Contenido de la celda como texto, o null si está vacía. Los números se escriben completos
     * (un código de barras 7751234567890 no se convierte en "7.75E+12").
     */
    private static String textoDeCelda(Cell cell) {
        if (cell == null) {
            return null;
        }
        String valor = switch (tipo(cell)) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
