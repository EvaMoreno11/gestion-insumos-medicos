/**
 * Cliente HTTP mínimo para hablar con el backend.
 * Convierte los errores "Problem Details" de la API en un ErrorApi con un mensaje listo para mostrar.
 */

// En desarrollo queda vacío (Vite reenvía /api al backend); en producción es la URL del servidor
const BASE = import.meta.env.VITE_API_URL ?? ''

export class ErrorApi extends Error {
  status: number
  /** Errores de validación por campo, p. ej. { nombre: "El nombre es obligatorio" } */
  errores: Record<string, string>

  constructor(mensaje: string, status: number, errores: Record<string, string> = {}) {
    super(mensaje)
    this.name = 'ErrorApi'
    this.status = status
    this.errores = errores
  }
}

type Params = Record<string, string | number | boolean | null | undefined>

interface Opciones {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  cuerpo?: unknown
  params?: Params
}

export function construirUrl(ruta: string, params?: Params): string {
  const query = new URLSearchParams()
  for (const [clave, valor] of Object.entries(params ?? {})) {
    if (valor !== null && valor !== undefined && valor !== '') {
      query.set(clave, String(valor))
    }
  }
  const texto = query.toString()
  return BASE + ruta + (texto ? '?' + texto : '')
}

/** Arma el error a partir de la respuesta de la API (o de un cuerpo que no es JSON). */
export function interpretarError(status: number, cuerpo: unknown): ErrorApi {
  const problema = (typeof cuerpo === 'object' && cuerpo !== null ? cuerpo : {}) as {
    detail?: string
    title?: string
    errores?: Record<string, string>
  }
  const errores = problema.errores ?? {}
  const detalleDeCampos = Object.values(errores).join('. ')
  const mensaje =
    detalleDeCampos ||
    problema.detail ||
    problema.title ||
    (status >= 500 ? 'El servidor no responde. Intenta de nuevo en un momento.' : `Error ${status}`)
  return new ErrorApi(mensaje, status, errores)
}

async function enviar(ruta: string, { metodo = 'GET', cuerpo, params }: Opciones): Promise<Response> {
  let respuesta: Response
  try {
    respuesta = await fetch(construirUrl(ruta, params), {
      method: metodo,
      headers: cuerpo === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
  } catch {
    throw new ErrorApi('No se pudo conectar con el servidor. Revisa tu conexión a internet.', 0)
  }
  if (!respuesta.ok) {
    const cuerpoError: unknown = await respuesta.json().catch(() => null)
    throw interpretarError(respuesta.status, cuerpoError)
  }
  return respuesta
}

export async function api<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const respuesta = await enviar(ruta, opciones)
  if (respuesta.status === 204) {
    return undefined as T
  }
  return (await respuesta.json()) as T
}

/** Descarga un archivo (PDF, Excel) como Blob. */
export async function descargar(ruta: string): Promise<Blob> {
  const respuesta = await enviar(ruta, {})
  return respuesta.blob()
}

/** Mensaje para mostrar al usuario a partir de cualquier error. */
export function mensajeDe(error: unknown): string {
  return error instanceof Error ? error.message : 'Ocurrió un error inesperado'
}
