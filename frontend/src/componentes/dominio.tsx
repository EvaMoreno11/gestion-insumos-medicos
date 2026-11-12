import { useState } from 'react'
import { productos } from '../api/recursos'
import type { CategoriaPrecio, EstadoStock, PedidoItem, ProductoResumen } from '../api/tipos'
import { useCarga, useRetardo } from '../hooks'
import { escribirMesAnio, porcentaje, unir } from '../util/formato'
import { Aviso, Buscador, Campo, Etiqueta } from './basicos'

// ---------------------------------------------------------------- stock

const COLOR_STOCK = { AGOTADO: 'rojo', BAJO: 'ambar', NORMAL: 'verde' } as const
const TEXTO_STOCK: Record<EstadoStock, string> = { AGOTADO: 'Agotado', BAJO: 'Stock bajo', NORMAL: 'Hay stock' }

export function EtiquetaStock({ estado }: { estado: EstadoStock }) {
  return <Etiqueta color={COLOR_STOCK[estado]}>{TEXTO_STOCK[estado]}</Etiqueta>
}

/** "8 separadas para pedidos · 2 libres": lo separado es lo que esperan los pedidos pendientes. */
export function ResumenStock({ producto }: { producto: ProductoResumen }) {
  const { stockSeparado, stockLibre } = producto
  return (
    <>
      {stockSeparado} {stockSeparado === 1 ? 'separada' : 'separadas'} para pedidos ·{' '}
      <strong>
        {stockLibre} {stockLibre === 1 ? 'libre' : 'libres'}
      </strong>
    </>
  )
}

/** Situación de un ítem de pedido frente al stock. */
export function EtiquetaDisponibilidad({ item }: { item: PedidoItem }) {
  switch (item.disponibilidad) {
    case 'DISPONIBLE':
      return <Etiqueta color="verde">✓ Disponible</Etiqueta>
    case 'PARCIAL':
      return (
        <Etiqueta color="ambar">
          Hay {item.disponible} · faltan {item.faltante}
        </Etiqueta>
      )
    case 'SIN_STOCK':
      return <Etiqueta color="rojo">Sin stock · faltan {item.faltante}</Etiqueta>
    case 'NO_REGISTRADO':
      return <Etiqueta color="gris">No registrado</Etiqueta>
    default:
      return null
  }
}

// ---------------------------------------------------------------- categoría de precio

/**
 * Elige el porcentaje de un cliente o de un pedido. Muestra la letra en grande y el porcentaje
 * en pequeño: la letra es una etiqueta interna, el cliente nunca la ve.
 */
export function SelectorCategoria({
  categorias,
  valor,
  onCambio,
}: {
  categorias: CategoriaPrecio[]
  valor: number | null
  onCambio: (porcentaje: number | null) => void
}) {
  const esDeCategoria = valor !== null && categorias.some((c) => Number(c.porcentaje) === valor)
  const [otro, setOtro] = useState(valor !== null && !esDeCategoria)
  const [textoOtro, setTextoOtro] = useState(valor !== null && !esDeCategoria ? String(valor) : '')

  return (
    <div>
      <div className="grid grid-cols-4 gap-2">
        {categorias.map((c) => {
          const elegido = !otro && valor === Number(c.porcentaje)
          return (
            <button
              key={c.id}
              type="button"
              aria-pressed={elegido}
              onClick={() => {
                setOtro(false)
                onCambio(Number(c.porcentaje))
              }}
              className={`flex min-h-16 flex-col items-center justify-center rounded-xl border-2 ${
                elegido ? 'border-marca bg-marca text-white' : 'border-slate-300 bg-white text-slate-800'
              }`}
            >
              <span className="text-2xl font-bold leading-none">{c.letra}</span>
              <span className={`mt-1 text-xs ${elegido ? 'text-white/80' : 'text-slate-500'}`}>
                {porcentaje(Number(c.porcentaje))}
              </span>
            </button>
          )
        })}
        <button
          type="button"
          aria-pressed={otro}
          onClick={() => {
            setOtro(true)
            onCambio(textoOtro === '' ? null : Number(textoOtro))
          }}
          className={`flex min-h-16 flex-col items-center justify-center rounded-xl border-2 text-sm font-semibold ${
            otro ? 'border-marca bg-marca text-white' : 'border-slate-300 bg-white text-slate-800'
          }`}
        >
          Otro
        </button>
      </div>
      {otro && (
        <div className="mt-2">
          <Campo
            etiqueta="Porcentaje sobre el costo"
            inputMode="decimal"
            placeholder="Ej. 12.5"
            value={textoOtro}
            onChange={(e) => {
              const texto = e.target.value.replace(',', '.').replace(/[^\d.]/g, '')
              setTextoOtro(texto)
              onCambio(texto === '' || Number.isNaN(Number(texto)) ? null : Number(texto))
            }}
          />
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- buscador de productos

/** Busca productos mientras se escribe y deja elegir uno. Lo usan los pedidos y las entradas. */
export function BuscadorProductos({
  onElegir,
  placeholder = 'Buscar producto…',
  autoFocus,
}: {
  onElegir: (producto: ProductoResumen) => void
  placeholder?: string
  autoFocus?: boolean
}) {
  const [texto, setTexto] = useState('')
  const buscado = useRetardo(texto.trim())
  const { datos, cargando, error } = useCarga(
    () => (buscado === '' ? Promise.resolve(null) : productos.buscar(buscado, { tamano: 8 })),
    buscado,
  )

  return (
    <div>
      <Buscador valor={texto} onCambio={setTexto} placeholder={placeholder} autoFocus={autoFocus} />
      {error && (
        <div className="mt-2">
          <Aviso tipo="error">{error}</Aviso>
        </div>
      )}
      {texto.trim() !== '' && !cargando && datos && (
        <ul className="mt-2 divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white">
          {datos.contenido.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => {
                  setTexto('')
                  onElegir(p)
                }}
                className="flex w-full items-center gap-3 px-3 py-2.5 text-left active:bg-slate-100"
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{p.nombre}</span>
                  <span className="block truncate text-sm text-slate-500">{unir(' · ', p.presentacion, p.marca)}</span>
                </span>
                <span
                  className={`shrink-0 text-sm font-semibold ${p.stockLibre > 0 ? 'text-emerald-700' : 'text-red-700'}`}
                >
                  {p.stockLibre > 0 ? `${p.stockLibre} libres` : 'Sin stock'}
                </span>
              </button>
            </li>
          ))}
          {datos.contenido.length === 0 && (
            <li className="px-3 py-3 text-sm text-slate-500">No hay productos que coincidan con “{texto.trim()}”.</li>
          )}
        </ul>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- vencimiento

/** Campo de vencimiento: solo mes y año. La barra se pone sola al escribir. */
export function CampoMesAnio({
  valor,
  onCambio,
  error,
}: {
  valor: string
  onCambio: (valor: string) => void
  error?: string
}) {
  return (
    <Campo
      etiqueta="Vencimiento"
      inputMode="numeric"
      placeholder="MM/AAAA"
      autoComplete="off"
      value={valor}
      onChange={(e) => onCambio(escribirMesAnio(e.target.value))}
      error={error}
      ayuda="Vacío si no vence"
    />
  )
}
