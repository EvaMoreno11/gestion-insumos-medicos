import type { ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'

interface Props {
  titulo: string
  /**
   * Muestra el botón "atrás". Regresa a la pantalla anterior (conservando, por ejemplo, la búsqueda);
   * si se entró directo por un enlace, va a esta ruta. Sin ella no hay botón (pantalla de inicio).
   */
  volver?: string
  /** Botón o enlace a la derecha del título */
  accion?: ReactNode
  /** Barra fija en la parte inferior (totales, botón principal) */
  pie?: ReactNode
  children: ReactNode
}

/** Estructura común de todas las pantallas: barra superior, contenido y pie opcional. */
export function Pantalla({ titulo, volver, accion, pie, children }: Props) {
  const navegar = useNavigate()
  const ubicacion = useLocation()

  function irAtras() {
    // key "default" = primera pantalla abierta en esta visita: no hay a dónde retroceder
    if (ubicacion.key !== 'default') {
      navegar(-1)
    } else if (volver) {
      navegar(volver, { replace: true })
    }
  }

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-20 bg-marca pt-[env(safe-area-inset-top)] text-white shadow-sm">
        <div className="mx-auto flex h-14 max-w-xl items-center gap-1 px-2">
          {volver ? (
            <button
              type="button"
              onClick={irAtras}
              aria-label="Volver"
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full pb-1 text-3xl leading-none active:bg-white/15"
            >
              ‹
            </button>
          ) : (
            <span className="w-2" />
          )}
          <h1 className="min-w-0 flex-1 truncate text-lg font-semibold">{titulo}</h1>
          {accion}
        </div>
      </header>

      <main className={`mx-auto max-w-xl px-4 pt-4 ${pie ? 'pb-48' : 'pb-10'}`}>{children}</main>

      {pie && (
        <footer className="fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white pb-[env(safe-area-inset-bottom)] shadow-[0_-4px_12px_rgba(0,0,0,0.06)]">
          <div className="mx-auto max-w-xl px-4 py-3">{pie}</div>
        </footer>
      )}
    </div>
  )
}
