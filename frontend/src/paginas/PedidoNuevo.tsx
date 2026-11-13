import { useState } from 'react'
import { useNavigate } from 'react-router'
import { mensajeDe } from '../api/http'
import { categoriasPrecio, clientes, pedidos } from '../api/recursos'
import type { ClienteDetalle } from '../api/tipos'
import { Aviso, Boton, BotonEnlace, Buscador, EstadoCarga, Etiqueta, Tarjeta, Vacio } from '../componentes/basicos'
import { DatosCliente, SelectorCategoria } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga, useRetardo } from '../hooks'
import { porcentaje as textoPorcentaje, unir } from '../util/formato'

/**
 * Paso 1 de un pedido: elegir el cliente, su local (si tiene varios) y la categoría de precio.
 * Si el cliente ya tiene categoría viene puesta; si no, hay que elegirla y se le guarda.
 */
export default function PedidoNuevo() {
  const navegar = useNavigate()
  const [texto, setTexto] = useState('')
  const buscado = useRetardo(texto.trim())
  const lista = useCarga(() => clientes.buscar(buscado), buscado)
  const categorias = useCarga(() => categoriasPrecio.listar())

  const [cliente, setCliente] = useState<ClienteDetalle>()
  const [localId, setLocalId] = useState<number>()
  const [porcentaje, setPorcentaje] = useState<number | null>(null)
  const [guardarEnCliente, setGuardarEnCliente] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  async function elegir(id: number) {
    setError(null)
    try {
      const detalle = await clientes.obtener(id)
      setCliente(detalle)
      setLocalId(detalle.locales.length === 1 ? detalle.locales[0].id : undefined)
      setPorcentaje(detalle.porcentaje)
      setGuardarEnCliente(false)
    } catch (e) {
      setError(mensajeDe(e))
    }
  }

  async function crear() {
    if (!cliente || localId === undefined || porcentaje === null) {
      return
    }
    setOcupado(true)
    setError(null)
    try {
      const pedido = await pedidos.crear({
        clienteId: cliente.id,
        localId,
        porcentaje,
        guardarPorcentajeEnCliente: guardarEnCliente,
      })
      navegar(`/pedidos/${pedido.id}`, { replace: true })
    } catch (e) {
      setError(mensajeDe(e))
      setOcupado(false)
    }
  }

  // ------------------------------------------------------------ paso A: buscar el cliente
  if (!cliente) {
    return (
      <Pantalla titulo="Nuevo pedido" volver="/pedidos">
        <div className="space-y-3">
          <Buscador valor={texto} onCambio={setTexto} placeholder="Buscar cliente por nombre, RUC, local o contacto…" autoFocus />
          {error && <Aviso tipo="error">{error}</Aviso>}
          <EstadoCarga cargando={lista.cargando} error={lista.error} hayDatos={lista.datos !== undefined}>
            <ul className="space-y-2">
              {lista.datos?.contenido.map((c) => (
                <li key={c.id}>
                  <button
                    type="button"
                    onClick={() => elegir(c.id)}
                    className="flex w-full items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3 text-left active:bg-slate-50"
                  >
                    <DatosCliente cliente={c} />
                    {c.porcentaje === null ? (
                      <Etiqueta color="ambar">Sin categoría</Etiqueta>
                    ) : (
                      <Etiqueta color="azul">{c.categoria ?? textoPorcentaje(c.porcentaje)}</Etiqueta>
                    )}
                  </button>
                </li>
              ))}
            </ul>
            {lista.datos?.contenido.length === 0 && (
              <Vacio>{buscado ? `No hay clientes que coincidan con “${buscado}”.` : 'Aún no hay clientes.'}</Vacio>
            )}
          </EstadoCarga>
          <BotonEnlace a="/clientes/nuevo" variante="secundario" ancho>
            + Registrar cliente nuevo
          </BotonEnlace>
        </div>
      </Pantalla>
    )
  }

  // ------------------------------------------------------------ paso B: local y categoría
  const primeraVez = cliente.porcentaje === null
  return (
    <Pantalla
      titulo="Nuevo pedido"
      volver="/pedidos"
      pie={
        <Boton ancho onClick={crear} disabled={ocupado || localId === undefined || porcentaje === null}>
          {ocupado ? 'Creando…' : 'Empezar pedido'}
        </Boton>
      }
    >
      <div className="space-y-3">
        {error && <Aviso tipo="error">{error}</Aviso>}

        <Tarjeta>
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs uppercase tracking-wide text-slate-500">Cliente</p>
              <h2 className="text-lg font-semibold leading-snug">{cliente.nombre}</h2>
            </div>
            <Boton variante="texto" className="min-h-10 shrink-0 px-2" onClick={() => setCliente(undefined)}>
              Cambiar
            </Boton>
          </div>
        </Tarjeta>

        <Tarjeta>
          <h3 className="font-semibold">Local de entrega</h3>
          <div className="mt-2 space-y-2">
            {cliente.locales.map((l) => (
              <label
                key={l.id}
                className={`flex cursor-pointer items-start gap-3 rounded-xl border-2 p-3 ${
                  localId === l.id ? 'border-marca bg-marca-claro' : 'border-slate-200'
                }`}
              >
                <input
                  type="radio"
                  name="local"
                  className="mt-1 h-5 w-5 accent-marca"
                  checked={localId === l.id}
                  onChange={() => setLocalId(l.id)}
                />
                <span className="min-w-0">
                  <span className="block font-medium">{l.nombre ?? 'Local único'}</span>
                  <span className="block text-sm text-slate-600">
                    {unir(' · ', l.direccion, l.contacto, l.telefono) || 'Sin dirección registrada'}
                  </span>
                </span>
              </label>
            ))}
          </div>
        </Tarjeta>

        <Tarjeta>
          <h3 className="font-semibold">Categoría de precio</h3>
          <p className="mb-3 text-sm text-slate-500">
            {primeraVez
              ? 'Es el primer pedido de este cliente: elige su categoría. Quedará guardada para los siguientes.'
              : 'Viene con la categoría del cliente. Puedes cambiarla solo para este pedido.'}
          </p>
          <EstadoCarga cargando={categorias.cargando} error={categorias.error} hayDatos={categorias.datos !== undefined}>
            <SelectorCategoria categorias={categorias.datos ?? []} valor={porcentaje} onCambio={setPorcentaje} />
          </EstadoCarga>
          {!primeraVez && porcentaje !== null && porcentaje !== cliente.porcentaje && (
            <label className="mt-3 flex items-center gap-3 text-sm">
              <input
                type="checkbox"
                className="h-5 w-5 accent-marca"
                checked={guardarEnCliente}
                onChange={(e) => setGuardarEnCliente(e.target.checked)}
              />
              Usarla también en sus próximos pedidos
            </label>
          )}
        </Tarjeta>
      </div>
    </Pantalla>
  )
}
