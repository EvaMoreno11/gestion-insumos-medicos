import type { ReactNode } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router'

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
  /**
   * Ancho del contenido en pantallas grandes: "amplio" para listas, tablas y el pedido;
   * "normal" para formularios (un formulario muy ancho cuesta leerlo). En el celular no cambia nada.
   */
  ancho?: 'normal' | 'amplio'
  children: ReactNode
}

// En el celular todo ocupa como máximo 36 rem; en la PC (lg = 1024 px o más) se ensancha
const ANCHO_CONTENIDO = {
  normal: 'max-w-xl lg:max-w-3xl',
  amplio: 'max-w-xl lg:max-w-6xl',
}

const SECCIONES = [
  { a: '/', icono: '🏠', titulo: 'Inicio', exacta: true },
  { a: '/inventario', icono: '🔎', titulo: 'Inventario' },
  { a: '/pedidos', icono: '🧾', titulo: 'Pedidos' },
  { a: '/entrada', icono: '📦', titulo: 'Registrar entrada' },
  { a: '/por-comprar', icono: '🛒', titulo: 'Por comprar' },
  { a: '/clientes', icono: '👥', titulo: 'Clientes' },
  { a: '/productos', icono: '🏷️', titulo: 'Productos' },
  { a: '/configuracion', icono: '⚙️', titulo: 'Configuración' },
]

/** Menú fijo a la izquierda, solo en pantallas grandes: se salta entre secciones sin volver al inicio. */
function MenuLateral() {
  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-slate-200 bg-white lg:flex">
      <div className="flex h-16 items-center gap-3 bg-marca px-5 text-white">
        <img src="/favicon.svg" alt="" className="h-8 w-8 rounded-lg ring-1 ring-white/30" />
        <span className="text-lg font-semibold">Insumos Médicos</span>
      </div>
      <nav className="flex-1 space-y-1 overflow-y-auto p-3">
        {SECCIONES.map((s) => (
          <NavLink
            key={s.a}
            to={s.a}
            end={s.exacta}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-xl px-3 py-3 font-medium ${
                isActive ? 'bg-marca text-white' : 'text-slate-700 hover:bg-slate-100'
              }`
            }
          >
            <span className="w-7 text-center text-xl" aria-hidden="true">
              {s.icono}
            </span>
            {s.titulo}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}

/** Estructura común de todas las pantallas: barra superior, contenido, pie opcional y, en la PC, menú lateral. */
export function Pantalla({ titulo, volver, accion, pie, ancho = 'normal', children }: Props) {
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const contenedor = `mx-auto ${ANCHO_CONTENIDO[ancho]}`

  function irAtras() {
    // key "default" = primera pantalla abierta en esta visita: no hay a dónde retroceder
    if (ubicacion.key !== 'default') {
      navegar(-1)
    } else if (volver) {
      navegar(volver, { replace: true })
    }
  }

  return (
    <div className="min-h-dvh lg:pl-64">
      <MenuLateral />

      <header className="sticky top-0 z-20 bg-marca pt-[env(safe-area-inset-top)] text-white shadow-sm">
        <div className={`${contenedor} flex h-14 items-center gap-1 px-2 lg:h-16 lg:px-6`}>
          {volver ? (
            <button
              type="button"
              onClick={irAtras}
              aria-label="Volver"
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full pb-1 text-3xl leading-none active:bg-white/15 lg:hover:bg-white/15"
            >
              ‹
            </button>
          ) : (
            <span className="w-2" />
          )}
          <h1 className="min-w-0 flex-1 truncate text-lg font-semibold lg:text-xl">{titulo}</h1>
          {accion}
        </div>
      </header>

      <main className={`${contenedor} px-4 pt-4 lg:px-6 lg:pt-6 ${pie ? 'pb-48' : 'pb-10'}`}>{children}</main>

      {pie && (
        <footer className="fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white pb-[env(safe-area-inset-bottom)] shadow-[0_-4px_12px_rgba(0,0,0,0.06)] lg:left-64">
          <div className={`${contenedor} px-4 py-3 lg:px-6`}>{pie}</div>
        </footer>
      )}
    </div>
  )
}
