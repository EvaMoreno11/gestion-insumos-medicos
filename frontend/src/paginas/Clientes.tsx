import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { categoriasPrecio, clientes, resumen } from '../api/recursos'
import type { LocalDatos, ProductoComprado } from '../api/tipos'
import { Aviso, Boton, BotonEnlace, Buscador, Campo, Cargando, EstadoCarga, Etiqueta, Tarjeta, Vacio } from '../componentes/basicos'
import { DatosCliente, SelectorCategoria } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { TextoGanancia } from '../componentes/resumen'
import { useBusquedaEnUrl, useCarga, useListaConVerMas, useRetardo } from '../hooks'
import { conUnidad, dinero, fechaDia, haceDias, porcentaje as textoPorcentaje, unir } from '../util/formato'

// ------------------------------------------------------------------ lista

export function Clientes() {
  const [texto, setTexto] = useState('')
  const buscado = useRetardo(texto.trim())
  const { datos, cargando, error } = useCarga(() => clientes.buscar(buscado), buscado)

  return (
    <Pantalla
      titulo="Clientes"
      volver="/"
      ancho="amplio"
      pie={
        <BotonEnlace a="/clientes/nuevo" ancho>
          + Nuevo cliente
        </BotonEnlace>
      }
    >
      <div className="space-y-3">
        <Buscador valor={texto} onCambio={setTexto} placeholder="Buscar por nombre, RUC, local o contacto…" />
        <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
          <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
            {datos?.contenido.map((c) => (
              <li key={c.id}>
                <Link
                  to={`/clientes/${c.id}`}
                  className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50"
                >
                  <DatosCliente cliente={c} />
                  {c.porcentaje === null ? (
                    <Etiqueta color="ambar">Sin categoría</Etiqueta>
                  ) : (
                    <Etiqueta color="azul">{c.categoria ?? textoPorcentaje(c.porcentaje)}</Etiqueta>
                  )}
                </Link>
              </li>
            ))}
          </ul>
          {datos?.contenido.length === 0 && (
            <Vacio>{buscado ? `No hay clientes que coincidan con “${buscado}”.` : 'Aún no hay clientes.'}</Vacio>
          )}
        </EstadoCarga>
      </div>
    </Pantalla>
  )
}

// ------------------------------------------------------------------ formulario

const LOCAL_VACIO: LocalDatos = { nombre: '', direccion: '', contacto: '', telefono: '' }

export function ClienteFormulario() {
  const parametro = useParams().id
  const id = parametro ? Number(parametro) : null
  const navegar = useNavigate()
  const [params, setParams] = useSearchParams()
  const pestana = id !== null && params.get('ver') !== 'datos' ? 'productos' : 'datos'
  const categorias = useCarga(() => categoriasPrecio.listar())

  const [cargado, setCargado] = useState(id === null)
  const [nombre, setNombre] = useState('')
  const [rucDni, setRucDni] = useState('')
  const [porcentaje, setPorcentaje] = useState<number | null>(null)
  const [observaciones, setObservaciones] = useState('')
  const [plazo, setPlazo] = useState('')
  const [locales, setLocales] = useState<LocalDatos[]>([{ ...LOCAL_VACIO }])
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    if (id === null) {
      return
    }
    clientes
      .obtener(id)
      .then((c) => {
        setNombre(c.nombre)
        setRucDni(c.rucDni ?? '')
        setPorcentaje(c.porcentaje)
        setObservaciones(c.observaciones ?? '')
        setPlazo(c.plazoDias === null ? '' : String(c.plazoDias))
        setLocales(
          c.locales.map((l) => ({
            id: l.id,
            nombre: l.nombre ?? '',
            direccion: l.direccion ?? '',
            contacto: l.contacto ?? '',
            telefono: l.telefono ?? '',
          })),
        )
        setCargado(true)
      })
      .catch((e: unknown) => setError(mensajeDe(e)))
  }, [id])

  function cambiarLocal(indice: number, campo: keyof Omit<LocalDatos, 'id'>, valor: string) {
    setLocales((actuales) => actuales.map((l, i) => (i === indice ? { ...l, [campo]: valor } : l)))
  }

  async function guardar() {
    setOcupado(true)
    setError(null)
    try {
      const datos = { nombre, rucDni, porcentaje, observaciones, plazoDias: plazo === '' ? null : Number(plazo), locales }
      if (id === null) {
        await clientes.crear(datos)
      } else {
        await clientes.actualizar(id, datos)
      }
      navegar(-1)
    } catch (e) {
      setError(mensajeDe(e))
      setOcupado(false)
    }
  }

  const variosLocales = locales.length > 1

  return (
    <Pantalla titulo={id === null ? 'Nuevo cliente' : nombre || 'Cliente'} volver="/clientes">
      {id !== null && (
        <div className="mb-3 grid grid-cols-2 gap-1 rounded-xl bg-slate-200 p-1" role="tablist">
          {(
            [
              ['productos', 'Productos que compra'],
              ['datos', 'Datos del cliente'],
            ] as const
          ).map(([valor, titulo]) => (
            <button
              key={valor}
              type="button"
              role="tab"
              aria-selected={pestana === valor}
              onClick={() => setParams(valor === 'datos' ? { ver: 'datos' } : {}, { replace: true })}
              className={`min-h-10 rounded-lg px-1 text-sm font-semibold ${
                pestana === valor ? 'bg-white text-marca shadow-sm' : 'text-slate-600'
              }`}
            >
              {titulo}
            </button>
          ))}
        </div>
      )}
      {id !== null && pestana === 'productos' ? (
        <ProductosDelCliente id={id} />
      ) : !cargado ? (
        error ? (
          <Aviso tipo="error">{error}</Aviso>
        ) : (
          <Cargando />
        )
      ) : (
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault()
            void guardar()
          }}
        >
          {error && <Aviso tipo="error">{error}</Aviso>}

          <Tarjeta className="space-y-3">
            <Campo etiqueta="Nombre o razón social" value={nombre} onChange={(e) => setNombre(e.target.value)} required />
            <Campo
              etiqueta="RUC o DNI"
              inputMode="numeric"
              value={rucDni}
              onChange={(e) => setRucDni(e.target.value.replace(/\D/g, '').slice(0, 11))}
              ayuda="Opcional: 11 dígitos (RUC) u 8 (DNI)"
            />
            <Campo etiqueta="Observaciones" value={observaciones} onChange={(e) => setObservaciones(e.target.value)} />
            <Campo
              etiqueta="Plazo de pago habitual (días)"
              inputMode="numeric"
              value={plazo}
              onChange={(e) => setPlazo(e.target.value.replace(/\D/g, '').slice(0, 3))}
              ayuda="0 = contado. Vacío = sin definir. Se llena solo al anotar el crédito de uno de sus pedidos."
            />
          </Tarjeta>

          <Tarjeta>
            <h2 className="font-semibold">Categoría de precio</h2>
            <p className="mb-3 text-sm text-slate-500">
              Opcional. Si la dejas vacía, la app la pedirá en su primer pedido.
            </p>
            <EstadoCarga cargando={categorias.cargando} error={categorias.error} hayDatos={categorias.datos !== undefined}>
              <SelectorCategoria categorias={categorias.datos ?? []} valor={porcentaje} onCambio={setPorcentaje} />
            </EstadoCarga>
          </Tarjeta>

          {locales.map((local, i) => (
            <Tarjeta key={local.id ?? `nuevo-${i}`} className="space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="font-semibold">{variosLocales ? `Local ${i + 1}` : 'Local de entrega'}</h2>
                {variosLocales && (
                  <Boton
                    variante="texto"
                    className="min-h-10 px-2 text-sm text-red-700"
                    onClick={() => setLocales((actuales) => actuales.filter((_, j) => j !== i))}
                  >
                    Quitar
                  </Boton>
                )}
              </div>
              <Campo
                etiqueta="Nombre del local"
                placeholder="Botica Santa Rosa, Sede Los Olivos…"
                value={local.nombre}
                onChange={(e) => cambiarLocal(i, 'nombre', e.target.value)}
                ayuda={
                  variosLocales
                    ? 'El nombre con el que lo conocen, p. ej. el de la botica'
                    : 'Opcional: el nombre con el que lo conocen, p. ej. el de la botica'
                }
              />
              <Campo etiqueta="Dirección" value={local.direccion} onChange={(e) => cambiarLocal(i, 'direccion', e.target.value)} />
              <div className="grid grid-cols-2 gap-3">
                <Campo etiqueta="Contacto" value={local.contacto} onChange={(e) => cambiarLocal(i, 'contacto', e.target.value)} />
                <Campo
                  etiqueta="Teléfono"
                  inputMode="tel"
                  value={local.telefono}
                  onChange={(e) => cambiarLocal(i, 'telefono', e.target.value)}
                />
              </div>
            </Tarjeta>
          ))}

          <Boton variante="secundario" ancho onClick={() => setLocales((actuales) => [...actuales, { ...LOCAL_VACIO }])}>
            + Agregar otro local
          </Boton>

          <Boton type="submit" ancho disabled={ocupado || nombre.trim() === ''}>
            {ocupado ? 'Guardando…' : 'Guardar cliente'}
          </Boton>
        </form>
      )}
    </Pantalla>
  )
}

/** Lo que compró el cliente en lo que va del año, con la ganancia que dejó (uso interno). */
function ComprasDelCliente({ id }: { id: number }) {
  const { datos } = useCarga(() => resumen.cliente(id), id)
  if (!datos) {
    return null
  }
  if (datos.pedidos === 0) {
    return (
      <Tarjeta>
        <p className="text-sm text-slate-600">
          Este año todavía no tiene pedidos despachados
          {datos.ultimoPedido ? ` · último pedido el ${fechaDia(datos.ultimoPedido)}` : ''}.
        </p>
      </Tarjeta>
    )
  }
  return (
    <Tarjeta>
      <p className="text-xs uppercase tracking-wide text-slate-500">Este año</p>
      <p className="mt-1">
        Compró <strong>{dinero(datos.ventas)}</strong> · {datos.pedidos} {datos.pedidos === 1 ? 'pedido' : 'pedidos'}
      </p>
      <TextoGanancia ganancia={datos.ganancia} margen={datos.margen} />
      {datos.ultimoPedido && (
        <p className="mt-1 text-sm text-slate-500">
          Último pedido {haceDias(datos.ultimoPedido)} ({fechaDia(datos.ultimoPedido)})
        </p>
      )}
      {datos.ventasSinCosto > 0 && (
        <p className="mt-1 text-xs text-amber-700">
          {dinero(datos.ventasSinCosto)} de lo vendido no tiene costo y no entra en la ganancia.
        </p>
      )}
    </Tarjeta>
  )
}

/**
 * Pestaña "Productos que compra" (uso interno: para saber a qué precio se le vendió cada producto; el cliente no la
 * ve). Arriba, lo que compró este año; debajo, cada producto que se le vendió en un pedido despachado, con el último
 * precio, la fecha y la cantidad, primero lo comprado más recientemente. Al tocar un producto se ven todas sus
 * ventas.
 */
function ProductosDelCliente({ id }: { id: number }) {
  const { texto, setTexto, buscado } = useBusquedaEnUrl()
  const { datos, cargando, error, lista, verMas, cargandoMas, errorMas } = useListaConVerMas(
    (pagina) => clientes.productos(id, { buscar: buscado, pagina }),
    `${id}|${buscado}`,
    (r) => r.contenido,
  )
  const [abierto, setAbierto] = useState<number | null>(null)
  const total = datos?.totalElementos ?? 0

  return (
    <div className="space-y-3">
      <ComprasDelCliente id={id} />
      <Buscador valor={texto} onCambio={setTexto} placeholder="Buscar producto…" />
      <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
        {datos && total > 0 && (
          <p className="text-sm text-slate-500">
            {total} {total === 1 ? 'producto' : 'productos'}
            {buscado ? ` con «${buscado}»` : ''}, primero lo que compró más recientemente
          </p>
        )}
        {datos && total === 0 && (
          <Vacio>
            {buscado
              ? 'No le vendieron ningún producto con esa búsqueda.'
              : 'Todavía no tiene pedidos despachados: aquí aparecerán los productos que compre.'}
          </Vacio>
        )}
        <ul className="space-y-2">
          {lista.map((p) => (
            <li key={p.productoId}>
              <FilaProductoComprado
                clienteId={id}
                producto={p}
                abierto={abierto === p.productoId}
                onTocar={() => setAbierto(abierto === p.productoId ? null : p.productoId)}
              />
            </li>
          ))}
        </ul>
        {errorMas && (
          <div className="mt-3">
            <Aviso tipo="error">{errorMas}</Aviso>
          </div>
        )}
        {lista.length < total && (
          <Boton variante="secundario" ancho className="mt-3" disabled={cargandoMas} onClick={() => void verMas()}>
            {cargandoMas ? 'Cargando…' : `Ver más (${total - lista.length} más)`}
          </Boton>
        )}
      </EstadoCarga>
    </div>
  )
}

function FilaProductoComprado({
  clienteId,
  producto: p,
  abierto,
  onTocar,
}: {
  clienteId: number
  producto: ProductoComprado
  abierto: boolean
  onTocar: () => void
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white">
      <button type="button" aria-expanded={abierto} onClick={onTocar} className="block w-full p-3 text-left active:bg-slate-50">
        <span className="flex items-start justify-between gap-3">
          <span className="min-w-0">
            <span className="block font-semibold leading-snug">{p.nombre}</span>
            <span className="block text-sm text-slate-500">{unir(' · ', p.presentacion, p.marca)}</span>
          </span>
          <span className="shrink-0 text-lg font-bold">{dinero(p.ultimoPrecio)}</span>
        </span>
        <span className="mt-1 flex items-end justify-between gap-3 text-sm text-slate-600">
          <span>
            Última venta: {fechaDia(p.ultimaVenta)} ({haceDias(p.ultimaVenta)}) · {conUnidad(p.ultimaCantidad, p.unidadMedida)}
          </span>
          <span className="shrink-0 text-marca" aria-hidden="true">
            {abierto ? '▴' : '▾'}
          </span>
        </span>
      </button>
      {abierto && <VentasDelProducto clienteId={clienteId} producto={p} />}
    </div>
  )
}

/** Todas las veces que se le vendió el producto: fecha, N° de nota, cantidad, precio y total. Cada una abre su nota. */
function VentasDelProducto({ clienteId, producto }: { clienteId: number; producto: ProductoComprado }) {
  const { datos, cargando, error } = useCarga(
    () => clientes.ventas(clienteId, producto.productoId),
    `${clienteId}|${producto.productoId}`,
  )
  return (
    <div className="border-t border-slate-100 px-3 pb-3 pt-2">
      <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
        <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          {datos?.length === 1 ? 'Se le vendió 1 vez' : `Se le vendió ${datos?.length ?? 0} veces`}
        </p>
        <ul className="divide-y divide-slate-100">
          {datos?.map((v) => (
            <li key={`${v.pedidoId}|${v.fecha}|${v.cantidad}`}>
              <Link to={`/pedidos/${v.pedidoId}`} className="flex items-start justify-between gap-3 py-2 text-sm">
                <span className="min-w-0">
                  <span className="block font-medium text-slate-800">{fechaDia(v.fecha)}</span>
                  <span className="block text-marca">{v.numero ? `N° ${v.numero}` : 'Solo tabla'}</span>
                </span>
                <span className="shrink-0 text-right">
                  <span className="block text-slate-700">
                    {conUnidad(v.cantidad, producto.unidadMedida)} × {dinero(v.precio)}
                  </span>
                  <span className="block font-semibold">{dinero(v.total)}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </EstadoCarga>
    </div>
  )
}
