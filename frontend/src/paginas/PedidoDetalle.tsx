import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { categoriasPrecio, pedidos } from '../api/recursos'
import type { ItemCambio } from '../api/recursos'
import type { CategoriaPrecio, Pedido, PedidoItem } from '../api/tipos'
import { Aviso, Boton, Campo, Cargando, Etiqueta, Tarjeta } from '../componentes/basicos'
import { BuscadorProductos, EtiquetaDisponibilidad, SelectorCategoria } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { compartirODescargar } from '../util/archivos'
import { dinero, fechaCorta, leerDecimal, leerEntero, mesAnio, porcentaje as textoPorcentaje, unir } from '../util/formato'

export default function PedidoDetalle() {
  const id = Number(useParams().id)
  const [pedido, setPedido] = useState<Pedido>()
  const [categorias, setCategorias] = useState<CategoriaPrecio[]>([])
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [panel, setPanel] = useState<'categoria' | 'manual' | null>(null)

  useEffect(() => {
    let vigente = true
    Promise.all([pedidos.obtener(id), categoriasPrecio.listar()])
      .then(([p, c]) => {
        if (vigente) {
          setPedido(p)
          setCategorias(c)
        }
      })
      .catch((e: unknown) => vigente && setError(mensajeDe(e)))
    return () => {
      vigente = false
    }
  }, [id])

  /** Ejecuta un cambio y reemplaza el pedido con la respuesta (trae el total y los faltantes al día). */
  async function aplicar(accion: () => Promise<Pedido>): Promise<boolean> {
    setOcupado(true)
    setError(null)
    try {
      setPedido(await accion())
      return true
    } catch (e) {
      setError(mensajeDe(e))
      return false
    } finally {
      setOcupado(false)
    }
  }

  async function nota(formato: 'pdf' | 'xlsx') {
    if (!pedido) {
      return
    }
    setOcupado(true)
    setError(null)
    try {
      const nombre = pedido.numero ? `nota-pedido-${pedido.numero}` : `pedido-borrador-${pedido.id}`
      await compartirODescargar(await pedidos.nota(pedido.id, formato), `${nombre}.${formato}`)
    } catch (e) {
      setError(mensajeDe(e))
    } finally {
      setOcupado(false)
    }
  }

  if (!pedido) {
    return (
      <Pantalla titulo="Pedido" volver="/pedidos">
        {error ? <Aviso tipo="error">{error}</Aviso> : <Cargando />}
      </Pantalla>
    )
  }

  const borrador = pedido.estado === 'BORRADOR'
  const titulo = pedido.numero ? `Nota ${pedido.numero}` : borrador ? 'Pedido' : 'Pedido anulado'

  return (
    <Pantalla
      titulo={titulo}
      volver="/pedidos"
      pie={<Pie pedido={pedido} ocupado={ocupado} aplicar={aplicar} nota={nota} />}
    >
      <div className="space-y-3">
        {error && <Aviso tipo="error">{error}</Aviso>}
        {pedido.estado === 'DESPACHADO' && (
          <Aviso tipo="exito">
            Despachado el {fechaCorta(pedido.despachadoEn)}. Ya se descontó del inventario.
          </Aviso>
        )}
        {pedido.estado === 'ANULADO' && <Aviso tipo="alerta">Este pedido fue anulado.</Aviso>}

        {/* ---------------- cliente y categoría ---------------- */}
        <Tarjeta>
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h2 className="text-lg font-semibold leading-snug">{pedido.cliente}</h2>
              <p className="text-sm text-slate-500">{unir(' · ', pedido.local, pedido.direccion)}</p>
              <p className="text-sm text-slate-500">{unir(' · ', pedido.contacto, pedido.telefono)}</p>
            </div>
            <button
              type="button"
              disabled={!borrador}
              onClick={() => setPanel(panel === 'categoria' ? null : 'categoria')}
              aria-label="Categoría de precio"
              className="flex shrink-0 flex-col items-center rounded-xl bg-marca-claro px-3 py-1.5 text-marca disabled:opacity-70"
            >
              <span className="text-xl font-bold leading-tight">{pedido.categoria ?? '%'}</span>
              <span className="text-[11px] leading-tight">{textoPorcentaje(pedido.porcentaje)}</span>
            </button>
          </div>
          {panel === 'categoria' && (
            <CambiarCategoria
              pedido={pedido}
              categorias={categorias}
              ocupado={ocupado}
              onGuardar={async (porcentaje, guardarEnCliente) => {
                const ok = await aplicar(() =>
                  pedidos.actualizar(pedido.id, {
                    porcentaje,
                    guardarPorcentajeEnCliente: guardarEnCliente,
                    nota: pedido.nota,
                  }),
                )
                if (ok) {
                  setPanel(null)
                }
              }}
            />
          )}
        </Tarjeta>

        {/* ---------------- agregar ítems ---------------- */}
        {borrador && (
          <Tarjeta>
            <BuscadorProductos
              placeholder="Agregar producto: jer 5ml, guantes m…"
              autoFocus={pedido.items.length === 0}
              onElegir={(p) => aplicar(() => pedidos.agregarItem(pedido.id, { productoId: p.id, cantidad: 1 }))}
            />
            <Boton
              variante="texto"
              className="mt-1 min-h-10 px-1 text-sm"
              onClick={() => setPanel(panel === 'manual' ? null : 'manual')}
            >
              {panel === 'manual' ? 'Cancelar ítem a mano' : '+ Agregar un ítem que no está en la lista'}
            </Boton>
            {panel === 'manual' && (
              <ItemAMano
                ocupado={ocupado}
                onAgregar={async (item) => {
                  const ok = await aplicar(() => pedidos.agregarItem(pedido.id, item))
                  if (ok) {
                    setPanel(null)
                  }
                }}
              />
            )}
          </Tarjeta>
        )}

        {/* ---------------- ítems ---------------- */}
        {pedido.items.length === 0 ? (
          <p className="py-6 text-center text-slate-500">
            {borrador ? 'Busca un producto para empezar el pedido.' : 'Este pedido no tiene ítems.'}
          </p>
        ) : (
          <ul className="space-y-2">
            {pedido.items.map((item, i) => (
              <li key={item.id}>
                <FilaItem
                  // Si el servidor devuelve otra cantidad o precio (p. ej. al recalcular), la fila se reinicia
                  key={`${item.cantidad}|${item.precioUnitario}`}
                  numero={i + 1}
                  item={item}
                  editable={borrador}
                  ocupado={ocupado}
                  onCambiar={(cambio) => aplicar(() => pedidos.cambiarItem(pedido.id, item.id, cambio))}
                  onQuitar={() => aplicar(() => pedidos.quitarItem(pedido.id, item.id))}
                />
              </li>
            ))}
          </ul>
        )}
      </div>
    </Pantalla>
  )
}

// ------------------------------------------------------------------ pie: total y acciones

function Pie({
  pedido,
  ocupado,
  aplicar,
  nota,
}: {
  pedido: Pedido
  ocupado: boolean
  aplicar: (accion: () => Promise<Pedido>) => Promise<boolean>
  nota: (formato: 'pdf' | 'xlsx') => void
}) {
  const borrador = pedido.estado === 'BORRADOR'
  const avisos = [
    pedido.itemsConFaltante > 0 ? `faltan unidades en ${pedido.itemsConFaltante}` : null,
    pedido.itemsSinPrecio > 0 ? `${pedido.itemsSinPrecio} sin precio` : null,
  ].filter(Boolean)

  return (
    <div>
      <div className="flex items-end justify-between gap-3">
        <div className="min-w-0 text-sm">
          <div className="text-slate-500">
            {pedido.items.length} {pedido.items.length === 1 ? 'ítem' : 'ítems'}
          </div>
          {borrador && avisos.length > 0 && <div className="font-medium text-amber-700">{avisos.join(' · ')}</div>}
        </div>
        <div className="text-right">
          <div className="text-xs uppercase tracking-wide text-slate-500">Total</div>
          <div className="text-2xl font-bold leading-none">{dinero(pedido.total)}</div>
        </div>
      </div>

      {pedido.estado !== 'ANULADO' && (
        <div className="mt-3 flex gap-2">
          {borrador && (
            <Boton
              className="flex-1"
              disabled={ocupado || !pedido.listoParaDespachar}
              onClick={() => {
                if (window.confirm('¿Despachar el pedido? Se descontará del inventario y se le asignará el número de nota.')) {
                  void aplicar(() => pedidos.despachar(pedido.id))
                }
              }}
            >
              Despachar
            </Boton>
          )}
          <Boton variante="secundario" className={borrador ? '' : 'flex-1'} disabled={ocupado} onClick={() => nota('pdf')}>
            PDF
          </Boton>
          <Boton variante="secundario" className={borrador ? '' : 'flex-1'} disabled={ocupado} onClick={() => nota('xlsx')}>
            Excel
          </Boton>
          {borrador && (
            <Boton
              variante="peligro"
              disabled={ocupado}
              aria-label="Anular pedido"
              onClick={() => {
                if (window.confirm('¿Anular este pedido? Se liberará el stock que tenía separado.')) {
                  void aplicar(() => pedidos.anular(pedido.id))
                }
              }}
            >
              Anular
            </Boton>
          )}
        </div>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ una línea del pedido

function FilaItem({
  numero,
  item,
  editable,
  ocupado,
  onCambiar,
  onQuitar,
}: {
  numero: number
  item: PedidoItem
  editable: boolean
  ocupado: boolean
  onCambiar: (cambio: ItemCambio) => void
  onQuitar: () => void
}) {
  const [cantidad, setCantidad] = useState(String(item.cantidad))
  const [precio, setPrecio] = useState(item.precioUnitario === null ? '' : item.precioUnitario.toFixed(2))


  function confirmarCantidad() {
    const nueva = leerEntero(cantidad)
    if (nueva === null || nueva < 1) {
      setCantidad(String(item.cantidad))
    } else if (nueva !== item.cantidad) {
      onCambiar({ cantidad: nueva })
    }
  }

  function confirmarPrecio() {
    const nuevo = leerDecimal(precio)
    if (nuevo === null) {
      setPrecio(item.precioUnitario === null ? '' : item.precioUnitario.toFixed(2))
    } else if (nuevo !== item.precioUnitario) {
      onCambiar({ cantidad: item.cantidad, precio: nuevo })
    }
  }

  const campo =
    'h-11 rounded-lg border border-slate-300 bg-white text-center font-semibold focus:border-marca focus:outline-none focus:ring-2 focus:ring-marca/20'

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-3">
      <div className="flex items-start gap-2">
        <span className="mt-0.5 w-5 shrink-0 text-sm text-slate-400">{numero}</span>
        <div className="min-w-0 flex-1">
          <p className="font-semibold leading-snug">{item.descripcion}</p>
          <p className="text-sm text-slate-500">{unir(' · ', item.presentacion, item.marca)}</p>
        </div>
        {editable && (
          <button
            type="button"
            aria-label={`Quitar ${item.descripcion}`}
            disabled={ocupado}
            onClick={onQuitar}
            className="-mr-1 -mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-xl text-slate-400 active:bg-slate-100"
          >
            ×
          </button>
        )}
      </div>

      <div className="mt-2 pl-7">
        <EtiquetaDisponibilidad item={item} />
      </div>

      {editable ? (
        <div className="mt-3 flex items-center gap-2">
          <div className="flex shrink-0 items-center">
            <button
              type="button"
              aria-label="Una menos"
              disabled={ocupado || item.cantidad <= 1}
              onClick={() => onCambiar({ cantidad: item.cantidad - 1 })}
              className="h-11 w-9 rounded-l-lg border border-r-0 border-slate-300 bg-slate-50 text-xl font-semibold disabled:text-slate-300"
            >
              −
            </button>
            <input
              aria-label="Cantidad"
              inputMode="numeric"
              value={cantidad}
              onChange={(e) => setCantidad(e.target.value.replace(/\D/g, ''))}
              onBlur={confirmarCantidad}
              onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
              onFocus={(e) => e.currentTarget.select()}
              className={`${campo} w-12 rounded-none`}
            />
            <button
              type="button"
              aria-label="Una más"
              disabled={ocupado}
              onClick={() => onCambiar({ cantidad: item.cantidad + 1 })}
              className="h-11 w-9 rounded-r-lg border border-l-0 border-slate-300 bg-slate-50 text-xl font-semibold"
            >
              +
            </button>
          </div>
          <span className="text-slate-400">×</span>
          <input
            aria-label="Precio unitario"
            inputMode="decimal"
            placeholder="Precio"
            value={precio}
            onChange={(e) => setPrecio(e.target.value.replace(/[^\d.,]/g, ''))}
            onBlur={confirmarPrecio}
            onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
            onFocus={(e) => e.currentTarget.select()}
            className={`${campo} w-20 shrink-0 ${item.precioUnitario === null ? 'border-amber-400 bg-amber-50' : ''}`}
          />
          <span className="ml-auto whitespace-nowrap text-lg font-bold">{dinero(item.total)}</span>
        </div>
      ) : (
        <div className="mt-2 flex items-center justify-between pl-7 text-sm">
          <span className="text-slate-600">
            {item.cantidad} × {dinero(item.precioUnitario)}
          </span>
          <span className="whitespace-nowrap text-lg font-bold">{dinero(item.total)}</span>
        </div>
      )}

      {editable && item.precioManual && item.productoId !== null && (
        <button
          type="button"
          disabled={ocupado}
          onClick={() => onCambiar({ cantidad: item.cantidad, precioAutomatico: true })}
          className="ml-7 mt-2 text-sm font-medium text-marca"
        >
          Precio escrito a mano · volver al calculado
        </button>
      )}

      {item.lotes.length > 0 && (
        <ul className="ml-7 mt-2 flex flex-wrap gap-1.5">
          {item.lotes.map((l, i) => (
            <li key={i}>
              <Etiqueta color="gris">
                {l.cantidad} · lote {l.numeroLote ?? 's/n'}
                {l.fechaVencimiento ? ` · vence ${mesAnio(l.fechaVencimiento)}` : ''}
              </Etiqueta>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ paneles

function CambiarCategoria({
  pedido,
  categorias,
  ocupado,
  onGuardar,
}: {
  pedido: Pedido
  categorias: CategoriaPrecio[]
  ocupado: boolean
  onGuardar: (porcentaje: number, guardarEnCliente: boolean) => void
}) {
  const [porcentaje, setPorcentaje] = useState<number | null>(pedido.porcentaje)
  const [guardarEnCliente, setGuardarEnCliente] = useState(false)

  return (
    <div className="mt-4 border-t border-slate-100 pt-4">
      <p className="mb-2 text-sm text-slate-600">
        Al cambiar la categoría se recalculan los precios, salvo los que escribiste a mano.
      </p>
      <SelectorCategoria categorias={categorias} valor={porcentaje} onCambio={setPorcentaje} />
      <label className="mt-3 flex items-center gap-3 text-sm">
        <input
          type="checkbox"
          className="h-5 w-5 accent-marca"
          checked={guardarEnCliente}
          onChange={(e) => setGuardarEnCliente(e.target.checked)}
        />
        Usarla también en los próximos pedidos de este cliente
      </label>
      <Boton
        ancho
        className="mt-3"
        disabled={ocupado || porcentaje === null}
        onClick={() => porcentaje !== null && onGuardar(porcentaje, guardarEnCliente)}
      >
        Aplicar
      </Boton>
    </div>
  )
}

function ItemAMano({
  ocupado,
  onAgregar,
}: {
  ocupado: boolean
  onAgregar: (item: { descripcion: string; presentacion: string; marca: string; cantidad: number; precio: number | null }) => void
}) {
  const [descripcion, setDescripcion] = useState('')
  const [presentacion, setPresentacion] = useState('')
  const [marca, setMarca] = useState('')
  const [cantidad, setCantidad] = useState('1')
  const [precio, setPrecio] = useState('')

  const cantidadValida = leerEntero(cantidad)
  const listo = descripcion.trim() !== '' && cantidadValida !== null && cantidadValida > 0

  return (
    <form
      className="mt-2 space-y-3 border-t border-slate-100 pt-3"
      onSubmit={(e) => {
        e.preventDefault()
        if (listo) {
          onAgregar({ descripcion, presentacion, marca, cantidad: cantidadValida, precio: leerDecimal(precio) })
        }
      }}
    >
      <p className="text-sm text-slate-600">
        Para algo que aún no está registrado. Saldrá en la nota, pero no descuenta inventario.
      </p>
      <Campo etiqueta="Descripción" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} autoFocus />
      <div className="grid grid-cols-2 gap-3">
        <Campo
          etiqueta="Presentación"
          placeholder="Caja x 100"
          value={presentacion}
          onChange={(e) => setPresentacion(e.target.value)}
        />
        <Campo etiqueta="Laboratorio" value={marca} onChange={(e) => setMarca(e.target.value)} />
        <Campo
          etiqueta="Cantidad"
          inputMode="numeric"
          value={cantidad}
          onChange={(e) => setCantidad(e.target.value.replace(/\D/g, ''))}
        />
        <Campo
          etiqueta="Precio (con IGV)"
          inputMode="decimal"
          placeholder="0.00"
          value={precio}
          onChange={(e) => setPrecio(e.target.value.replace(/[^\d.,]/g, ''))}
        />
      </div>
      <Boton type="submit" ancho disabled={ocupado || !listo}>
        Agregar al pedido
      </Boton>
    </form>
  )
}
