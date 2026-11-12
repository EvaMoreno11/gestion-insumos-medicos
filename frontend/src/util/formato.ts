/** Funciones puras para mostrar y leer datos: dinero, vencimientos (mes/año) y números. */

/** 1234.5 → "S/ 1,234.50". Sin valor → "—". */
export function dinero(valor: number | null | undefined): string {
  if (valor === null || valor === undefined) {
    return '—'
  }
  return 'S/ ' + valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** "2027-03-31" → "03/2027". Sin fecha → "". */
export function mesAnio(fecha: string | null | undefined): string {
  if (!fecha) {
    return ''
  }
  const [anio, mes] = fecha.split('-')
  return `${mes}/${anio}`
}

/** "2026-11-10T15:30:00Z" → "10/11/2026" (fecha local del dispositivo). */
export function fechaCorta(instante: string | null | undefined): string {
  if (!instante) {
    return ''
  }
  const d = new Date(instante)
  const dos = (n: number) => String(n).padStart(2, '0')
  return `${dos(d.getDate())}/${dos(d.getMonth() + 1)}/${d.getFullYear()}`
}

/**
 * Da formato a lo que se escribe en el campo de vencimiento: solo dígitos y la barra
 * se pone sola. "0327" → "03/27", "032027" → "03/2027".
 */
export function escribirMesAnio(texto: string): string {
  const digitos = texto.replace(/\D/g, '').slice(0, 6)
  return digitos.length <= 2 ? digitos : digitos.slice(0, 2) + '/' + digitos.slice(2)
}

/**
 * Convierte "03/2027" en la fecha que espera la API ("2027-03-01"; el servidor toma el fin de mes).
 * @returns la fecha, null si el campo está vacío (no vence) o undefined si lo escrito no es válido
 */
export function mesAnioAFecha(texto: string): string | null | undefined {
  const limpio = texto.trim()
  if (limpio === '') {
    return null
  }
  const partes = /^(\d{1,2})\/(\d{4})$/.exec(limpio)
  if (!partes) {
    return undefined
  }
  const mes = Number(partes[1])
  const anio = Number(partes[2])
  if (mes < 1 || mes > 12 || anio < 2000 || anio > 2100) {
    return undefined
  }
  return `${anio}-${String(mes).padStart(2, '0')}-01`
}

/** "12" → 12. Vacío, decimales, negativos o texto → null. */
export function leerEntero(texto: string): number | null {
  const limpio = texto.trim()
  if (!/^\d+$/.test(limpio)) {
    return null
  }
  return Number(limpio)
}

/** "8,5" o "8.50" → 8.5. Vacío, negativo o texto → null. */
export function leerDecimal(texto: string): number | null {
  const limpio = texto.trim().replace(',', '.')
  if (!/^\d+(\.\d+)?$/.test(limpio)) {
    return null
  }
  return Number(limpio)
}

/** Une solo los textos que existen: unir(" · ", "Caja x 100", null, "Marca A") → "Caja x 100 · Marca A". */
export function unir(separador: string, ...partes: (string | null | undefined)[]): string {
  return partes.filter((p) => p !== null && p !== undefined && p.trim() !== '').join(separador)
}

/** 15 → "15 %", 12.5 → "12.5 %". */
export function porcentaje(valor: number): string {
  return `${Number(valor.toFixed(2))} %`
}
