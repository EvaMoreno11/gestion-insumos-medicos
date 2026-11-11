import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router'

// ---------------------------------------------------------------- botones

type Variante = 'primario' | 'secundario' | 'peligro' | 'texto'

const ESTILOS: Record<Variante, string> = {
  primario: 'bg-marca text-white active:bg-marca-oscuro disabled:bg-slate-300',
  secundario: 'bg-white text-marca border border-slate-300 active:bg-slate-100 disabled:text-slate-400',
  peligro: 'bg-white text-red-700 border border-red-200 active:bg-red-50 disabled:text-slate-400',
  texto: 'text-marca active:bg-marca-claro disabled:text-slate-400',
}

const BASE_BOTON =
  'inline-flex min-h-12 items-center justify-center gap-2 rounded-xl px-4 text-base font-semibold transition-colors disabled:cursor-not-allowed'

interface BotonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: Variante
  ancho?: boolean
}

export function Boton({ variante = 'primario', ancho, className = '', type = 'button', ...resto }: BotonProps) {
  return (
    <button
      type={type}
      className={`${BASE_BOTON} ${ESTILOS[variante]} ${ancho ? 'w-full' : ''} ${className}`}
      {...resto}
    />
  )
}

/** Enlace con aspecto de botón. */
export function BotonEnlace({
  a,
  variante = 'primario',
  ancho,
  children,
}: {
  a: string
  variante?: Variante
  ancho?: boolean
  children: ReactNode
}) {
  return (
    <Link to={a} className={`${BASE_BOTON} ${ESTILOS[variante]} ${ancho ? 'w-full' : ''}`}>
      {children}
    </Link>
  )
}

// ---------------------------------------------------------------- campos

export const ESTILO_CAMPO =
  'block w-full rounded-xl border border-slate-300 bg-white px-3 py-3 text-slate-900 placeholder:text-slate-400 focus:border-marca focus:outline-none focus:ring-2 focus:ring-marca/20 disabled:bg-slate-100'

interface CampoProps extends InputHTMLAttributes<HTMLInputElement> {
  etiqueta: string
  ayuda?: string
  error?: string
}

/** Campo de texto con su etiqueta. Para números usa inputMode="numeric" o "decimal": abre el teclado numérico. */
export function Campo({ etiqueta, ayuda, error, className = '', ...resto }: CampoProps) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{etiqueta}</span>
      <input className={`${ESTILO_CAMPO} ${error ? 'border-red-400' : ''} ${className}`} {...resto} />
      {error ? (
        <span className="mt-1 block text-sm text-red-700">{error}</span>
      ) : (
        ayuda && <span className="mt-1 block text-sm text-slate-500">{ayuda}</span>
      )}
    </label>
  )
}

/** Campo de búsqueda con botón para borrar. */
export function Buscador({
  valor,
  onCambio,
  placeholder,
  autoFocus,
}: {
  valor: string
  onCambio: (valor: string) => void
  placeholder: string
  autoFocus?: boolean
}) {
  return (
    <div className="relative">
      <input
        type="search"
        enterKeyHint="search"
        autoComplete="off"
        autoCorrect="off"
        autoFocus={autoFocus}
        value={valor}
        onChange={(e) => onCambio(e.target.value)}
        placeholder={placeholder}
        className={`${ESTILO_CAMPO} pr-11 [&::-webkit-search-cancel-button]:hidden`}
      />
      {valor && (
        <button
          type="button"
          aria-label="Borrar búsqueda"
          onClick={() => onCambio('')}
          className="absolute right-1 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full text-xl text-slate-400 active:bg-slate-100"
        >
          ×
        </button>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- avisos y estados

type TipoAviso = 'error' | 'alerta' | 'exito' | 'info'

const ESTILO_AVISO: Record<TipoAviso, string> = {
  error: 'border-red-200 bg-red-50 text-red-800',
  alerta: 'border-amber-200 bg-amber-50 text-amber-900',
  exito: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  info: 'border-slate-200 bg-white text-slate-700',
}

export function Aviso({ tipo = 'info', children }: { tipo?: TipoAviso; children: ReactNode }) {
  return (
    <div role={tipo === 'error' ? 'alert' : 'status'} className={`rounded-xl border px-4 py-3 text-sm ${ESTILO_AVISO[tipo]}`}>
      {children}
    </div>
  )
}

export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return <p className="py-8 text-center text-slate-500">{texto}</p>
}

export function Vacio({ children }: { children: ReactNode }) {
  return <p className="rounded-xl border border-dashed border-slate-300 px-4 py-8 text-center text-slate-500">{children}</p>
}

/** Muestra el estado de una carga: error, cargando, o el contenido cuando ya hay datos. */
export function EstadoCarga({
  cargando,
  error,
  hayDatos,
  children,
}: {
  cargando: boolean
  error: string | null
  hayDatos: boolean
  children: ReactNode
}) {
  if (error) {
    return <Aviso tipo="error">{error}</Aviso>
  }
  if (!hayDatos) {
    return cargando ? <Cargando /> : null
  }
  return <>{children}</>
}

export function Tarjeta({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-2xl border border-slate-200 bg-white p-4 ${className}`}>{children}</div>
}

/** Etiqueta pequeña de color (estado, categoría, aviso). */
export function Etiqueta({
  color,
  children,
}: {
  color: 'verde' | 'ambar' | 'rojo' | 'gris' | 'azul'
  children: ReactNode
}) {
  const estilos = {
    verde: 'bg-emerald-100 text-emerald-800',
    ambar: 'bg-amber-100 text-amber-900',
    rojo: 'bg-red-100 text-red-800',
    gris: 'bg-slate-200 text-slate-700',
    azul: 'bg-marca-claro text-marca',
  }
  return (
    <span className={`inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${estilos[color]}`}>
      {children}
    </span>
  )
}
