package com.evamoreno.inventario.catalogo;

import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Una hoja de Excel con tabla: títulos en azul, filtros, primera fila fija y lista para imprimir en A4. */
final class HojaExcel {

    private static final byte[] AZUL = { 0x1F, 0x4E, 0x79 };
    private static final byte[] GRIS_CLARO = { (byte) 0xF2, (byte) 0xF2, (byte) 0xF2 };
    private static final byte[] GRIS_BORDE = { (byte) 0xA6, (byte) 0xA6, (byte) 0xA6 };
    private static final byte[] BLANCO = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };

    final XSSFSheet hoja;
    final XSSFCellStyle texto;
    final XSSFCellStyle numero;
    final XSSFCellStyle dinero;
    final XSSFCellStyle titulo;
    final XSSFCellStyle grupo;
    final XSSFCellStyle negrita;
    private final XSSFWorkbook libro;
    private int filaActual;

    HojaExcel(XSSFWorkbook libro, String nombre) {
        this.libro = libro;
        this.hoja = libro.createSheet(nombre);
        XSSFFont normal = fuente(10, false, null);
        texto = estilo(normal, HorizontalAlignment.LEFT, null);
        texto.setWrapText(true);
        numero = estilo(normal, HorizontalAlignment.CENTER, null);
        dinero = estilo(normal, HorizontalAlignment.RIGHT, null);
        dinero.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));
        titulo = estilo(fuente(9, true, BLANCO), HorizontalAlignment.CENTER, AZUL);
        titulo.setWrapText(true);
        grupo = estilo(fuente(10.5, true, null), HorizontalAlignment.LEFT, GRIS_CLARO);
        negrita = libro.createCellStyle();
        negrita.setFont(fuente(10.5, true, null));
    }

    /** Encabezado libre (sin bordes) arriba de la tabla. */
    void lineaSuelta(String valor, double tamano, boolean enNegrita) {
        XSSFRow fila = hoja.createRow(filaActual++);
        XSSFCellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente(tamano, enNegrita, enNegrita ? AZUL : null));
        XSSFCell celda = fila.createCell(0);
        celda.setCellStyle(estilo);
        celda.setCellValue(valor == null ? "" : valor);
    }

    void saltarFila() {
        filaActual++;
    }

    /** Fila de títulos de la tabla; la deja fija al bajar y le pone filtros. */
    void titulos(String[] titulos, double[] anchos) {
        for (int i = 0; i < anchos.length; i++) {
            hoja.setColumnWidth(i, (int) Math.round(anchos[i] * 256));
        }
        int indice = filaActual++;
        XSSFRow fila = hoja.createRow(indice);
        fila.setHeightInPoints(30);
        for (int c = 0; c < titulos.length; c++) {
            XSSFCell celda = fila.createCell(c);
            celda.setCellStyle(titulo);
            celda.setCellValue(titulos[c]);
        }
        hoja.createFreezePane(0, indice + 1);
        hoja.setRepeatingRows(new CellRangeAddress(indice, indice, 0, titulos.length - 1));
    }

    XSSFRow fila() {
        XSSFRow fila = hoja.createRow(filaActual++);
        fila.setHeightInPoints(20);
        return fila;
    }

    /** Fila que agrupa (p. ej. el nombre de la categoría), unida a lo ancho de la tabla. */
    void filaDeGrupo(String valor, int columnas) {
        XSSFRow fila = hoja.createRow(filaActual);
        fila.setHeightInPoints(22);
        for (int c = 0; c < columnas; c++) {
            XSSFCell celda = fila.createCell(c);
            celda.setCellStyle(grupo);
            if (c == 0) {
                celda.setCellValue(valor);
            }
        }
        hoja.addMergedRegion(new CellRangeAddress(filaActual, filaActual, 0, columnas - 1));
        filaActual++;
    }

    static void celda(XSSFRow fila, int columna, String valor, XSSFCellStyle estilo) {
        XSSFCell celda = fila.createCell(columna);
        celda.setCellStyle(estilo);
        if (valor != null && !valor.isBlank()) {
            celda.setCellValue(valor);
        }
    }

    static void celda(XSSFRow fila, int columna, Number valor, XSSFCellStyle estilo) {
        XSSFCell celda = fila.createCell(columna);
        celda.setCellStyle(estilo);
        if (valor instanceof BigDecimal decimal) {
            celda.setCellValue(decimal.doubleValue());
        } else if (valor != null) {
            celda.setCellValue(valor.doubleValue());
        }
    }

    /** Filtros en la fila de títulos y configuración de impresión (A4, una página de ancho). */
    void terminar(int filaTitulos, int columnas, boolean horizontal) {
        if (filaActual > filaTitulos + 1) {
            hoja.setAutoFilter(new CellRangeAddress(filaTitulos, filaActual - 1, 0, columnas - 1));
        }
        PrintSetup impresion = hoja.getPrintSetup();
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setLandscape(horizontal);
        impresion.setFitWidth((short) 1);
        impresion.setFitHeight((short) 0);
        hoja.setFitToPage(true);
        hoja.setAutobreaks(true);
        hoja.getFooter().setCenter("&8Página &P de &N");
    }

    int filaActual() {
        return filaActual;
    }

    private XSSFFont fuente(double tamano, boolean enNegrita, byte[] color) {
        XSSFFont fuente = libro.createFont();
        fuente.setFontName("Arial");
        fuente.setFontHeight(tamano);
        fuente.setBold(enNegrita);
        if (color != null) {
            fuente.setColor(new XSSFColor(color, null));
        }
        return fuente;
    }

    private XSSFCellStyle estilo(XSSFFont fuente, HorizontalAlignment alineacion, byte[] fondo) {
        XSSFCellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        estilo.setAlignment(alineacion);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        if (fondo != null) {
            estilo.setFillForegroundColor(new XSSFColor(fondo, null));
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        XSSFColor gris = new XSSFColor(GRIS_BORDE, null);
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
        estilo.setTopBorderColor(gris);
        estilo.setBottomBorderColor(gris);
        estilo.setLeftBorderColor(gris);
        estilo.setRightBorderColor(gris);
        return estilo;
    }
}
