package com.evamoreno.inventario.catalogo;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import com.evamoreno.inventario.catalogo.Exportaciones.Catalogo;
import com.evamoreno.inventario.catalogo.Exportaciones.Grupo;
import com.evamoreno.inventario.catalogo.Exportaciones.Inventario;
import com.evamoreno.inventario.catalogo.Exportaciones.Item;

/** Dibuja el inventario completo (Excel) y el catálogo para clientes (Excel y PDF). */
final class ArchivosExportados {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SIN_PRECIO = "Consultar";

    private ArchivosExportados() {
    }

    // ================================================================== inventario (Excel)

    static byte[] inventarioExcel(Inventario inventario) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaExcel productos = new HojaExcel(libro, "Inventario");
            productos.lineaSuelta("Inventario al " + inventario.fecha().format(FECHA), 13, true);
            productos.lineaSuelta("Uso interno: incluye costos. Stock disponible = unidades en lotes no vencidos.", 9,
                    false);
            productos.saltarFila();
            int titulos = productos.filaActual();
            productos.titulos(new String[] { "CÓDIGO", "CATEGORÍA", "NOMBRE DEL PRODUCTO", "MARCA", "PRESENTACIÓN",
                    "UNIDAD", "STOCK DISPONIBLE", "STOCK MÍNIMO", "COSTO", "VALOR" },
                    new double[] { 11, 16, 38, 16, 16, 9, 11, 10, 10, 12 });
            for (Exportaciones.Producto p : inventario.productos()) {
                XSSFRow fila = productos.fila();
                HojaExcel.celda(fila, 0, p.codigo(), productos.texto);
                HojaExcel.celda(fila, 1, p.categoria(), productos.texto);
                HojaExcel.celda(fila, 2, p.nombre(), productos.texto);
                HojaExcel.celda(fila, 3, p.marca(), productos.texto);
                HojaExcel.celda(fila, 4, p.presentacion(), productos.texto);
                HojaExcel.celda(fila, 5, p.unidad(), productos.texto);
                HojaExcel.celda(fila, 6, p.stockDisponible(), productos.numero);
                HojaExcel.celda(fila, 7, p.stockMinimo() == 0 ? null : p.stockMinimo(), productos.numero);
                HojaExcel.celda(fila, 8, p.costo(), productos.dinero);
                HojaExcel.celda(fila, 9, p.valor(), productos.dinero);
            }
            productos.terminar(titulos, 10, true);

            HojaExcel lotes = new HojaExcel(libro, "Lotes");
            lotes.lineaSuelta("Lotes con unidades al " + inventario.fecha().format(FECHA), 13, true);
            lotes.saltarFila();
            int titulosLotes = lotes.filaActual();
            lotes.titulos(new String[] { "CÓDIGO", "PRODUCTO", "MARCA", "PRESENTACIÓN", "N° DE LOTE", "VENCE",
                    "CANTIDAD", "COSTO", "ESTADO" },
                    new double[] { 11, 38, 16, 16, 14, 9, 10, 10, 10 });
            for (Exportaciones.Lote l : inventario.lotes()) {
                XSSFRow fila = lotes.fila();
                HojaExcel.celda(fila, 0, l.codigo(), lotes.texto);
                HojaExcel.celda(fila, 1, l.producto(), lotes.texto);
                HojaExcel.celda(fila, 2, l.marca(), lotes.texto);
                HojaExcel.celda(fila, 3, l.presentacion(), lotes.texto);
                HojaExcel.celda(fila, 4, l.numeroLote() == null ? "Sin número" : l.numeroLote(), lotes.numero);
                HojaExcel.celda(fila, 5, l.vencimiento(), lotes.numero);
                HojaExcel.celda(fila, 6, l.cantidad(), lotes.numero);
                HojaExcel.celda(fila, 7, l.costoPromedio(), lotes.dinero);
                HojaExcel.celda(fila, 8, l.vencido() ? "Vencido" : "Vigente", lotes.numero);
            }
            lotes.terminar(titulosLotes, 9, true);
            return escribir(libro);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ================================================================== catálogo (Excel)

    static byte[] catalogoExcel(Catalogo catalogo) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaExcel hoja = new HojaExcel(libro, catalogo.conPrecios() ? "Lista de precios" : "Catálogo");
            hoja.lineaSuelta(catalogo.negocio(), 15, true);
            hoja.lineaSuelta(catalogo.negocioContacto(), 10, false);
            hoja.saltarFila();
            hoja.lineaSuelta(titulo(catalogo), 12, true);
            hoja.lineaSuelta(subtitulo(catalogo), 10, false);
            hoja.saltarFila();
            int titulos = hoja.filaActual();
            int columnas = catalogo.conPrecios() ? 4 : 3;
            hoja.titulos(catalogo.conPrecios()
                    ? new String[] { "DESCRIPCIÓN", "PRESENTACIÓN", "LABORATORIO", "PRECIO (inc. IGV)" }
                    : new String[] { "DESCRIPCIÓN", "PRESENTACIÓN", "LABORATORIO" },
                    catalogo.conPrecios() ? new double[] { 46, 18, 18, 14 } : new double[] { 50, 20, 20 });
            for (Grupo g : catalogo.grupos()) {
                hoja.filaDeGrupo(g.categoria().toUpperCase(Locale.ROOT), columnas);
                for (Item item : g.items()) {
                    XSSFRow fila = hoja.fila();
                    HojaExcel.celda(fila, 0, item.nombre(), hoja.texto);
                    HojaExcel.celda(fila, 1, item.presentacion(), hoja.texto);
                    HojaExcel.celda(fila, 2, item.marca(), hoja.texto);
                    if (catalogo.conPrecios()) {
                        if (item.precio() == null) {
                            HojaExcel.celda(fila, 3, SIN_PRECIO, hoja.numero);
                        } else {
                            HojaExcel.celda(fila, 3, item.precio(), hoja.dinero);
                        }
                    }
                }
            }
            hoja.terminar(titulos, columnas, false);
            return escribir(libro);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ================================================================== catálogo (PDF)

    private static final Color AZUL = new Color(0x1F, 0x4E, 0x79);
    private static final Color GRIS_CLARO = new Color(0xF2, 0xF2, 0xF2);
    private static final Color GRIS_BORDE = new Color(0xA6, 0xA6, 0xA6);
    private static final Color GRIS_TEXTO = new Color(0x59, 0x59, 0x59);
    private static final Font NEGOCIO = fuente(15, Font.BOLD, AZUL);
    private static final Font PEQUENA_GRIS = fuente(9, Font.NORMAL, GRIS_TEXTO);
    private static final Font TITULO = fuente(13, Font.BOLD, Color.BLACK);
    private static final Font NORMAL = fuente(10, Font.NORMAL, Color.BLACK);
    private static final Font TITULO_TABLA = fuente(8, Font.BOLD, Color.WHITE);
    private static final Font CATEGORIA = fuente(10, Font.BOLD, Color.BLACK);
    private static final Font CELDA = fuente(9.5f, Font.NORMAL, Color.BLACK);

    static byte[] catalogoPdf(Catalogo catalogo) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 32, 32, 36, 42);
        PdfWriter.getInstance(documento, salida);
        HeaderFooter pie = new HeaderFooter(new Phrase("Página ", PEQUENA_GRIS), true);
        pie.setAlignment(Element.ALIGN_CENTER);
        pie.setBorder(Rectangle.NO_BORDER);
        documento.setFooter(pie);

        documento.open();
        documento.add(new Paragraph(catalogo.negocio(), NEGOCIO));
        if (!catalogo.negocioContacto().isBlank()) {
            documento.add(new Paragraph(catalogo.negocioContacto(), PEQUENA_GRIS));
        }
        Paragraph titulo = new Paragraph(titulo(catalogo), TITULO);
        titulo.setSpacingBefore(12);
        documento.add(titulo);
        Paragraph subtitulo = new Paragraph(subtitulo(catalogo), NORMAL);
        subtitulo.setSpacingAfter(10);
        documento.add(subtitulo);

        float[] anchos = catalogo.conPrecios() ? new float[] { 50, 18, 18, 14 } : new float[] { 56, 22, 22 };
        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        String[] titulos = catalogo.conPrecios()
                ? new String[] { "DESCRIPCIÓN", "PRESENTACIÓN", "LABORATORIO", "PRECIO (inc. IGV)" }
                : new String[] { "DESCRIPCIÓN", "PRESENTACIÓN", "LABORATORIO" };
        for (String t : titulos) {
            PdfPCell celda = celda(t, TITULO_TABLA, Element.ALIGN_CENTER);
            celda.setBackgroundColor(AZUL);
            tabla.addCell(celda);
        }
        for (Grupo g : catalogo.grupos()) {
            PdfPCell categoria = celda(g.categoria().toUpperCase(Locale.ROOT), CATEGORIA, Element.ALIGN_LEFT);
            categoria.setColspan(anchos.length);
            categoria.setBackgroundColor(GRIS_CLARO);
            tabla.addCell(categoria);
            for (Item item : g.items()) {
                tabla.addCell(celda(item.nombre(), CELDA, Element.ALIGN_LEFT));
                tabla.addCell(celda(item.presentacion(), CELDA, Element.ALIGN_LEFT));
                tabla.addCell(celda(item.marca(), CELDA, Element.ALIGN_LEFT));
                if (catalogo.conPrecios()) {
                    tabla.addCell(celda(item.precio() == null ? SIN_PRECIO : dinero(item.precio()), CELDA,
                            Element.ALIGN_RIGHT));
                }
            }
        }
        if (catalogo.grupos().isEmpty()) {
            PdfPCell vacio = celda("No hay productos para mostrar.", CELDA, Element.ALIGN_CENTER);
            vacio.setColspan(anchos.length);
            tabla.addCell(vacio);
        }
        documento.add(tabla);
        documento.close();
        return salida.toByteArray();
    }

    // ================================================================== utilidades

    private static String titulo(Catalogo catalogo) {
        return catalogo.conPrecios() ? "LISTA DE PRECIOS" : "CATÁLOGO DE PRODUCTOS";
    }

    private static String subtitulo(Catalogo catalogo) {
        String fecha = "Actualizado al " + catalogo.fecha().format(FECHA);
        return catalogo.conPrecios()
                ? "Cliente: " + catalogo.cliente() + " · " + fecha + " · Precios incluyen IGV"
                : fecha;
    }

    private static byte[] escribir(XSSFWorkbook libro) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        libro.write(salida);
        return salida.toByteArray();
    }

    private static PdfPCell celda(String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celda.setMinimumHeight(20f);
        celda.setPadding(4);
        celda.setBorderColor(GRIS_BORDE);
        return celda;
    }

    private static String dinero(BigDecimal valor) {
        return "S/ " + String.format(Locale.US, "%,.2f", valor);
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }
}
