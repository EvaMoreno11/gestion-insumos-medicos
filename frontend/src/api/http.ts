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

/** Evento que se emite cuando la API responde 401: la app vuelve a mostrar la pantalla de entrada. */
export const EVENTO_SESION_VENCIDA = 'sesion-vencida'

/** Valor de una cookie que el JavaScript puede leer (p. ej. el token CSRF). */
export function leerCookie(nombre: string, cookies: string = document.cookie): string | null {
  for (const parte of cookies.split(';')) {
    const [clave, ...valor] = parte.trim().split('=')
    if (clave === nombre) {
      return decodeURIComponent(valor.join('='))
    }
  }
  return null
}

async function enviar(ruta: string, { metodo = 'GET', cuerpo, params }: Opciones): Promise<Response> {
  const cabeceras: Record<string, string> = {}
  if (cuerpo !== undefined) {
    cabeceras['Content-Type'] = 'application/json'
  }
  // Protección CSRF: toda operación que cambia datos reenvía el token que el servidor dejó en la cookie
  const token = metodo === 'GET' ? null : leerCookie('XSRF-TOKEN')
  if (token) {
    cabeceras['X-XSRF-TOKEN'] = token
  }

  let respuesta: Response
  try {
    respuesta = await fetch(construirUrl(ruta, params), {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      credentials: 'same-origin',
    })
  } catch {
    throw new ErrorApi('No se pudo conectar con el servidor. Revisa tu conexión a internet.', 0)
  }
  if (!respuesta.ok) {
    const cuerpoError: unknown = await respuesta.json().catch(() => null)
    // La sesión venció: se avisa a la app (salvo en las propias rutas de entrada, que manejan su error)
    if (respuesta.status === 401 && !ruta.startsWith('/api/auth/')) {
      window.dispatchEvent(new Event(EVENTO_SESION_VENCIDA))
    }
    throw interpretarError(respuesta.status, cuerpoError)
  }
  return respuesta
}

export async function api<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const respuesta = await enviar(ruta, opciones)
  // Algunas respuestas no traen contenido (204, o 201 al crear la cuenta)
  const texto = await respuesta.text()
  return (texto === '' ? undefined : JSON.parse(texto)) as T
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
