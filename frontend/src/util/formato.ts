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

const dos = (n: number) => String(n).padStart(2, '0')

/** "2026-11-10T15:30:00Z" → "10/11/2026" (fecha local del dispositivo). */
export function fechaCorta(instante: string | null | undefined): string {
  if (!instante) {
    return ''
  }
  const d = new Date(instante)
  return `${dos(d.getDate())}/${dos(d.getMonth() + 1)}/${d.getFullYear()}`
}

/** "2026-11-10T15:30:00Z" → "10:30" (hora local del dispositivo, en Lima). */
export function hora(instante: string): string {
  const d = new Date(instante)
  return `${dos(d.getHours())}:${dos(d.getMinutes())}`
}

/** Título de un día en una lista: "Hoy", "Ayer" o "17/11/2026" (`hoy` se pasa para poder probarlo). */
export function nombreDelDia(instante: string, hoy: Date = new Date()): string {
  const d = new Date(instante)
  const mismoDia = (a: Date, b: Date) =>
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  const ayer = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - 1)
  if (mismoDia(d, hoy)) {
    return 'Hoy'
  }
  if (mismoDia(d, ayer)) {
    return 'Ayer'
  }
  return fechaCorta(instante)
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

/** Para comparar textos sin mayúsculas, tildes ni espacios de más: "  Algodón " y "ALGODON" → "algodon". */
export function textoComparable(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ')
}

const DICCIONARIO = new Intl.Collator('es', { sensitivity: 'base', numeric: true })

/** Ordena por nombre como un diccionario: sin importar mayúsculas ni tildes ("Agujas" junto a "AGUJA DENTAL"). */
export function ordenarPorNombre<T extends { nombre: string }>(lista: readonly T[]): T[] {
  return [...lista].sort((a, b) => DICCIONARIO.compare(a.nombre, b.nombre))
}

/** 15 → "15 %", 12.5 → "12.5 %". */
export function porcentaje(valor: number): string {
  return `${Number(valor.toFixed(2))} %`
}

/** Cuándo vence un lote: "Vence en 45 días", "Vence mañana", "Vence hoy", "Venció hace 10 días". */
export function cuandoVence(dias: number): string {
  if (dias < 0) {
    return dias === -1 ? 'Venció ayer' : `Venció hace ${-dias} días`
  }
  if (dias === 0) {
    return 'Vence hoy'
  }
  return dias === 1 ? 'Vence mañana' : `Vence en ${dias} días`
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'set', 'oct', 'nov', 'dic']
const MESES_LARGOS = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre',
]

/** "2026-06" → "jun". */
export function mesCorto(mes: string): string {
  return MESES[Number(mes.slice(5, 7)) - 1] ?? mes
}

/** "2026-06" → "junio 2026". */
export function mesLargo(mes: string): string {
  return `${MESES_LARGOS[Number(mes.slice(5, 7)) - 1] ?? mes} ${mes.slice(0, 4)}`
}

/** Fecha de la API ("2026-03-01") → "01/03/2026", sin pasar por la zona horaria. */
export function fechaDia(fecha: string | null | undefined): string {
  if (!fecha) {
    return '—'
  }
  const [anio, mes, dia] = fecha.split('-')
  return `${dia}/${mes}/${anio}`
}

/** "2026-06-01", "2026-06-15" → "1 jun – 15 jun 2026" (o "28 dic 2025 – 3 ene 2026"). */
export function rangoFechas(desde: string, hasta: string): string {
  const parte = (fecha: string) => `${Number(fecha.slice(8, 10))} ${MESES[Number(fecha.slice(5, 7)) - 1]}`
  const mismoAnio = desde.slice(0, 4) === hasta.slice(0, 4)
  return mismoAnio
    ? `${parte(desde)} – ${parte(hasta)} ${hasta.slice(0, 4)}`
    : `${parte(desde)} ${desde.slice(0, 4)} – ${parte(hasta)} ${hasta.slice(0, 4)}`
}

/** Variación % de un valor respecto del anterior, redondeada; null si antes era 0 (no se puede comparar). */
export function variacion(actual: number, anterior: number): number | null {
  if (anterior === 0) {
    return null
  }
  return Math.round(((actual - anterior) / Math.abs(anterior)) * 100)
}

/** Hoy en la hora del dispositivo, como aaaa-mm-dd. */
export function hoyLocal(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Plazo de pago: null → "Sin plazo", 0 → "Contado", 15 → "Crédito a 15 días". */
export function textoPlazo(plazoDias: number | null | undefined): string {
  if (plazoDias === null || plazoDias === undefined) {
    return 'Sin plazo'
  }
  if (plazoDias === 0) {
    return 'Contado'
  }
  return `Crédito a ${plazoDias} ${plazoDias === 1 ? 'día' : 'días'}`
}
