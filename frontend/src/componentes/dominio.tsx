import { useState } from 'react'
import { mensajeDe } from '../api/http'
import { pedidos, productos } from '../api/recursos'
import type {
  CategoriaPrecio,
  ClienteResumen,
  CostoPedido,
  DocumentoPedido,
  EstadoStock,
  PedidoItem,
  ProductoResumen,
} from '../api/tipos'
import { useCarga, useRetardo } from '../hooks'
import { dinero, escribirMesAnio, porcentaje, unir } from '../util/formato'
import { Aviso, Buscador, Campo, Etiqueta } from './basicos'

// ---------------------------------------------------------------- pedidos

const DOCUMENTOS: { valor: DocumentoPedido; titulo: string; detalle: string }[] = [
  { valor: 'NOTA', titulo: 'Nota de pedido', detalle: 'Con el nombre del negocio, el cliente, la fecha y el número.' },
  { valor: 'SOLO_TABLA', titulo: 'Solo tabla', detalle: 'Solo los productos y el total: sin nombres, sin fecha y sin número.' },
]

/** Con qué documento se entrega el pedido. */
export function SelectorDocumento({
  valor,
  onCambio,
  deshabilitado,
}: {
  valor: DocumentoPedido
  onCambio: (valor: DocumentoPedido) => void
  deshabilitado?: boolean
}) {
  return (
    <div className="space-y-2" role="radiogroup" aria-label="Documento">
      {DOCUMENTOS.map((d) => (
        <label
          key={d.valor}
          className={`flex cursor-pointer items-start gap-3 rounded-xl border-2 p-3 ${
            valor === d.valor ? 'border-marca bg-marca-claro' : 'border-slate-200'
          }`}
        >
          <input
            type="radio"
            name="documento"
            className="mt-1 h-5 w-5 accent-marca"
            checked={valor === d.valor}
            disabled={deshabilitado}
            onChange={() => onCambio(d.valor)}
          />
          <span className="min-w-0">
            <span className="block font-medium">{d.titulo}</span>
            <span className="block text-sm text-slate-600">{d.detalle}</span>
          </span>
        </label>
      ))}
    </div>
  )
}

// ---------------------------------------------------------------- clientes

/**
 * Nombre y RUC de un cliente en una lista de resultados. Si se le encontró por un local
 * (nombre de la botica) o por un contacto, lo indica debajo.
 */
export function DatosCliente({ cliente }: { cliente: ClienteResumen }) {
  return (
    <span className="min-w-0 flex-1">
      <span className="block font-semibold leading-snug break-words">{cliente.nombre}</span>
      {cliente.rucDni && <span className="block text-sm text-slate-500">{cliente.rucDni}</span>}
      {cliente.localesEncontrados?.map((l, i) => (
        <span key={i} className="block text-sm break-words text-marca">
          {unir(' · ', l.nombre && `Local: ${l.nombre}`, l.contacto && `Contacto: ${l.contacto}`)}
        </span>
      ))}
    </span>
  )
}

// ---------------------------------------------------------------- stock

const COLOR_STOCK = { AGOTADO: 'rojo', BAJO: 'ambar', NORMAL: 'verde' } as const
const TEXTO_STOCK: Record<EstadoStock, string> = { AGOTADO: 'Agotado', BAJO: 'Stock bajo', NORMAL: 'Hay stock' }

/**
 * Costo, ganancia y margen de un pedido ("gana S/ 120.00 · 12 %"), en letra más pequeña y en otro color que el
 * total (uso interno: nunca va en la nota). Si se vende por debajo del costo dice "pierde", en rojo.
 */
export function CostoYGanancia({ costo, className = '' }: { costo: CostoPedido | null; className?: string }) {
  if (!costo || (costo.costo === 0 && costo.ganancia === 0 && costo.itemsSinCosto === 0)) {
    return null
  }
  const pierde = costo.ganancia < 0
  const todoSinCosto = costo.costo === 0 && costo.itemsSinCosto > 0
  return (
    <span className={`block text-right text-sm leading-tight ${className}`}>
      {!todoSinCosto && (
        <>
          <span className="block whitespace-nowrap text-slate-500">costo {dinero(costo.costo)}</span>
          <span className={`block whitespace-nowrap font-medium ${pierde ? 'text-red-700' : 'text-emerald-700'}`}>
            {pierde ? 'pierde' : 'gana'} {dinero(Math.abs(costo.ganancia))}
            {costo.margen !== null && ` · ${Number(costo.margen)} %`}
          </span>
        </>
      )}
      {costo.itemsSinCosto > 0 && (
        <span className="block whitespace-nowrap text-xs text-amber-700">
          {costo.itemsSinCosto} {costo.itemsSinCosto === 1 ? 'ítem' : 'ítems'} sin costo
        </span>
      )}
    </span>
  )
}

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
                className="flex w-full items-start gap-3 px-3 py-2.5 text-left active:bg-slate-100"
              >
                <span className="min-w-0 flex-1">
                  <span className="block font-medium leading-snug break-words">{p.nombre}</span>
                  <span className="block text-sm break-words text-slate-500">{unir(' · ', p.presentacion, p.marca)}</span>
                </span>
                <span
                  className={`shrink-0 pt-0.5 text-sm font-semibold ${p.stockLibre > 0 ? 'text-emerald-700' : 'text-red-700'}`}
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

// ---------------------------------------------------------------- por comprar

/**
 * "🛒 Agregar a Por comprar" de un toque, sin cantidad (se pone después en Por comprar si hace falta). Si el
 * producto ya está en Por comprar, se ve "✓ En Por comprar" (o el texto de `listo`). "chico" es una pastilla para
 * listas largas (Inventario); "grande" ocupa todo el ancho (Alertas). No abre el producto aunque esté dentro de una
 * fila que sí lo abre.
 */
export function AgregarAPorComprar({
  productoId,
  enPorComprar,
  onAgregado,
  tamano = 'grande',
  listo = '✓ En Por comprar',
}: {
  productoId: number
  enPorComprar: boolean
  onAgregado: () => void
  tamano?: 'grande' | 'chico'
  listo?: string
}) {
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const chico = tamano === 'chico'

  async function agregar() {
    setOcupado(true)
    setError(null)
    try {
      await pedidos.agregarAPorComprarDeUnToque(productoId)
      onAgregado()
    } catch (e) {
      setError(mensajeDe(e))
    } finally {
      setOcupado(false)
    }
  }

  const estiloChico = 'inline-flex min-h-9 items-center whitespace-nowrap rounded-full border px-3 text-sm font-semibold'
  return (
    <span className={chico ? 'inline-flex flex-col items-end' : 'block'}>
      {enPorComprar ? (
        <span
          role="status"
          className={
            chico
              ? `${estiloChico} border-emerald-200 bg-emerald-50 text-emerald-800`
              : 'flex min-h-12 items-center justify-center rounded-xl border border-emerald-200 bg-emerald-50 px-4 font-semibold text-emerald-800'
          }
        >
          {listo}
        </span>
      ) : (
        <button
          type="button"
          disabled={ocupado}
          onClick={(e) => {
            e.stopPropagation()
            void agregar()
          }}
          className={
            chico
              ? `${estiloChico} border-slate-300 bg-white text-marca active:bg-slate-100 disabled:text-slate-400`
              : 'inline-flex min-h-12 w-full items-center justify-center rounded-xl border border-slate-300 bg-white px-4 text-base font-semibold text-marca active:bg-slate-100 disabled:text-slate-400'
          }
        >
          {ocupado ? 'Agregando…' : chico ? '🛒 Agregar' : '🛒 Agregar a Por comprar'}
        </button>
      )}
      {error && <span className="mt-1 block text-sm text-red-700">{error}</span>}
    </span>
  )
}

// ---------------------------------------------------------------- costo debajo del precio

/**
 * "costo S/ 16.80", chico y en gris, debajo del precio de un ítem (uso interno: nunca va en la nota). Sirve para no
 * vender por debajo del costo sin darse cuenta al cambiar el precio a mano.
 */
export function TextoCosto({ costo }: { costo: number | null }) {
  if (costo === null) {
    return null
  }
  return <p className="mt-1 whitespace-nowrap text-xs text-slate-500">costo {dinero(costo)}</p>
}
