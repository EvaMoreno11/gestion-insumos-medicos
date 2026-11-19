import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { categoriasPrecio, pedidos } from '../api/recursos'
import type { ItemCambio } from '../api/recursos'
import type { CategoriaPrecio, DocumentoPedido, GrupoPedido, Pedido, PedidoItem } from '../api/tipos'
import { Aviso, Boton, Campo, Cargando, Etiqueta, Tarjeta } from '../componentes/basicos'
import { TarjetaCobro } from '../componentes/cobro'
import { CorregirPedido, HistorialCorrecciones } from '../componentes/correccion'
import { BuscadorProductos, EtiquetaDisponibilidad, SelectorCategoria, SelectorDocumento } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { compartirODescargar } from '../util/archivos'
import { dinero, fechaCorta, leerDecimal, leerEntero, mesAnio, porcentaje as textoPorcentaje, unir } from '../util/formato'

export default function PedidoDetalle() {
  const id = Number(useParams().id)
  const volver = useSearchParams()[0].get('desde') === 'cobranzas' ? '/cobranzas' : '/pedidos'
  const [pedido, setPedido] = useState<Pedido>()
  const [categorias, setCategorias] = useState<CategoriaPrecio[]>([])
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [panel, setPanel] = useState<'categoria' | 'manual' | 'documento' | null>(null)
  const [confirmarParcial, setConfirmarParcial] = useState(false)
  const [corrigiendo, setCorrigiendo] = useState(false)

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
      const nombre =
        pedido.documento === 'SOLO_TABLA'
          ? `pedido-${pedido.id}`
          : pedido.numero
            ? `nota-pedido-${pedido.numero}`
            : `pedido-borrador-${pedido.id}`
      await compartirODescargar(await pedidos.nota(pedido.id, formato), `${nombre}.${formato}`)
    } catch (e) {
      setError(mensajeDe(e))
    } finally {
      setOcupado(false)
    }
  }

  if (!pedido) {
    return (
      <Pantalla titulo="Pedido" volver={volver}>
        {error ? <Aviso tipo="error">{error}</Aviso> : <Cargando />}
      </Pantalla>
    )
  }

  const borrador = pedido.estado === 'BORRADOR'
  const titulo =
    pedido.estado === 'ANULADO'
      ? 'Pedido anulado'
      : pedido.numero
        ? `Nota ${pedido.numero}`
        : pedido.estado === 'DESPACHADO'
          ? 'Pedido despachado'
          : 'Pedido'

  function despachar() {
    if (!pedido) {
      return
    }
    if (!pedido.listoParaDespachar) {
      setConfirmarParcial(true) // faltan unidades: primero se muestra qué sale y qué queda pendiente
      return
    }
    const texto =
      pedido.documento === 'SOLO_TABLA'
        ? '¿Despachar el pedido? Se descontará del inventario. Sale como "Solo tabla": sin número de nota.'
        : '¿Despachar el pedido? Se descontará del inventario y se le asignará el número de nota.'
    if (window.confirm(texto)) {
      void aplicar(() => pedidos.despachar(pedido.id))
    }
  }

  if (corrigiendo) {
    return (
      <Pantalla titulo={`Corregir ${titulo.toLowerCase()}`} volver={volver} ancho="amplio">
        <CorregirPedido
          pedido={pedido}
          onCancelar={() => setCorrigiendo(false)}
          onGuardado={(corregido) => {
            setPedido(corregido)
            setCorrigiendo(false)
          }}
        />
      </Pantalla>
    )
  }

  return (
    <Pantalla
      titulo={titulo}
      volver={volver}
      ancho="amplio"
      pie={
        <Pie
          pedido={pedido}
          ocupado={ocupado}
          aplicar={aplicar}
          nota={nota}
          onDespachar={despachar}
          onCorregir={() => setCorrigiendo(true)}
        />
      }
    >
      <div className="space-y-3">
        {error && <Aviso tipo="error">{error}</Aviso>}
        {pedido.estado === 'DESPACHADO' && (
          <Aviso tipo="exito">
            Despachado el {fechaCorta(pedido.despachadoEn)}. Ya se descontó del inventario.
          </Aviso>
        )}
        {pedido.estado === 'ANULADO' && <Aviso tipo="alerta">Este pedido fue anulado.</Aviso>}
        {pedido.origenId !== null && (
          <Aviso>
            Es el saldo (lo que faltó entregar) de{' '}
            <Link to={`/pedidos/${pedido.origenId}`} className="font-semibold text-marca underline">
              {pedido.origenNumero ? `la nota ${pedido.origenNumero}` : 'un pedido anterior'}
            </Link>
            . Conserva su lugar en la fila para recibir el stock que llegue.
          </Aviso>
        )}
        {pedido.cobro && <TarjetaCobro pedido={pedido} cobro={pedido.cobro} ocupado={ocupado} aplicar={aplicar} />}
        {pedido.correcciones.length > 0 && <HistorialCorrecciones correcciones={pedido.correcciones} />}
        {pedido.grupo && <EntregaPorPartes grupo={pedido.grupo} actual={pedido.id} />}
        {confirmarParcial && (
          <ConfirmarEntregaParcial
            pedido={pedido}
            ocupado={ocupado}
            onCancelar={() => setConfirmarParcial(false)}
            onConfirmar={async () => {
              await aplicar(() => pedidos.despachar(pedido.id, true))
              setConfirmarParcial(false)
            }}
          />
        )}

        {/* En la PC: a la izquierda el cliente y el buscador (quedan fijos al bajar); a la derecha los ítems */}
        <div className="space-y-3 lg:grid lg:grid-cols-[minmax(0,26rem)_minmax(0,1fr)] lg:items-start lg:gap-6 lg:space-y-0">
        <div className="space-y-3 lg:sticky lg:top-24">
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

        {/* ---------------- documento y observaciones ---------------- */}
        <Tarjeta>
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs uppercase tracking-wide text-slate-500">Documento</p>
              <p className="font-medium">
                {pedido.documento === 'SOLO_TABLA' ? 'Solo tabla (sin nombres ni número)' : 'Nota de pedido'}
              </p>
              {pedido.nota && <p className="mt-1 text-sm text-slate-600">Observaciones: {pedido.nota}</p>}
            </div>
            {borrador && (
              <Boton
                variante="texto"
                className="min-h-10 shrink-0 px-2"
                onClick={() => setPanel(panel === 'documento' ? null : 'documento')}
              >
                {panel === 'documento' ? 'Cerrar' : 'Cambiar'}
              </Boton>
            )}
          </div>
          {panel === 'documento' && (
            <CambiarDocumento
              pedido={pedido}
              ocupado={ocupado}
              onGuardar={async (documento, observaciones) => {
                const ok = await aplicar(() =>
                  pedidos.actualizar(pedido.id, {
                    documento,
                    nota: observaciones.trim() === '' ? null : observaciones,
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
        </div>

        {/* ---------------- ítems ---------------- */}
        <div>
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
                  pedidoId={pedido.id}
                  onCambiar={(cambio) => aplicar(() => pedidos.cambiarItem(pedido.id, item.id, cambio))}
                  onQuitar={() => aplicar(() => pedidos.quitarItem(pedido.id, item.id))}
                  onVincular={(productoId) => aplicar(() => pedidos.vincularProducto(pedido.id, item.id, productoId))}
                />
              </li>
            ))}
          </ul>
        )}
        </div>
        </div>
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
  onDespachar,
  onCorregir,
}: {
  pedido: Pedido
  ocupado: boolean
  aplicar: (accion: () => Promise<Pedido>) => Promise<boolean>
  nota: (formato: 'pdf' | 'xlsx') => void
  onDespachar: () => void
  onCorregir: () => void
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
              disabled={ocupado || !(pedido.listoParaDespachar || pedido.puedeEntregarParcial)}
              onClick={onDespachar}
            >
              {pedido.listoParaDespachar || !pedido.puedeEntregarParcial ? 'Despachar' : 'Despachar lo que hay'}
            </Boton>
          )}
          <Boton variante="secundario" className={borrador ? '' : 'flex-1'} disabled={ocupado} onClick={() => nota('pdf')}>
            PDF
          </Boton>
          <Boton variante="secundario" className={borrador ? '' : 'flex-1'} disabled={ocupado} onClick={() => nota('xlsx')}>
            Excel
          </Boton>
          {pedido.estado === 'DESPACHADO' && (
            <Boton variante="secundario" className="flex-1" disabled={ocupado} onClick={onCorregir}>
              Corregir
            </Boton>
          )}
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
  pedidoId,
  onCambiar,
  onQuitar,
  onVincular,
}: {
  numero: number
  item: PedidoItem
  editable: boolean
  ocupado: boolean
  pedidoId: number
  onCambiar: (cambio: ItemCambio) => void
  onQuitar: () => void
  onVincular: (productoId: number) => void
}) {
  const [cantidad, setCantidad] = useState(String(item.cantidad))
  const [precio, setPrecio] = useState(item.precioUnitario === null ? '' : item.precioUnitario.toFixed(2))
  const [eligiendo, setEligiendo] = useState(false)


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

      <div className="mt-2 flex flex-wrap gap-1.5 pl-7">
        {item.cantidad === 0 ? <Etiqueta color="gris">Quitado en una corrección</Etiqueta> : <EtiquetaDisponibilidad item={item} />}
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

      {editable && item.productoId === null && (
        <div className="ml-7 mt-2 border-t border-slate-100 pt-2">
          <p className="text-sm text-slate-600">No está en el inventario, así que no descuenta stock.</p>
          <div className="mt-1 flex flex-wrap gap-x-4">
            <Link
              to={`/productos/nuevo?${new URLSearchParams({
                pedido: String(pedidoId),
                item: String(item.id),
                nombre: item.descripcion,
                presentacion: item.presentacion ?? '',
                marca: item.marca ?? '',
              }).toString()}`}
              className="py-2 text-sm font-semibold text-marca"
            >
              Registrar como producto
            </Link>
            <button
              type="button"
              disabled={ocupado}
              onClick={() => setEligiendo(!eligiendo)}
              className="py-2 text-sm font-semibold text-marca"
            >
              {eligiendo ? 'Cancelar' : 'Elegir del inventario'}
            </button>
          </div>
          {eligiendo && (
            <BuscadorProductos
              placeholder="¿Cuál producto es?"
              autoFocus
              onElegir={(p) => {
                setEligiendo(false)
                onVincular(p.id)
              }}
            />
          )}
        </div>
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

function CambiarDocumento({
  pedido,
  ocupado,
  onGuardar,
}: {
  pedido: Pedido
  ocupado: boolean
  onGuardar: (documento: DocumentoPedido, observaciones: string) => void
}) {
  const [documento, setDocumento] = useState<DocumentoPedido>(pedido.documento)
  const [observaciones, setObservaciones] = useState(pedido.nota ?? '')

  return (
    <div className="mt-4 space-y-3 border-t border-slate-100 pt-4">
      <SelectorDocumento valor={documento} onCambio={setDocumento} />
      <Campo
        etiqueta="Observaciones"
        placeholder="(No se factura)"
        value={observaciones}
        onChange={(e) => setObservaciones(e.target.value)}
        maxLength={300}
        ayuda="Solo para ustedes: no sale en la nota ni en la tabla."
      />
      <Boton ancho disabled={ocupado} onClick={() => onGuardar(documento, observaciones)}>
        Guardar
      </Boton>
    </div>
  )
}

// ------------------------------------------------------------------ entrega por partes

/** Lo pedido, lo entregado y lo pendiente de un pedido que se entregó por partes. Solo para uso interno. */
function EntregaPorPartes({ grupo, actual }: { grupo: GrupoPedido; actual: number }) {
  const ESTADO = { BORRADOR: 'Pendiente', DESPACHADO: 'Entregado', ANULADO: 'Anulado' } as const
  return (
    <Tarjeta>
      <h2 className="font-semibold">Entrega por partes</h2>
      <p className="text-sm text-slate-500">Solo lo ven ustedes: el cliente recibe cada nota por separado.</p>
      <dl className="mt-3 grid grid-cols-3 gap-2 text-center">
        <div className="rounded-xl bg-slate-50 p-2">
          <dt className="text-xs uppercase tracking-wide text-slate-500">Pedido</dt>
          <dd className="font-bold">{dinero(grupo.pedido)}</dd>
        </div>
        <div className="rounded-xl bg-emerald-50 p-2">
          <dt className="text-xs uppercase tracking-wide text-emerald-800">Entregado</dt>
          <dd className="font-bold text-emerald-900">{dinero(grupo.entregado)}</dd>
        </div>
        <div className="rounded-xl bg-amber-50 p-2">
          <dt className="text-xs uppercase tracking-wide text-amber-900">Pendiente</dt>
          <dd className="font-bold text-amber-900">{dinero(grupo.pendiente)}</dd>
        </div>
      </dl>
      {grupo.anulado > 0 && (
        <p className="mt-2 text-sm text-slate-600">No se entregó (saldo anulado): {dinero(grupo.anulado)}</p>
      )}
      <ul className="mt-3 divide-y divide-slate-100 text-sm">
        {grupo.pedidos.map((p) => {
          const nombre = p.numero ? `Nota ${p.numero}` : p.esSaldo ? 'Saldo' : 'Pedido'
          const contenido = (
            <>
              <span className="min-w-0 truncate">
                {nombre} · {ESTADO[p.estado]}
                {p.despachadoEn ? ` el ${fechaCorta(p.despachadoEn)}` : ''}
              </span>
              <span className="shrink-0 font-semibold">{dinero(p.total)}</span>
            </>
          )
          return (
            <li key={p.id}>
              {p.id === actual ? (
                <div className="flex justify-between gap-3 py-2 font-medium">{contenido}</div>
              ) : (
                <Link to={`/pedidos/${p.id}`} className="flex justify-between gap-3 py-2 text-marca">
                  {contenido}
                </Link>
              )}
            </li>
          )
        })}
      </ul>
    </Tarjeta>
  )
}

/**
 * Antes de despachar un pedido con faltantes: muestra qué sale ahora y qué queda pendiente (con montos).
 * Lo pendiente pasa a un pedido nuevo (saldo).
 */
function ConfirmarEntregaParcial({
  pedido,
  ocupado,
  onCancelar,
  onConfirmar,
}: {
  pedido: Pedido
  ocupado: boolean
  onCancelar: () => void
  onConfirmar: () => void
}) {
  useEffect(() => {
    const alPresionar = (e: KeyboardEvent) => e.key === 'Escape' && onCancelar()
    window.addEventListener('keydown', alPresionar)
    return () => window.removeEventListener('keydown', alPresionar)
  }, [onCancelar])

  // Los ítems a mano no tienen control de stock: salen en la entrega
  const ahora = pedido.items
    .map((i) => ({ item: i, cantidad: i.productoId === null ? i.cantidad : Math.min(i.cantidad, i.disponible ?? 0) }))
    .filter((x) => x.cantidad > 0)
  const pendiente = pedido.items
    .filter((i) => (i.faltante ?? 0) > 0)
    .map((i) => ({ item: i, cantidad: i.faltante ?? 0 }))
  const suma = (lista: { item: PedidoItem; cantidad: number }[]) =>
    Math.round(lista.reduce((s, x) => s + x.cantidad * (x.item.precioUnitario ?? 0), 0) * 100) / 100

  const lista = (filas: { item: PedidoItem; cantidad: number }[], mostrarDeCuanto: boolean) => (
    <ul className="mt-1 divide-y divide-slate-100 text-sm">
      {filas.map(({ item, cantidad }) => (
        <li key={item.id} className="flex justify-between gap-3 py-1.5">
          <span className="min-w-0">{item.descripcion}</span>
          <span className="shrink-0 font-semibold">
            {cantidad}
            {mostrarDeCuanto && cantidad !== item.cantidad ? ` de ${item.cantidad}` : ''}
          </span>
        </li>
      ))}
    </ul>
  )

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="titulo-entrega-parcial"
    >
      <div className="max-h-[92dvh] w-full max-w-lg overflow-y-auto rounded-t-2xl bg-white p-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:rounded-2xl">
        <h2 id="titulo-entrega-parcial" className="text-lg font-bold">
          Este pedido tiene faltantes
        </h2>

        <section className="mt-3 rounded-xl border border-emerald-200 bg-emerald-50 p-3">
          <h3 className="flex justify-between gap-3 font-semibold text-emerald-900">
            <span>Se entrega ahora</span>
            <span>{dinero(suma(ahora))}</span>
          </h3>
          {lista(ahora, true)}
        </section>

        <section className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3">
          <h3 className="flex justify-between gap-3 font-semibold text-amber-900">
            <span>Queda pendiente</span>
            <span>{dinero(suma(pendiente))}</span>
          </h3>
          {lista(pendiente, false)}
        </section>

        <p className="mt-3 text-sm text-slate-600">
          Lo pendiente pasa a un pedido nuevo (saldo) para el mismo cliente y local, con los mismos precios y su lugar
          en la fila. {pedido.documento === 'SOLO_TABLA' ? 'La tabla' : 'La nota'} que recibe el cliente solo lleva lo
          que se entrega.
        </p>

        <div className="mt-4 flex gap-2">
          <Boton variante="secundario" className="flex-1" disabled={ocupado} onClick={onCancelar} autoFocus>
            Cancelar
          </Boton>
          <Boton className="flex-1" disabled={ocupado} onClick={onConfirmar}>
            {ocupado ? 'Despachando…' : 'Despachar lo que hay'}
          </Boton>
        </div>
      </div>
    </div>
  )
}
