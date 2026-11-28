/**
 * Cerrar el teclado del celular al deslizar la pantalla (ver useCerrarTecladoAlDeslizar en hooks.ts).
 * Aquí solo están las decisiones, sin tocar la página, para poder probarlas.
 */

/** Cuánto (en px) hay que deslizar el dedo para cerrar el teclado: menos que eso es un toque. */
export const DISTANCIA_PARA_CERRAR = 12

export interface Punto {
  x: number
  y: number
}

/** ¿El dedo se deslizó hacia arriba o hacia abajo (más que hacia los lados) lo suficiente? */
export function deslizoVertical(inicio: Punto, actual: Punto, distancia = DISTANCIA_PARA_CERRAR): boolean {
  const vertical = Math.abs(actual.y - inicio.y)
  return vertical >= distancia && vertical > Math.abs(actual.x - inicio.x)
}

/** Campos <input> que no abren el teclado (casillas, botones, archivos…). */
const SIN_TECLADO = new Set(['checkbox', 'radio', 'range', 'button', 'submit', 'reset', 'file', 'color', 'image', 'hidden'])

/** ¿Un elemento con esta etiqueta y tipo abre el teclado del celular? */
export function abreTeclado(etiqueta: string, tipo = ''): boolean {
  const nombre = etiqueta.toUpperCase()
  return nombre === 'TEXTAREA' || (nombre === 'INPUT' && !SIN_TECLADO.has(tipo.toLowerCase()))
}
