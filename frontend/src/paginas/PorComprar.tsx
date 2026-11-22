import { useEffect, useEffectEvent, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { pedidos, productos } from '../api/recursos'
import type { ProductoDetalle, ProductoPorComprar, PorComprar as PorComprarDatos } from '../api/tipos'
import { Aviso, Boton, BotonEnlace, Campo, EstadoCarga, Tarjeta, Vacio } from '../componentes/basicos'
import { BuscadorProductos } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { diaLocal, dinero, fechaDia, leerEntero, unir } from '../util/formato'

/**
 * Lista universal de lo que hay que comprar: lo que falta para completar los pedidos pendientes y los productos
 * agregados a mano (recordatorios). Un producto que está en las dos sale una sola vez, arriba, con todo sumado.
 * Se llega con ?agregar=ID desde la ficha de un producto para agregarlo directamente.
 */
export default function PorComprar() {
  const [params, setParams] = useSearchParams()
  const [recarga, setRecarga] = useState(0)
  const { datos, cargando, error } = useCarga(() => pedidos.porComprar(), recarga)
  const [agregando, setAgregando] = useState(false)
  const [errorQuitar, setErrorQuitar] = useState<string | null>(null)

  const preelegido = params.get('agregar')
  const abierto = agregando || preelegido !== null
  const aMano = datos?.productos.filter((p) => p.aMano !== null) ?? []
  const paraPedidos = datos?.productos.filter((p) => p.aMano === null) ?? []
  const vacio = datos && datos.productos.length === 0 && datos.noRegistrados.length === 0

  function cerrar() {
    setAgregando(false)
    if (preelegido !== null) {
      setParams({}, { replace: true })
    }
  }

  function cambiar(p: ProductoPorComprar) {
    setParams({ agregar: String(p.productoId) }, { replace: true })
    window.scrollTo({ top: 0 })
  }

  async function quitar(p: ProductoPorComprar) {
    const aviso = p.faltante > 0 ? ' Lo que falta para los pedidos sigue en la lista.' : ''
    if (!window.confirm(`¿Quitar «${p.nombre}» de lo agregado a mano?${aviso}`)) {
      return
    }
    setErrorQuitar(null)
    try {
      await pedidos.quitarDePorComprar(p.productoId)
      setRecarga((r) => r + 1)
    } catch (e) {
      setErrorQuitar(mensajeDe(e))
    }
  }

  return (
    <Pantalla
      titulo="Por comprar"
      volver="/"
      ancho="amplio"
      pie={datos && !vacio ? <CostoDeLoQueFalta datos={datos} /> : undefined}
    >
      <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
        {datos && (
          <div className="mb-4 lg:max-w-xl">
            {abierto ? (
              <AgregarProducto
                // Al cambiar de producto (desde "Cambiar" de otra tarjeta) el panel empieza de nuevo
                key={preelegido ?? 'nuevo'}
                productoId={preelegido === null ? undefined : Number(preelegido)}
                datos={datos}
                onListo={() => {
                  cerrar()
                  setRecarga((r) => r + 1)
                }}
                onCancelar={cerrar}
              />
            ) : (
              <Boton variante="secundario" ancho onClick={() => setAgregando(true)}>
                + Agregar producto
              </Boton>
            )}
          </div>
        )}
        {errorQuitar && (
          <div className="mb-3">
            <Aviso tipo="error">{errorQuitar}</Aviso>
          </div>
        )}

        {vacio && (
          <Vacio>
            No falta nada: todos los pedidos pendientes están completos. Con "Agregar producto" puedes anotar algo que
            no quieres olvidarte de comprar.
          </Vacio>
        )}

        {aMano.length > 0 && (
          <section>
            <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-500">Agregados a mano</h2>
            <ListaPorComprar productos={aMano} onCambiar={cambiar} onQuitar={(p) => void quitar(p)} />
          </section>
        )}

        {paraPedidos.length > 0 && (
          <section className={aMano.length > 0 ? 'mt-6' : ''}>
            {aMano.length > 0 && (
              <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-500">
                Para pedidos pendientes
              </h2>
            )}
            <ListaPorComprar productos={paraPedidos} onCambiar={cambiar} onQuitar={(p) => void quitar(p)} />
          </section>
        )}

        {datos && datos.noRegistrados.length > 0 && (
          <section className="mt-6">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">No registrados</h2>
            <p className="mb-2 text-sm text-slate-500">
              Ítems escritos a mano en un pedido. Al registrarlos como productos, la línea del pedido pasa a
              descontar del inventario.
            </p>
            <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
              {datos.noRegistrados.map((n) => (
                <li key={n.itemId} className="rounded-2xl border border-slate-200 bg-white p-3">
                  <Link to={`/pedidos/${n.pedidoId}`} className="flex items-start justify-between gap-3">
                    <span className="min-w-0">
                      <span className="block font-medium leading-snug">{n.descripcion}</span>
                      <span className="block text-sm text-slate-500">{unir(' · ', n.presentacion, n.marca)}</span>
                      <span className="block text-sm text-marca">{unir(' · ', n.cliente, n.local)}</span>
                    </span>
                    <span className="shrink-0 text-xl font-bold">{n.cantidad}</span>
                  </Link>
                  <div className="mt-3">
                    <BotonEnlace
                      a={`/productos/nuevo?${new URLSearchParams({
                        pedido: String(n.pedidoId),
                        item: String(n.itemId),
                        nombre: n.descripcion,
                        presentacion: n.presentacion ?? '',
                        marca: n.marca ?? '',
                      }).toString()}`}
                      variante="secundario"
                      ancho
                    >
                      Registrar como producto
                    </BotonEnlace>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}
      </EstadoCarga>
    </Pantalla>
  )
}

function ListaPorComprar({
  productos: lista,
  onCambiar,
  onQuitar,
}: {
  productos: ProductoPorComprar[]
  onCambiar: (p: ProductoPorComprar) => void
  onQuitar: (p: ProductoPorComprar) => void
}) {
  return (
    <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
      {lista.map((p) => (
        <li key={p.productoId}>
          <TarjetaPorComprar producto={p} onCambiar={() => onCambiar(p)} onQuitar={() => onQuitar(p)} />
        </li>
      ))}
    </ul>
  )
}

/**
 * Un producto por comprar. Lo que esperan los pedidos dice FALTAN en rojo; lo agregado a mano dice COMPRAR en azul
 * (lo que falta más lo agregado) y, en vez de un cliente, la fila "Agregado a mano el …" con Cambiar y Quitar.
 */
function TarjetaPorComprar({
  producto: p,
  onCambiar,
  onQuitar,
}: {
  producto: ProductoPorComprar
  onCambiar: () => void
  onQuitar: () => void
}) {
  const agregado = p.aMano
  const stock =
    p.pedido > 0
      ? `Piden ${p.pedido} · hay ${p.stockDisponible}`
      : unir(' · ', `Hay ${p.stockDisponible}`, p.stockMinimo > 0 ? `mínimo ${p.stockMinimo}` : null)
  return (
    <Tarjeta>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="font-semibold leading-snug">{p.nombre}</h2>
          <p className="text-sm text-slate-500">{unir(' · ', p.presentacion, p.marca)}</p>
          <p className="mt-1 text-sm text-slate-600">{stock}</p>
        </div>
        <div className="shrink-0 text-right">
          <div className="text-xs uppercase tracking-wide text-slate-500">{agregado ? 'Comprar' : 'Faltan'}</div>
          {p.comprar > 0 ? (
            <div className={`text-3xl font-bold leading-none ${agregado ? 'text-marca' : 'text-red-700'}`}>
              {p.comprar}
            </div>
          ) : (
            <div className="text-sm font-medium text-slate-500">sin cantidad</div>
          )}
        </div>
      </div>

      <ul className="mt-3 space-y-1 border-t border-slate-100 pt-3 text-sm">
        {p.pedidos.map((e) => (
          <li key={e.pedidoId}>
            <Link to={`/pedidos/${e.pedidoId}`} className="flex justify-between gap-3 text-marca">
              <span className="min-w-0 break-words">{unir(' · ', e.cliente, e.local)}</span>
              <span className="shrink-0 text-slate-600">le faltan {e.faltante}</span>
            </Link>
          </li>
        ))}
        {agregado && (
          <li className="flex items-start justify-between gap-3">
            <span className="min-w-0 break-words text-slate-600">
              {unir(
                ' · ',
                `Agregado a mano el ${fechaDia(diaLocal(new Date(agregado.creadoEn)))}`,
                agregado.nota && `«${agregado.nota}»`,
              )}
            </span>
            <span className="flex shrink-0 items-center gap-3">
              {p.faltante > 0 && agregado.cantidad !== null && (
                <span className="text-slate-600">{agregado.cantidad} más</span>
              )}
              <button type="button" className="min-h-8 font-semibold text-marca" onClick={onCambiar}>
                Cambiar
              </button>
              <button type="button" className="min-h-8 font-semibold text-red-700" onClick={onQuitar}>
                Quitar
              </button>
            </span>
          </li>
        )}
      </ul>

      <div className="mt-3">
        <BotonEnlace a={`/entrada?producto=${p.productoId}`} variante="secundario" ancho>
          Llegó: registrar entrada
        </BotonEnlace>
      </div>
    </Tarjeta>
  )
}

/**
 * Agregar un producto a mano (o cambiar el que ya está): se elige, se ve cuánto hay, y se escriben cuántos comprar
 * (opcional, por si aún no se sabe) y una nota. Si ya falta para algún pedido, avisa que se suma en la misma tarjeta.
 */
function AgregarProducto({
  productoId,
  datos,
  onListo,
  onCancelar,
}: {
  productoId: number | undefined
  datos: PorComprarDatos
  onListo: () => void
  onCancelar: () => void
}) {
  const [producto, setProducto] = useState<ProductoDetalle>()
  const [cantidad, setCantidad] = useState('')
  const [nota, setNota] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  /** Si ya estaba agregado, el formulario viene con su cantidad y su nota. */
  function mostrar(p: ProductoDetalle) {
    const ya = datos.productos.find((x) => x.productoId === p.id)?.aMano
    setProducto(p)
    setCantidad(ya?.cantidad ? String(ya.cantidad) : '')
    setNota(ya?.nota ?? '')
  }

  async function elegir(id: number) {
    setError(null)
    try {
      mostrar(await productos.obtener(id))
    } catch (e) {
      setError(mensajeDe(e))
    }
  }

  // Llegando desde la ficha del producto o desde "Cambiar", el producto ya viene elegido. Solo se llena al abrir:
  // si la lista se recarga mientras se escribe, no se pisa lo escrito.
  const mostrarElegido = useEffectEvent(mostrar)
  useEffect(() => {
    if (productoId === undefined) {
      return
    }
    let vigente = true
    productos
      .obtener(productoId)
      .then((p) => vigente && mostrarElegido(p))
      .catch((e: unknown) => vigente && setError(mensajeDe(e)))
    return () => {
      vigente = false
    }
  }, [productoId])

  const existente = producto ? datos.productos.find((x) => x.productoId === producto.id) : undefined
  const cantidadLeida = cantidad.trim() === '' ? null : leerEntero(cantidad)
  const cantidadValida = cantidad.trim() === '' || (cantidadLeida !== null && cantidadLeida > 0)

  async function guardar() {
    if (!producto || !cantidadValida) {
      return
    }
    setOcupado(true)
    setError(null)
    try {
      await pedidos.agregarAPorComprar(producto.id, { cantidad: cantidadLeida, nota: nota.trim() || null })
      onListo()
    } catch (e) {
      setError(mensajeDe(e))
      setOcupado(false)
    }
  }

  return (
    <Tarjeta>
      {error && (
        <div className="mb-3">
          <Aviso tipo="error">{error}</Aviso>
        </div>
      )}
      {!producto ? (
        <div className="space-y-3">
          {productoId === undefined ? (
            <BuscadorProductos onElegir={(p) => void elegir(p.id)} placeholder="¿Qué producto hay que comprar?" autoFocus />
          ) : (
            <p className="text-sm text-slate-500">Cargando el producto…</p>
          )}
          <Boton variante="secundario" ancho onClick={onCancelar}>
            Cancelar
          </Boton>
        </div>
      ) : (
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault()
            void guardar()
          }}
        >
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h2 className="font-semibold leading-snug">{producto.nombre}</h2>
              <p className="text-sm text-slate-500">{unir(' · ', producto.presentacion, producto.marca)}</p>
              <p className="text-sm text-slate-600">
                {unir(
                  ' · ',
                  `Hay ${producto.stockDisponible} disponibles`,
                  producto.stockSeparado > 0 ? `${producto.stockSeparado} separadas para pedidos` : null,
                  producto.stockMinimo > 0 ? `mínimo ${producto.stockMinimo}` : null,
                )}
              </p>
            </div>
            {productoId === undefined && (
              <Boton variante="texto" className="min-h-10 shrink-0 px-2" onClick={() => setProducto(undefined)}>
                Cambiar
              </Boton>
            )}
          </div>

          {existente?.aMano ? (
            <Aviso tipo="info">
              Ya lo agregaste el {fechaDia(diaLocal(new Date(existente.aMano.creadoEn)))}. Al guardar se cambian la
              cantidad y la nota.
            </Aviso>
          ) : (
            existente &&
            existente.faltante > 0 && (
              <Aviso tipo="alerta">
                Ya está en Por comprar: a los pedidos pendientes les faltan {existente.faltante}. Lo que escribas aquí
                se compra además, y se verá en la misma tarjeta.
              </Aviso>
            )
          )}

          <Campo
            etiqueta={`¿Cuántos comprar? (${producto.unidadMedida.toLowerCase()})`}
            inputMode="numeric"
            autoComplete="off"
            value={cantidad}
            onChange={(e) => setCantidad(e.target.value.replace(/\D/g, ''))}
            error={cantidadValida ? undefined : 'Escribe un número mayor que 0, o déjalo vacío'}
            ayuda="Opcional: déjalo vacío si aún no sabes cuántos"
          />
          <Campo
            etiqueta="Nota (opcional)"
            autoComplete="off"
            maxLength={300}
            placeholder="Ej.: urgente, se acaba esta semana"
            value={nota}
            onChange={(e) => setNota(e.target.value)}
          />
          <div className="grid grid-cols-2 gap-3">
            <Boton variante="secundario" onClick={onCancelar}>
              Cancelar
            </Boton>
            <Boton type="submit" disabled={!cantidadValida || ocupado}>
              {ocupado ? 'Guardando…' : existente?.aMano ? 'Guardar cambios' : 'Agregar'}
            </Boton>
          </div>
        </form>
      )}
    </Tarjeta>
  )
}

/** Barra fija de abajo: cuánto costaría comprar todo, con el último costo de cada producto. */
function CostoDeLoQueFalta({ datos }: { datos: PorComprarDatos }) {
  const sinSumar = unir(
    ' · ',
    datos.productosSinCosto > 0
      ? `${datos.productosSinCosto} ${datos.productosSinCosto === 1 ? 'producto sin costo no se suma' : 'productos sin costo no se suman'}`
      : null,
    datos.noRegistrados.length > 0 ? 'los no registrados no se suman' : null,
  )
  return (
    <div className="flex items-center justify-between gap-3">
      <div className="min-w-0">
        <p className="font-semibold leading-snug">Costo de lo que falta</p>
        <p className="text-sm text-slate-500">{sinSumar || 'Con el último costo de compra de cada producto'}</p>
      </div>
      <p className="shrink-0 text-2xl font-bold tabular-nums">{dinero(datos.costoTotal)}</p>
    </div>
  )
}
