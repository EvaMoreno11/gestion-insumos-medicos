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

/** Fecha local del dispositivo (en Lima) en aaaa-mm-dd. */
export function diaLocal(fecha: Date = new Date()): string {
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}`
}

/** "2026-11-21" y −6 → "2026-11-15". */
export function sumarDias(dia: string, dias: number): string {
  const [anio, mes, d] = dia.split('-').map(Number)
  return diaLocal(new Date(anio, mes - 1, d + dias))
}

/** Cuánto pasó desde un día (aaaa-mm-dd): "hoy", "ayer" o "hace 6 días". */
export function haceDias(dia: string, hoy: string = diaLocal()): string {
  const [a1, m1, d1] = dia.split('-').map(Number)
  const [a2, m2, d2] = hoy.split('-').map(Number)
  const dias = Math.round((Date.UTC(a2, m2 - 1, d2) - Date.UTC(a1, m1 - 1, d1)) / 86_400_000)
  if (dias <= 0) {
    return 'hoy'
  }
  return dias === 1 ? 'ayer' : `hace ${dias} días`
}

/** Título de un día (aaaa-mm-dd) en una lista: "Hoy", "Ayer" o "17/11/2026". */
export function nombreDeFecha(dia: string, hoy: Date = new Date()): string {
  const deHoy = diaLocal(hoy)
  if (dia === deHoy) {
    return 'Hoy'
  }
  if (dia === sumarDias(deHoy, -1)) {
    return 'Ayer'
  }
  return fechaDia(dia)
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

/** Costo leído: el valor, o por qué todavía no se puede usar (pendiente = falta escribir algo, no es un error). */
export type CostoLeido = { valor: number; division: boolean } | { error: string; pendiente: boolean }

/**
 * Lee el costo escrito: un número ("8.50") o una división ("30÷12", también "30/12") para lo que se compra por
 * docena o paquete y se registra por unidad. La división se redondea a 4 decimales, como se guarda el costo, así
 * 25÷12 = 2.0833 y 12 unidades siguen sumando S/ 25.00. Vacío → null.
 */
export function leerCosto(texto: string): CostoLeido | null {
  const limpio = texto.replace(/\s+/g, '').replace('/', '÷')
  if (limpio === '') {
    return null
  }
  const partes = limpio.split('÷')
  if (partes.length === 1) {
    const valor = leerDecimal(limpio)
    return valor === null ? { error: 'Escribe un número, p. ej. 8.50', pendiente: false } : { valor, division: false }
  }
  const [precio, unidades] = partes
  if (partes.length > 2) {
    return { error: 'Escribe una sola división, p. ej. 30÷12', pendiente: false }
  }
  if (precio === '') {
    return { error: 'Escribe primero el precio del paquete, p. ej. 30÷12', pendiente: true }
  }
  if (unidades === '') {
    return { error: 'Escribe entre cuántas unidades dividir, p. ej. 30÷12', pendiente: true }
  }
  const a = leerDecimal(precio)
  const b = leerDecimal(unidades)
  if (a === null || b === null) {
    return { error: 'Escribe números, p. ej. 30÷12', pendiente: false }
  }
  if (b === 0) {
    return { error: 'No se puede dividir entre 0', pendiente: false }
  }
  return { valor: Math.round((a / b) * 10000) / 10000, division: true }
}

/** Lo que se deja escribir en el costo: números, punto o coma y una sola división ("/" se muestra como "÷"). */
export function limpiarCosto(texto: string): string {
  const limpio = texto.replace(/\//g, '÷').replace(/[^\d.,÷]/g, '')
  const division = limpio.indexOf('÷')
  return division === -1 ? limpio : limpio.slice(0, division + 1) + limpio.slice(division + 1).replace(/÷/g, '')
}

/** Como dinero(), pero con hasta 4 decimales si los tiene: 2.5 → "S/ 2.50", 2.0833 → "S/ 2.0833". */
export function dineroExacto(valor: number): string {
  return 'S/ ' + valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

/** Une solo los textos que existen: unir(" · ", "Caja x 100", null, "Marca A") → "Caja x 100 · Marca A". */
export function unir(separador: string, ...partes: (string | null | undefined)[]): string {
  return partes.filter((p) => p !== null && p !== undefined && p.trim() !== '').join(separador)
}

/** Número de lote como lo guarda el servidor: sin espacios de más y en mayúsculas; vacío = sin número (null). */
export function numeroDeLote(texto: string): string | null {
  const limpio = texto.trim().replace(/\s+/g, ' ').toUpperCase()
  return limpio === '' ? null : limpio
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
  return diaLocal()
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
