import { useState } from 'react'
import { mensajeDe } from '../api/http'
import { pedidos } from '../api/recursos'
import type { CorreccionPedido, Pedido, PedidoItem, ProductoResumen } from '../api/tipos'
import { dinero, fechaCorta, leerDecimal, leerEntero, mesAnio, unir } from '../util/formato'
import { Aviso, Boton, Campo, Etiqueta, Tarjeta } from './basicos'
import { BuscadorProductos } from './dominio'

interface Linea {
  cantidad: string
  precio: string
  /** loteId → unidades que vuelven a ese lote (solo si el ítem salió de varios lotes) */
  lotes: Record<number, string>
}

interface Nuevo {
  clave: string
  productoId: number | null
  descripcion: string
  detalle: string
  cantidad: string
  precio: string
}

const precioTexto = (valor: number | null) => (valor === null ? '' : valor.toFixed(2))

/** Valida lo escrito y arma lo que se envía al servidor, con el total resultante. */
function analizar(pedido: Pedido, lineas: Record<number, Linea>, nuevos: Nuevo[]) {
  const problemas: string[] = []
  let total = 0
  const cambios = pedido.items.flatMap((item) => {
    const linea = lineas[item.id]
    const cantidad = leerEntero(linea.cantidad)
    const precio = linea.precio.trim() === '' ? null : leerDecimal(linea.precio)
    if (cantidad === null || cantidad < 0) {
      problemas.push(`Cantidad inválida en "${item.descripcion}"`)
      return []
    }
    if (linea.precio.trim() !== '' && precio === null) {
      problemas.push(`Precio inválido en "${item.descripcion}"`)
      return []
    }
    total += cantidad * (precio ?? item.precioUnitario ?? 0)
    const devueltas = item.cantidad - cantidad
    let lotes: { loteId: number; cantidad: number }[] | undefined
    if (devueltas > 0 && item.productoId !== null && item.lotes.length > 1) {
      lotes = item.lotes
        .map((l) => ({ loteId: l.loteId, cantidad: leerEntero(linea.lotes[l.loteId] ?? '') ?? 0 }))
        .filter((l) => l.cantidad > 0)
      const suma = lotes.reduce((s, l) => s + l.cantidad, 0)
      if (suma !== devueltas) {
        problemas.push(`"${item.descripcion}": indica a qué lote vuelven las ${devueltas} unidades (van ${suma})`)
      }
    }
    const cambioPrecio = precio !== null && precio !== item.precioUnitario
    if (cantidad === item.cantidad && !cambioPrecio) {
      return []
    }
    return [{ itemId: item.id, cantidad, precio: cambioPrecio ? precio : undefined, lotes }]
  })
  const itemsNuevos = nuevos.flatMap((n) => {
    const cantidad = leerEntero(n.cantidad)
    const precio = n.precio.trim() === '' ? null : leerDecimal(n.precio)
    if (cantidad === null || cantidad < 1) {
      problemas.push(`Cantidad inválida en "${n.descripcion}"`)
      return []
    }
    if (precio === null) {
      problemas.push(`Falta el precio de "${n.descripcion}"`)
      return []
    }
    total += cantidad * precio
    return [
      n.productoId === null
        ? { descripcion: n.descripcion, cantidad, precio }
        : { productoId: n.productoId, cantidad, precio },
    ]
  })
  return { problemas, total, cambios, itemsNuevos }
}

/**
 * Corregir un pedido ya despachado: cambiar cantidades (0 = quitar), precios y agregar ítems, con un motivo.
 * Lo que se devuelve vuelve al lote del que salió; si salió de varios lotes, se elige a cuál.
 */
export function CorregirPedido({
  pedido,
  onCancelar,
  onGuardado,
}: {
  pedido: Pedido
  onCancelar: () => void
  onGuardado: (pedido: Pedido) => void
}) {
  const [lineas, setLineas] = useState<Record<number, Linea>>(() =>
    Object.fromEntries(
      pedido.items.map((i) => [i.id, { cantidad: String(i.cantidad), precio: precioTexto(i.precioUnitario), lotes: {} }]),
    ),
  )
  const [nuevos, setNuevos] = useState<Nuevo[]>([])
  const [manual, setManual] = useState(false)
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  const cambiar = (id: number, cambio: Partial<Linea>) =>
    setLineas((actual) => ({ ...actual, [id]: { ...actual[id], ...cambio } }))

  const { problemas, total, cambios, itemsNuevos } = analizar(pedido, lineas, nuevos)
  const sinCambios = cambios.length === 0 && itemsNuevos.length === 0
  const listo = problemas.length === 0 && !sinCambios && motivo.trim() !== ''

  async function guardar() {
    if (!listo) {
      return
    }
    if (!window.confirm(`¿Guardar la corrección? El total pasa de ${dinero(pedido.total)} a ${dinero(total)}.`)) {
      return
    }
    setOcupado(true)
    setError(null)
    try {
      onGuardado(await pedidos.corregir(pedido.id, { motivo, cambios, nuevos: itemsNuevos }))
    } catch (e) {
      setError(mensajeDe(e))
      setOcupado(false)
    }
  }

  function agregarProducto(p: ProductoResumen) {
    if (pedido.items.some((i) => i.productoId === p.id) || nuevos.some((n) => n.productoId === p.id)) {
      setError(`"${p.nombre}" ya está en el pedido: cambia su cantidad arriba`)
      return
    }
    setError(null)
    // Precio sugerido: costo + porcentaje del pedido (el servidor hace el mismo cálculo)
    const sugerido = p.costo === null ? '' : (Math.round(p.costo * (1 + pedido.porcentaje / 100) * 100) / 100).toFixed(2)
    setNuevos((actual) => [
      ...actual,
      {
        clave: `p${p.id}`,
        productoId: p.id,
        descripcion: p.nombre,
        detalle: unir(' · ', p.presentacion, p.marca),
        cantidad: '1',
        precio: sugerido,
      },
    ])
  }

  return (
    <div className="space-y-3">
      <Aviso>
        Cambia lo que haga falta y guarda con un motivo. Lo que bajes o quites <strong>vuelve al inventario</strong>{' '}
        (al lote del que salió); lo que subas o agregues <strong>sale del inventario</strong>. La nota mantiene su
        número y sale con las cantidades corregidas.
      </Aviso>
      {error && <Aviso tipo="error">{error}</Aviso>}

      <div className="space-y-2">
        {pedido.items.map((item) => (
          <LineaCorreccion key={item.id} item={item} linea={lineas[item.id]} onCambio={(c) => cambiar(item.id, c)} />
        ))}
        {nuevos.map((n) => (
          <Tarjeta key={n.clave} className="border-marca/40">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <Etiqueta color="azul">Nuevo</Etiqueta>
                <p className="mt-1 font-semibold leading-snug">{n.descripcion}</p>
                {n.detalle && <p className="text-sm text-slate-500">{n.detalle}</p>}
              </div>
              <Boton
                variante="texto"
                className="min-h-10 px-2 text-sm text-red-700"
                onClick={() => setNuevos((actual) => actual.filter((x) => x.clave !== n.clave))}
              >
                Quitar
              </Boton>
            </div>
            <div className="mt-2 grid grid-cols-2 gap-3">
              <Campo
                etiqueta="Cantidad"
                inputMode="numeric"
                value={n.cantidad}
                onChange={(e) =>
                  setNuevos((a) => a.map((x) => (x.clave === n.clave ? { ...x, cantidad: e.target.value.replace(/\D/g, '') } : x)))
                }
              />
              <Campo
                etiqueta="Precio (con IGV)"
                inputMode="decimal"
                value={n.precio}
                onChange={(e) =>
                  setNuevos((a) =>
                    a.map((x) => (x.clave === n.clave ? { ...x, precio: e.target.value.replace(/[^\d.,]/g, '') } : x)),
                  )
                }
              />
            </div>
            {n.productoId !== null && <p className="mt-2 text-sm text-slate-600">Sale del inventario al guardar.</p>}
          </Tarjeta>
        ))}
      </div>

      <Tarjeta>
        <BuscadorProductos placeholder="Agregar un producto al pedido…" onElegir={agregarProducto} />
        <Boton variante="texto" className="mt-1 min-h-10 px-1 text-sm" onClick={() => setManual(!manual)}>
          {manual ? 'Cancelar ítem a mano' : '+ Agregar un ítem que no está en la lista'}
        </Boton>
        {manual && (
          <NuevoAMano
            onAgregar={(descripcion) => {
              setNuevos((a) => [
                ...a,
                { clave: `m${Date.now()}`, productoId: null, descripcion, detalle: 'Escrito a mano', cantidad: '1', precio: '' },
              ])
              setManual(false)
            }}
          />
        )}
      </Tarjeta>

      <Tarjeta className="space-y-3">
        <div className="flex items-baseline justify-between gap-3">
          <span className="text-slate-600">Total</span>
          <span className="text-right">
            <span className="block text-sm text-slate-500">antes {dinero(pedido.total)}</span>
            <span className="block text-2xl font-bold">{dinero(Math.round(total * 100) / 100)}</span>
          </span>
        </div>
        <Campo
          etiqueta="Motivo de la corrección"
          placeholder="Devolvieron 2 cajas: se entregó de más"
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          maxLength={300}
          ayuda="Queda en el historial del pedido. Solo lo ven ustedes."
        />
        {problemas.length > 0 && (
          <ul className="list-disc space-y-1 pl-5 text-sm text-red-700">
            {problemas.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        )}
        <div className="flex gap-2">
          <Boton variante="secundario" className="flex-1" disabled={ocupado} onClick={onCancelar}>
            Cancelar
          </Boton>
          <Boton className="flex-1" disabled={ocupado || !listo} onClick={() => void guardar()}>
            {ocupado ? 'Guardando…' : 'Guardar corrección'}
          </Boton>
        </div>
        {sinCambios && <p className="text-center text-sm text-slate-500">Todavía no hay cambios.</p>}
      </Tarjeta>
    </div>
  )
}

function LineaCorreccion({
  item,
  linea,
  onCambio,
}: {
  item: PedidoItem
  linea: Linea
  onCambio: (cambio: Partial<Linea>) => void
}) {
  const cantidad = leerEntero(linea.cantidad)
  const devueltas = cantidad === null ? 0 : item.cantidad - cantidad
  const campo =
    'h-11 rounded-lg border border-slate-300 bg-white text-center font-semibold focus:border-marca focus:outline-none focus:ring-2 focus:ring-marca/20'

  return (
    <Tarjeta className={cantidad === 0 && item.cantidad > 0 ? 'opacity-80' : ''}>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold leading-snug">{item.descripcion}</p>
          <p className="text-sm text-slate-500">
            {unir(' · ', item.presentacion, item.marca, `entregado: ${item.cantidad}`)}
          </p>
        </div>
        {item.cantidad > 0 && cantidad !== 0 && (
          <Boton variante="texto" className="min-h-10 px-2 text-sm text-red-700" onClick={() => onCambio({ cantidad: '0' })}>
            Quitar
          </Boton>
        )}
      </div>

      <div className="mt-2 flex items-center gap-2">
        <div className="flex shrink-0 items-center">
          <button
            type="button"
            aria-label={`Una menos de ${item.descripcion}`}
            disabled={cantidad === null || cantidad <= 0}
            onClick={() => cantidad !== null && onCambio({ cantidad: String(cantidad - 1) })}
            className="h-11 w-9 rounded-l-lg border border-r-0 border-slate-300 bg-slate-50 text-xl font-semibold disabled:text-slate-300"
          >
            −
          </button>
          <input
            aria-label={`Cantidad de ${item.descripcion}`}
            inputMode="numeric"
            value={linea.cantidad}
            onChange={(e) => onCambio({ cantidad: e.target.value.replace(/\D/g, '') })}
            onFocus={(e) => e.currentTarget.select()}
            className={`${campo} w-14 rounded-none`}
          />
          <button
            type="button"
            aria-label={`Una más de ${item.descripcion}`}
            onClick={() => onCambio({ cantidad: String((cantidad ?? 0) + 1) })}
            className="h-11 w-9 rounded-r-lg border border-l-0 border-slate-300 bg-slate-50 text-xl font-semibold"
          >
            +
          </button>
        </div>
        <span className="text-slate-400">×</span>
        <input
          aria-label={`Precio de ${item.descripcion}`}
          inputMode="decimal"
          value={linea.precio}
          onChange={(e) => onCambio({ precio: e.target.value.replace(/[^\d.,]/g, '') })}
          onFocus={(e) => e.currentTarget.select()}
          className={`${campo} w-20 shrink-0`}
        />
      </div>

      {item.productoId !== null && devueltas > 0 && item.lotes.length === 1 && (
        <p className="mt-2 text-sm text-emerald-800">
          Vuelven {devueltas} al lote {item.lotes[0].numeroLote ?? 'sin número'}.
        </p>
      )}
      {item.productoId !== null && devueltas > 0 && item.lotes.length > 1 && (
        <div className="mt-3 rounded-xl bg-amber-50 p-3">
          <p className="text-sm font-medium text-amber-900">¿A qué lote vuelven las {devueltas} unidades?</p>
          <div className="mt-2 space-y-2">
            {item.lotes.map((l) => (
              <label key={l.loteId} className="flex items-center justify-between gap-3 text-sm">
                <span>
                  Lote {l.numeroLote ?? 'sin número'}
                  {l.fechaVencimiento ? ` · vence ${mesAnio(l.fechaVencimiento)}` : ''} · se entregaron {l.cantidad}
                </span>
                <input
                  inputMode="numeric"
                  aria-label={`Unidades que vuelven al lote ${l.numeroLote ?? 'sin número'}`}
                  value={linea.lotes[l.loteId] ?? ''}
                  onChange={(e) => onCambio({ lotes: { ...linea.lotes, [l.loteId]: e.target.value.replace(/\D/g, '') } })}
                  className={`${campo} w-16 shrink-0`}
                />
              </label>
            ))}
          </div>
        </div>
      )}
      {item.productoId !== null && devueltas < 0 && (
        <p className="mt-2 text-sm text-slate-600">Salen {-devueltas} más del inventario (primero lo que vence antes).</p>
      )}
    </Tarjeta>
  )
}

function NuevoAMano({ onAgregar }: { onAgregar: (descripcion: string) => void }) {
  const [descripcion, setDescripcion] = useState('')
  return (
    <div className="mt-2 flex items-end gap-2 border-t border-slate-100 pt-3">
      <div className="flex-1">
        <Campo etiqueta="Descripción" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      </div>
      <Boton variante="secundario" disabled={descripcion.trim() === ''} onClick={() => onAgregar(descripcion.trim())}>
        Agregar
      </Boton>
    </div>
  )
}

/** Historial de correcciones de un pedido despachado (solo para uso interno). */
export function HistorialCorrecciones({ correcciones }: { correcciones: CorreccionPedido[] }) {
  return (
    <Tarjeta>
      <h2 className="font-semibold">Correcciones</h2>
      <ul className="mt-2 divide-y divide-slate-100">
        {correcciones.map((c) => (
          <li key={c.id} className="py-2 text-sm">
            <p className="font-medium">
              {fechaCorta(c.fecha)} · {c.motivo}
            </p>
            <ul className="mt-1 list-disc space-y-0.5 pl-5 text-slate-600">
              {c.detalle.map((d) => (
                <li key={d}>{d}</li>
              ))}
            </ul>
            <p className="mt-1 text-slate-500">
              Total: {dinero(c.totalAnterior)} → {dinero(c.totalNuevo)}
            </p>
          </li>
        ))}
      </ul>
    </Tarjeta>
  )
}
