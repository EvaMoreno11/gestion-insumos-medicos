package com.evamoreno.inventario.pedido;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Dibuja la nota de pedido en Excel (.xlsx) con Apache POI, lista para imprimir en A4.
 * Los totales son fórmulas, pero se guardan ya calculados para que también se vean
 * en visores que no recalculan (por ejemplo, la vista previa del iPhone).
 */
final class NotaPedidoExcel {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final byte[] AZUL = { 0x1F, 0x4E, 0x79 };
    private static final byte[] GRIS_CLARO = { (byte) 0xF2, (byte) 0xF2, (byte) 0xF2 };
    private static final byte[] GRIS_BORDE = { (byte) 0xA6, (byte) 0xA6, (byte) 0xA6 };
    private static final byte[] GRIS_TEXTO = { 0x59, 0x59, 0x59 };
    private static final byte[] BLANCO = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };

    // N° | Descripción | Presentación | Laboratorio | Lote | F.V. | Cant. | Precio | Total
    private static final double[] ANCHOS = { 4.5, 33, 13, 13.5, 11, 8.5, 6.5, 9, 10 };
    private static final String[] TITULOS = { "N°", "DESCRIPCIÓN", "PRESENTACIÓN", "LABORATORIO", "LOTE", "F.V.",
            "CANT.", "PRECIO\n(inc. IGV)", "TOTAL\n(inc. IGV)" };
    private static final int FILA_TITULOS = 8; // fila 9 de Excel
    private static final float ALTO_FILA = 30f;

    private final XSSFWorkbook libro = new XSSFWorkbook();
    private final XSSFSheet hoja = libro.createSheet("Nota de pedido");

    private NotaPedidoExcel() {
    }

    static byte[] generar(NotaPedido nota) {
        NotaPedidoExcel excel = new NotaPedidoExcel();
        try (excel.libro) {
            excel.dibujar(nota);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            excel.libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void dibujar(NotaPedido nota) {
        hoja.setDisplayGridlines(false);
        for (int i = 0; i < ANCHOS.length; i++) {
            hoja.setColumnWidth(i, (int) Math.round(ANCHOS[i] * 256));
        }
        encabezado(nota);
        datosDelCliente(nota);
        int ultima = tabla(nota);
        configurarImpresion(ultima);
        libro.getCreationHelper().createFormulaEvaluator().evaluateAll(); // deja los totales ya calculados
    }

    // ------------------------------------------------------------------ secciones

    private void encabezado(NotaPedido nota) {
        XSSFRow fila1 = fila(0, 24);
        XSSFRow fila2 = fila(1, 22);
        hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        hoja.addMergedRegion(new CellRangeAddress(1, 1, 0, 4));
        hoja.addMergedRegion(new CellRangeAddress(0, 0, 6, 8));
        hoja.addMergedRegion(new CellRangeAddress(1, 1, 6, 8));

        celda(fila1, 0, nota.negocio(), estilo(fuente(15, true, AZUL), HorizontalAlignment.LEFT, null, false));
        celda(fila2, 0, nota.negocioContacto(), estilo(fuente(10, false, GRIS_TEXTO), HorizontalAlignment.LEFT, null, false));

        XSSFCellStyle titulo = estilo(fuente(12, true, BLANCO), HorizontalAlignment.CENTER, AZUL, true);
        XSSFCellStyle numero = estilo(fuente(12, true, null), HorizontalAlignment.CENTER, null, true);
        for (int c = 6; c <= 8; c++) {
            celda(fila1, c, c == 6 ? "NOTA DE PEDIDO" : null, titulo);
            celda(fila2, c, c == 6 ? (nota.esBorrador() ? "BORRADOR" : "N° " + nota.numero()) : null, numero);
        }
    }

    private void datosDelCliente(NotaPedido nota) {
        String[][] izquierda = {
                { "Cliente:", nota.cliente() },
                { "Local:", nota.local() },
                { "Punto de llegada:", nota.puntoLlegada() },
                { "Punto de partida:", nota.negocioDireccion() },
        };
        String[][] derecha = {
                { "Fecha:", nota.fecha().format(FECHA) },
                { "Contacto:", nota.contacto() },
        };
        XSSFCellStyle estilo = estilo(fuente(10.5, false, null), HorizontalAlignment.LEFT, null, false);
        int r = 3;
        int d = 0;
        for (String[] par : izquierda) {
            if (par[1] == null || par[1].isBlank()) {
                continue; // no se muestran renglones vacíos (p. ej. "Local" si el cliente tiene uno solo)
            }
            XSSFRow fila = fila(r, 19);
            hoja.addMergedRegion(new CellRangeAddress(r, r, 0, 3));
            etiquetaYValor(fila, 0, par[0], par[1], estilo);
            while (d < derecha.length && (derecha[d][1] == null || derecha[d][1].isBlank())) {
                d++;
            }
            if (d < derecha.length) {
                hoja.addMergedRegion(new CellRangeAddress(r, r, 4, 8));
                etiquetaYValor(fila, 4, derecha[d][0], derecha[d][1], estilo);
                d++;
            }
            r++;
        }
    }

    /** @return índice de la última fila usada */
    private int tabla(NotaPedido nota) {
        XSSFRow titulos = fila(FILA_TITULOS, 30);
        XSSFCellStyle estiloTitulo = estilo(fuente(8.5, true, BLANCO), HorizontalAlignment.CENTER, AZUL, true);
        estiloTitulo.setWrapText(true);
        for (int c = 0; c < TITULOS.length; c++) {
            celda(titulos, c, TITULOS[c], estiloTitulo);
        }

        XSSFFont normal = fuente(10, false, null);
        XSSFCellStyle centro = estilo(normal, HorizontalAlignment.CENTER, null, true);
        XSSFCellStyle texto = estilo(normal, HorizontalAlignment.LEFT, null, true);
        texto.setWrapText(true);
        XSSFCellStyle dinero = estilo(normal, HorizontalAlignment.RIGHT, null, true);
        dinero.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));

        int r = FILA_TITULOS;
        for (NotaPedido.Fila f : nota.filas()) {
            XSSFRow fila = fila(++r, ALTO_FILA);
            XSSFCell numero = fila.createCell(0);
            numero.setCellStyle(centro);
            if (f.numero() != null) {
                numero.setCellValue(f.numero());
            }
            celda(fila, 1, f.descripcion(), texto);
            celda(fila, 2, f.presentacion(), texto);
            celda(fila, 3, f.laboratorio(), texto);
            celda(fila, 4, f.lote(), centro);
            celda(fila, 5, f.vencimiento(), centro);
            XSSFCell cantidad = fila.createCell(6);
            cantidad.setCellStyle(centro);
            cantidad.setCellValue(f.cantidad());
            XSSFCell precio = fila.createCell(7);
            precio.setCellStyle(dinero);
            XSSFCell total = fila.createCell(8);
            total.setCellStyle(dinero);
            if (f.precio() != null) {
                precio.setCellValue(f.precio().doubleValue());
                total.setCellFormula("ROUND(G" + (r + 1) + "*H" + (r + 1) + ",2)");
            }
        }

        int primera = FILA_TITULOS + 2; // en numeración de Excel
        int ultimaDeDatos = r + 1;
        XSSFRow filaTotal = fila(++r, ALTO_FILA);
        hoja.addMergedRegion(new CellRangeAddress(r, r, 5, 7));
        XSSFCellStyle etiqueta = estilo(fuente(12, true, null), HorizontalAlignment.RIGHT, GRIS_CLARO, true);
        XSSFCellStyle valor = estilo(fuente(12, true, null), HorizontalAlignment.RIGHT, GRIS_CLARO, true);
        valor.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));
        for (int c = 5; c <= 7; c++) {
            celda(filaTotal, c, c == 5 ? "TOTAL S/" : null, etiqueta);
        }
        XSSFCell total = filaTotal.createCell(8);
        total.setCellStyle(valor);
        if (nota.filas().isEmpty()) {
            total.setCellValue(0);
        } else {
            total.setCellFormula("SUM(I" + primera + ":I" + ultimaDeDatos + ")");
        }
        return r;
    }

    private void configurarImpresion(int ultimaFila) {
        PrintSetup impresion = hoja.getPrintSetup();
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setLandscape(false);
        impresion.setFitWidth((short) 1);
        impresion.setFitHeight((short) 0);
        hoja.setFitToPage(true);
        hoja.setAutobreaks(true);
        hoja.setHorizontallyCenter(true);
        hoja.setRepeatingRows(new CellRangeAddress(FILA_TITULOS, FILA_TITULOS, 0, 8)); // títulos en cada hoja
        libro.setPrintArea(0, 0, 8, 0, ultimaFila);
        hoja.setMargin(PageMargin.LEFT, 0.35);
        hoja.setMargin(PageMargin.RIGHT, 0.35);
        hoja.setMargin(PageMargin.TOP, 0.5);
        hoja.setMargin(PageMargin.BOTTOM, 0.6);
        hoja.getFooter().setCenter("&8Página &P de &N");
    }

    // ------------------------------------------------------------------ utilidades

    private XSSFRow fila(int indice, float alto) {
        XSSFRow fila = hoja.createRow(indice);
        fila.setHeightInPoints(alto);
        return fila;
    }

    private static void celda(XSSFRow fila, int columna, String valor, XSSFCellStyle estilo) {
        XSSFCell celda = fila.createCell(columna);
        celda.setCellStyle(estilo);
        if (valor != null && !valor.isBlank()) {
            celda.setCellValue(valor);
        }
    }

    /** "Cliente:  Botica Demo" con la etiqueta en negrita, en una sola celda. */
    private void etiquetaYValor(XSSFRow fila, int columna, String etiqueta, String valor, XSSFCellStyle estilo) {
        XSSFRichTextString texto = new XSSFRichTextString();
        texto.append(etiqueta + "  ", fuente(10.5, true, null));
        texto.append(valor, fuente(10.5, false, null));
        XSSFCell celda = fila.createCell(columna);
        celda.setCellStyle(estilo);
        celda.setCellValue(texto);
    }

    private XSSFFont fuente(double tamano, boolean negrita, byte[] color) {
        XSSFFont fuente = libro.createFont();
        fuente.setFontName("Arial");
        fuente.setFontHeight(tamano);
        fuente.setBold(negrita);
        if (color != null) {
            fuente.setColor(new XSSFColor(color, null));
        }
        return fuente;
    }

    private XSSFCellStyle estilo(XSSFFont fuente, HorizontalAlignment alineacion, byte[] fondo, boolean bordes) {
        XSSFCellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        estilo.setAlignment(alineacion);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        if (fondo != null) {
            estilo.setFillForegroundColor(new XSSFColor(fondo, null));
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (bordes) {
            XSSFColor gris = new XSSFColor(GRIS_BORDE, null);
            estilo.setBorderTop(BorderStyle.THIN);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
            estilo.setTopBorderColor(gris);
            estilo.setBottomBorderColor(gris);
            estilo.setLeftBorderColor(gris);
            estilo.setRightBorderColor(gris);
        }
        return estilo;
    }
}
