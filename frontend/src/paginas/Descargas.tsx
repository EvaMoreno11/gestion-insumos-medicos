import { useState } from 'react'
import { mensajeDe } from '../api/http'
import { clientes, exportar } from '../api/recursos'
import type { ClienteResumen } from '../api/tipos'
import { Aviso, Boton, Buscador, EstadoCarga, Tarjeta, Vacio } from '../componentes/basicos'
import { DatosCliente } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga, useRetardo } from '../hooks'
import { compartirODescargar } from '../util/archivos'
import { hoyLocal } from '../util/formato'

/**
 * Descargar el inventario completo (Excel, uso interno) o el catálogo para clientes (PDF o Excel),
 * sin precios o con la lista de precios de un cliente.
 */
export default function Descargas() {
  const [ocupado, setOcupado] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [conPrecios, setConPrecios] = useState(false)
  const [cliente, setCliente] = useState<ClienteResumen | null>(null)

  async function bajar(clave: string, archivo: () => Promise<Blob>, nombre: string) {
    setOcupado(clave)
    setError(null)
    try {
      await compartirODescargar(await archivo(), nombre)
    } catch (e) {
      setError(mensajeDe(e))
    } finally {
      setOcupado(null)
    }
  }

  const hoy = hoyLocal()
  const listo = !conPrecios || cliente !== null
  const nombreCatalogo = conPrecios ? `lista-de-precios-${hoy}` : `catalogo-${hoy}`

  return (
    <Pantalla titulo="Descargar" volver="/inventario">
      <div className="space-y-3">
        {error && <Aviso tipo="error">{error}</Aviso>}

        <Tarjeta>
          <h2 className="font-semibold">Inventario completo</h2>
          <p className="mt-1 text-sm text-slate-600">
            Excel con todos los productos, tengan o no stock: stock disponible, mínimo, costo y valor. Incluye una
            hoja con los lotes.
          </p>
          <p className="mt-1 text-sm font-medium text-amber-800">Solo para ustedes: lleva los costos.</p>
          <Boton
            ancho
            className="mt-3"
            disabled={ocupado !== null}
            onClick={() => bajar('inventario', exportar.inventario, `inventario-${hoy}.xlsx`)}
          >
            {ocupado === 'inventario' ? 'Preparando…' : 'Descargar inventario (Excel)'}
          </Boton>
        </Tarjeta>

        <Tarjeta>
          <h2 className="font-semibold">Catálogo para clientes</h2>
          <p className="mt-1 text-sm text-slate-600">
            Los productos que tienen stock mínimo, por categoría, con los datos del negocio. Sin costos ni cantidades.
          </p>

          <div className="mt-3 grid grid-cols-2 gap-1 rounded-xl bg-slate-200 p-1" role="tablist" aria-label="Precios">
            {[
              { valor: false, titulo: 'Sin precios' },
              { valor: true, titulo: 'Con precios' },
            ].map((o) => (
              <button
                key={o.titulo}
                type="button"
                role="tab"
                aria-selected={conPrecios === o.valor}
                onClick={() => setConPrecios(o.valor)}
                className={`min-h-10 rounded-lg text-sm font-semibold ${
                  conPrecios === o.valor ? 'bg-white text-marca shadow-sm' : 'text-slate-600'
                }`}
              >
                {o.titulo}
              </button>
            ))}
          </div>

          {conPrecios && (
            <div className="mt-3">
              {cliente ? (
                <div className="flex items-center justify-between gap-3 rounded-xl border-2 border-marca bg-marca-claro p-3">
                  <DatosCliente cliente={cliente} />
                  <Boton variante="texto" className="min-h-10 shrink-0 px-2" onClick={() => setCliente(null)}>
                    Cambiar
                  </Boton>
                </div>
              ) : (
                <ElegirCliente onElegir={setCliente} />
              )}
              <p className="mt-2 text-sm text-slate-500">
                Cada precio sale con el porcentaje de ese cliente, como en sus pedidos.
              </p>
            </div>
          )}

          <div className="mt-3 grid grid-cols-2 gap-2">
            <Boton
              disabled={ocupado !== null || !listo}
              onClick={() => bajar('pdf', () => exportar.catalogo('pdf', cliente?.id), `${nombreCatalogo}.pdf`)}
            >
              {ocupado === 'pdf' ? 'Preparando…' : 'PDF'}
            </Boton>
            <Boton
              variante="secundario"
              disabled={ocupado !== null || !listo}
              onClick={() => bajar('xlsx', () => exportar.catalogo('xlsx', cliente?.id), `${nombreCatalogo}.xlsx`)}
            >
              {ocupado === 'xlsx' ? 'Preparando…' : 'Excel'}
            </Boton>
          </div>
        </Tarjeta>
      </div>
    </Pantalla>
  )
}

function ElegirCliente({ onElegir }: { onElegir: (cliente: ClienteResumen) => void }) {
  const [texto, setTexto] = useState('')
  const buscado = useRetardo(texto.trim())
  const lista = useCarga(() => clientes.buscar(buscado), buscado)

  return (
    <div className="space-y-2">
      <Buscador valor={texto} onCambio={setTexto} placeholder="Buscar cliente por nombre, RUC, local o contacto…" />
      <EstadoCarga cargando={lista.cargando} error={lista.error} hayDatos={lista.datos !== undefined}>
        {lista.datos?.contenido.length === 0 && <Vacio>No hay clientes que coincidan.</Vacio>}
        <ul className="max-h-72 space-y-2 overflow-y-auto">
          {lista.datos?.contenido.map((c) => (
            <li key={c.id}>
              <button
                type="button"
                onClick={() => onElegir(c)}
                className="flex w-full items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3 text-left active:bg-slate-50"
              >
                <DatosCliente cliente={c} />
              </button>
            </li>
          ))}
        </ul>
      </EstadoCarga>
    </div>
  )
}
