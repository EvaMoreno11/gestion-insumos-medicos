/**
 * Archivo principal de la app dentro de un index.html, p. ej. "/assets/index-BD7C7c_j.js". Su nombre cambia
 * con cada versión publicada, así que compararlo con el de la versión abierta dice si hay una nueva.
 */
export function scriptPrincipal(html: string): string | null {
  const encontrado = html.match(/<script\b[^>]*\bsrc="([^"]*\/assets\/[^"]+\.js)"/)
  return encontrado ? encontrado[1] : null
}
