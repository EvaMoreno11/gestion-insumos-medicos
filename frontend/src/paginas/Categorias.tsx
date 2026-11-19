import { useState } from 'react'
import { mensajeDe } from '../api/http'
import { categorias } from '../api/recursos'
import type { Categoria } from '../api/tipos'
import { Aviso, Boton, Buscador, EstadoCarga, Vacio } from '../componentes/basicos'
import { DialogoCategoria } from '../componentes/categorias'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { ordenarPorNombre, textoComparable } from '../util/formato'

/**
 * Administrar categorías: crear, renombrar y eliminar las que no tienen productos.
 * Se entra desde Productos (botón "Categorías" arriba a la derecha).
 */
export default function Categorias() {
  const [version, setVersion] = useState(0)
  const lista = useCarga(() => categorias.listar(), version)
  const [texto, setTexto] = useState('')
  // undefined = cerrado; null = nueva; una categoría = renombrar esa
  const [editando, setEditando] = useState<Categoria | null | undefined>(undefined)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const todas = ordenarPorNombre(lista.datos ?? [])
  const buscado = textoComparable(texto)
  const visibles = buscado === '' ? todas : todas.filter((c) => textoComparable(c.nombre).includes(buscado))
  const vacias = todas.filter((c) => c.productos === 0).length

  async function eliminar(categoria: Categoria) {
    if (!window.confirm(`¿Eliminar la categoría "${categoria.nombre}"? No tiene productos.`)) {
      return
    }
    setError(null)
    try {
      await categorias.eliminar(categoria.id)
      setMensaje(`Se eliminó la categoría "${categoria.nombre}".`)
      setVersion((v) => v + 1)
    } catch (e) {
      setError(mensajeDe(e))
    }
  }

  return (
    <Pantalla
      titulo="Categorías"
      volver="/productos"
      pie={
        <Boton ancho onClick={() => setEditando(null)}>
          + Nueva categoría
        </Boton>
      }
    >
      <div className="space-y-3">
        {mensaje && <Aviso tipo="exito">{mensaje}</Aviso>}
        {error && <Aviso tipo="error">{error}</Aviso>}
        <Buscador valor={texto} onCambio={setTexto} placeholder="Buscar categoría…" />

        <EstadoCarga cargando={lista.cargando} error={lista.error} hayDatos={lista.datos !== undefined}>
          <p className="text-sm text-slate-500">
            {todas.length} {todas.length === 1 ? 'categoría' : 'categorías'}
            {vacias > 0 && ` · ${vacias} sin productos (se pueden eliminar)`}
          </p>
          <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white">
            {visibles.map((c) => (
              <li key={c.id} className="flex items-center gap-2 py-1 pr-1 pl-3">
                <span className="min-w-0 flex-1">
                  <span className="block font-semibold leading-snug">{c.nombre}</span>
                  <span className={`block text-sm ${c.productos === 0 ? 'text-amber-700' : 'text-slate-500'}`}>
                    {c.productos === 0 ? 'Sin productos' : `${c.productos} ${c.productos === 1 ? 'producto' : 'productos'}`}
                  </span>
                </span>
                <Boton variante="texto" className="px-3" onClick={() => setEditando(c)} aria-label={`Renombrar ${c.nombre}`}>
                  ✏️ <span className="text-sm">Renombrar</span>
                </Boton>
                {c.productos === 0 && (
                  <Boton
                    variante="texto"
                    className="px-3 text-red-700"
                    onClick={() => void eliminar(c)}
                    aria-label={`Eliminar ${c.nombre}`}
                  >
                    🗑
                  </Boton>
                )}
              </li>
            ))}
          </ul>
          {visibles.length === 0 && <Vacio>No hay categorías que coincidan con “{texto.trim()}”.</Vacio>}
          <p className="text-sm text-slate-500">
            Solo se eliminan las categorías sin productos. Al renombrar, los productos siguen en su categoría y sus códigos
            no cambian.
          </p>
        </EstadoCarga>
      </div>

      {editando !== undefined && (
        <DialogoCategoria
          existentes={todas}
          categoria={editando ?? undefined}
          onCerrar={() => setEditando(undefined)}
          onListo={(c) => {
            setMensaje(editando ? `Categoría renombrada: "${c.nombre}".` : `Categoría creada: "${c.nombre}".`)
            setError(null)
            setEditando(undefined)
            setVersion((v) => v + 1)
          }}
        />
      )}
    </Pantalla>
  )
}
