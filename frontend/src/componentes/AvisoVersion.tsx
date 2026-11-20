import { useEffect, useState } from 'react'
import { scriptPrincipal } from '../util/version'

const CADA_DIEZ_MINUTOS = 10 * 60 * 1000

/**
 * Avisa cuando se publicó una versión nueva de la app. En el iPhone, la app instalada queda abierta en
 * segundo plano con la versión vieja; con este aviso se actualiza de un toque, sin cerrarla a mano.
 *
 * Revisa al volver a la app y cada 10 minutos. Nunca recarga sola: así no se pierde lo que se está
 * escribiendo (por ejemplo, un pedido a medias).
 */
export function AvisoVersion() {
  const [hayNueva, setHayNueva] = useState(false)
  const [oculto, setOculto] = useState(false)

  useEffect(() => {
    // En desarrollo no hay versiones publicadas
    if (!import.meta.env.PROD) {
      return
    }
    const actual = document.querySelector('script[type="module"][src*="/assets/"]')?.getAttribute('src') ?? null
    if (!actual) {
      return
    }
    let revisando = false
    async function revisar() {
      if (revisando || document.visibilityState !== 'visible') {
        return
      }
      revisando = true
      try {
        const respuesta = await fetch('/', { cache: 'no-store' })
        const publicada = respuesta.ok ? scriptPrincipal(await respuesta.text()) : null
        if (publicada && publicada !== actual) {
          setHayNueva(true)
          setOculto(false) // si se cerró con "Más tarde", vuelve a aparecer al regresar a la app
        }
      } catch {
        // Sin conexión: se vuelve a revisar más tarde
      } finally {
        revisando = false
      }
    }
    const intervalo = window.setInterval(revisar, CADA_DIEZ_MINUTOS)
    document.addEventListener('visibilitychange', revisar)
    window.addEventListener('focus', revisar)
    window.addEventListener('pageshow', revisar)
    return () => {
      window.clearInterval(intervalo)
      document.removeEventListener('visibilitychange', revisar)
      window.removeEventListener('focus', revisar)
      window.removeEventListener('pageshow', revisar)
    }
  }, [])

  if (!hayNueva || oculto) {
    return null
  }
  return (
    <div
      role="status"
      className="fixed inset-x-0 top-0 z-[60] border-b border-amber-300 bg-amber-50 pt-[env(safe-area-inset-top)] text-amber-950 shadow-md"
    >
      <div className="mx-auto flex max-w-3xl items-center gap-2 px-4 py-2">
        <p className="min-w-0 flex-1 text-sm font-medium">Hay una versión nueva de la app.</p>
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="min-h-11 rounded-xl bg-marca px-4 text-sm font-semibold text-white active:bg-marca-oscuro"
        >
          Actualizar
        </button>
        <button
          type="button"
          onClick={() => setOculto(true)}
          className="flex h-11 w-11 items-center justify-center rounded-full text-xl text-amber-900 active:bg-amber-100"
          aria-label="Más tarde"
        >
          ×
        </button>
      </div>
    </div>
  )
}
