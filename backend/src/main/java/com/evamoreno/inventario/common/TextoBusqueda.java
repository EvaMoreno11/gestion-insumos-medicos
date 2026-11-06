package com.evamoreno.inventario.common;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Normaliza textos para que la búsqueda no dependa de cómo se escriba:
 * mayúsculas, tildes, espacios o "5ml" vs "5 ml".
 *
 * <pre>
 * "Jeringa 5ml c/ aguja 21G x 1½\"" → "jeringa 5 ml c aguja 21 g x 1 1/2"
 * "Gasa Estéril 10x10 cm"            → "gasa esteril 10 x 10 cm"
 * </pre>
 */
public final class TextoBusqueda {

    private static final int MAX_TOKENS = 10;

    private TextoBusqueda() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String t = texto
                .replace("½", " 1/2 ")
                .replace("¼", " 1/4 ")
                .replace("¾", " 3/4 ");
        // Quitar tildes: "é" → "e", "ñ" → "n"
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.toLowerCase(Locale.ROOT);
        // Coma decimal → punto: "2,5" → "2.5"
        t = t.replaceAll("(?<=\\d),(?=\\d)", ".");
        // Separar números de letras: "5ml" → "5 ml", "x100" → "x 100"
        t = t.replaceAll("(?<=\\d)(?=[a-z])|(?<=[a-z])(?=\\d)", " ");
        // Todo lo que no sea letra, número, punto o barra → espacio
        t = t.replaceAll("[^a-z0-9./]+", " ");
        // Punto y barra solo se conservan entre números ("2.5", "1/2")
        t = t.replaceAll("(?<!\\d)[./]|[./](?!\\d)", " ");
        return t.trim().replaceAll("\\s+", " ");
    }

    /** Palabras de la búsqueda, ya normalizadas y sin repetir. */
    public static List<String> tokens(String busqueda) {
        String normalizado = normalizar(busqueda);
        if (normalizado.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalizado.split(" "))
                .distinct()
                .limit(MAX_TOKENS)
                .toList();
    }

    /** Limpia un texto para guardarlo: sin espacios sobrantes. Conserva mayúsculas y tildes. */
    public static String limpiar(String texto) {
        return texto == null ? null : texto.trim().replaceAll("\\s+", " ");
    }
}
