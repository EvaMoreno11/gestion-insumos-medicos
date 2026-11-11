package com.evamoreno.inventario.pedido;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.HeaderFooter;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/** Dibuja la nota de pedido en PDF (A4) con OpenPDF, con el mismo diseño que la versión en Excel. */
final class NotaPedidoPdf {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color AZUL = new Color(0x1F, 0x4E, 0x79);
    private static final Color GRIS_CLARO = new Color(0xF2, 0xF2, 0xF2);
    private static final Color GRIS_BORDE = new Color(0xA6, 0xA6, 0xA6);
    private static final Color GRIS_TEXTO = new Color(0x59, 0x59, 0x59);

    // N° | Descripción | Presentación | Laboratorio | Lote | F.V. | Cant. | Precio | Total
    private static final float[] ANCHOS = { 22f, 160f, 68f, 68f, 62f, 46f, 34f, 46f, 50f };
    private static final String[] TITULOS = { "N°", "DESCRIPCIÓN", "PRESENTACIÓN", "LABORATORIO", "LOTE", "F.V.",
            "CANT.", "PRECIO\n(inc. IGV)", "TOTAL\n(inc. IGV)" };
    private static final float ALTO_FILA = 26f;

    private static final Font NEGOCIO = fuente(15, Font.BOLD, AZUL);
    private static final Font PEQUENA_GRIS = fuente(9, Font.NORMAL, GRIS_TEXTO);
    private static final Font TITULO_NOTA = fuente(12, Font.BOLD, Color.WHITE);
    private static final Font NUMERO_NOTA = fuente(12, Font.BOLD, Color.BLACK);
    private static final Font ETIQUETA = fuente(10, Font.BOLD, Color.BLACK);
    private static final Font NORMAL = fuente(10, Font.NORMAL, Color.BLACK);
    private static final Font TITULO_TABLA = fuente(7, Font.BOLD, Color.WHITE);
    private static final Font CELDA = fuente(9, Font.NORMAL, Color.BLACK);
    private static final Font TOTAL = fuente(12, Font.BOLD, Color.BLACK);

    private NotaPedidoPdf() {
    }

    static byte[] generar(NotaPedido nota) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 28, 28, 36, 42);
        PdfWriter.getInstance(documento, salida);

        HeaderFooter pie = new HeaderFooter(new Phrase("Página ", PEQUENA_GRIS), true);
        pie.setAlignment(Element.ALIGN_CENTER);
        pie.setBorder(Rectangle.NO_BORDER);
        documento.setFooter(pie);

        documento.open();
        documento.add(encabezado(nota));
        documento.add(datosDelCliente(nota));
        documento.add(tabla(nota));
        documento.close();
        return salida.toByteArray();
    }

    // ------------------------------------------------------------------ secciones

    private static PdfPTable encabezado(NotaPedido nota) {
        PdfPTable tabla = new PdfPTable(new float[] { 68f, 32f });
        tabla.setWidthPercentage(100);

        PdfPCell negocio = sinBorde();
        negocio.addElement(new Paragraph(nota.negocio(), NEGOCIO));
        negocio.addElement(new Paragraph(nota.negocioContacto(), PEQUENA_GRIS));
        tabla.addCell(negocio);

        PdfPTable numero = new PdfPTable(1);
        numero.setWidthPercentage(100);
        PdfPCell titulo = celda("NOTA DE PEDIDO", TITULO_NOTA, Element.ALIGN_CENTER);
        titulo.setBackgroundColor(AZUL);
        numero.addCell(titulo);
        numero.addCell(celda(nota.esBorrador() ? "BORRADOR" : "N° " + nota.numero(), NUMERO_NOTA,
                Element.ALIGN_CENTER));
        PdfPCell derecha = sinBorde();
        derecha.addElement(numero);
        tabla.addCell(derecha);

        tabla.setSpacingAfter(10);
        return tabla;
    }

    private static PdfPTable datosDelCliente(NotaPedido nota) {
        PdfPTable tabla = new PdfPTable(new float[] { 60f, 40f });
        tabla.setWidthPercentage(100);

        PdfPCell izquierda = sinBorde();
        renglon(izquierda, "Cliente:", nota.cliente());
        renglon(izquierda, "Local:", nota.local());
        renglon(izquierda, "Punto de llegada:", nota.puntoLlegada());
        renglon(izquierda, "Punto de partida:", nota.negocioDireccion());
        tabla.addCell(izquierda);

        PdfPCell derecha = sinBorde();
        renglon(derecha, "Fecha:", nota.fecha().format(FECHA));
        renglon(derecha, "Contacto:", nota.contacto());
        tabla.addCell(derecha);

        tabla.setSpacingAfter(12);
        return tabla;
    }

    private static PdfPTable tabla(NotaPedido nota) {
        PdfPTable tabla = new PdfPTable(ANCHOS);
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1); // los títulos se repiten en cada página

        for (String titulo : TITULOS) {
            PdfPCell celda = celda(titulo, TITULO_TABLA, Element.ALIGN_CENTER);
            celda.setBackgroundColor(AZUL);
            tabla.addCell(celda);
        }
        for (NotaPedido.Fila f : nota.filas()) {
            tabla.addCell(celda(f.numero() == null ? "" : f.numero().toString(), CELDA, Element.ALIGN_CENTER));
            tabla.addCell(celda(f.descripcion(), CELDA, Element.ALIGN_LEFT));
            tabla.addCell(celda(f.presentacion(), CELDA, Element.ALIGN_LEFT));
            tabla.addCell(celda(f.laboratorio(), CELDA, Element.ALIGN_LEFT));
            tabla.addCell(celda(f.lote(), CELDA, Element.ALIGN_CENTER));
            tabla.addCell(celda(f.vencimiento(), CELDA, Element.ALIGN_CENTER));
            tabla.addCell(celda(String.valueOf(f.cantidad()), CELDA, Element.ALIGN_CENTER));
            tabla.addCell(celda(dinero(f.precio()), CELDA, Element.ALIGN_RIGHT));
            tabla.addCell(celda(dinero(f.total()), CELDA, Element.ALIGN_RIGHT));
        }

        PdfPCell vacio = sinBorde();
        vacio.setColspan(5);
        tabla.addCell(vacio);
        PdfPCell etiqueta = celda("TOTAL S/", TOTAL, Element.ALIGN_RIGHT);
        etiqueta.setColspan(3);
        etiqueta.setBackgroundColor(GRIS_CLARO);
        tabla.addCell(etiqueta);
        PdfPCell total = celda(dinero(nota.total()), TOTAL, Element.ALIGN_RIGHT);
        total.setBackgroundColor(GRIS_CLARO);
        tabla.addCell(total);
        return tabla;
    }

    // ------------------------------------------------------------------ utilidades

    private static void renglon(PdfPCell celda, String etiqueta, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        Paragraph parrafo = new Paragraph();
        parrafo.add(new Chunk(etiqueta + "  ", ETIQUETA));
        parrafo.add(new Chunk(valor, NORMAL));
        parrafo.setSpacingAfter(3);
        celda.addElement(parrafo);
    }

    private static PdfPCell celda(String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celda.setMinimumHeight(ALTO_FILA);
        celda.setPadding(4);
        celda.setLeading(0f, 1.2f); // interlineado cuando el texto ocupa dos renglones
        celda.setBorderColor(GRIS_BORDE);
        return celda;
    }

    private static PdfPCell sinBorde() {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0);
        return celda;
    }

    private static String dinero(BigDecimal valor) {
        return valor == null ? "" : String.format(Locale.US, "%,.2f", valor);
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }
}
