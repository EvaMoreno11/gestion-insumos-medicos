import { useState } from 'react'
import { mensajeDe } from '../api/http'
import { categorias } from '../api/recursos'
import type { Categoria } from '../api/tipos'
import { textoComparable } from '../util/formato'
import { Aviso, Boton, Campo } from './basicos'

interface Props {
  /** Las que ya existen: para avisar si el nombre se repite antes de guardar */
  existentes: Categoria[]
  /** Si viene, se renombra esta categoría; si no, se crea una nueva */
  categoria?: Categoria
  onListo: (categoria: Categoria) => void
  /** Si el nombre ya existe, ofrece usar esa categoría (solo al crear desde el formulario de producto) */
  onUsarExistente?: (categoria: Categoria) => void
  onCerrar: () => void
}

/** Ventana para crear o renombrar una categoría. No deja repetir nombres, aunque cambien mayúsculas o tildes. */
export function DialogoCategoria({ existentes, categoria, onListo, onUsarExistente, onCerrar }: Props) {
  const [nombre, setNombre] = useState(categoria?.nombre ?? '')
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  const limpio = nombre.trim().replace(/\s+/g, ' ')
  const repetida = existentes.find(
    (c) => c.id !== categoria?.id && textoComparable(c.nombre) === textoComparable(limpio),
  )
  const sinCambios = categoria !== undefined && limpio === categoria.nombre
  const listo = limpio !== '' && limpio.length <= 80 && repetida === undefined && !sinCambios

  async function guardar() {
    setOcupado(true)
    setError(null)
    try {
      onListo(categoria ? await categorias.renombrar(categoria.id, limpio) : await categorias.crear(limpio))
    } catch (e) {
      setError(mensajeDe(e))
      setOcupado(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="titulo-categoria"
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          onCerrar()
        }
      }}
    >
      <form
        className="w-full max-w-md space-y-3 rounded-t-2xl bg-white p-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:rounded-2xl"
        onSubmit={(e) => {
          e.preventDefault()
          if (listo && !ocupado) {
            void guardar()
          }
        }}
      >
        <h2 id="titulo-categoria" className="text-lg font-bold">
          {categoria ? 'Renombrar categoría' : 'Nueva categoría'}
        </h2>
        {error && <Aviso tipo="error">{error}</Aviso>}
        <Campo
          etiqueta="Nombre"
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          maxLength={80}
          autoFocus
          autoComplete="off"
          ayuda={categoria ? 'Los productos siguen en esta categoría y sus códigos no cambian.' : undefined}
        />
        {repetida && (
          <Aviso tipo="alerta">
            <p>
              Ya existe la categoría <strong>{repetida.nombre}</strong>.
            </p>
            {onUsarExistente && (
              <Boton variante="texto" className="mt-1 -ml-4" onClick={() => onUsarExistente(repetida)}>
                Usar «{repetida.nombre}»
              </Boton>
            )}
          </Aviso>
        )}
        <div className="grid grid-cols-2 gap-3">
          <Boton variante="secundario" onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" disabled={!listo || ocupado}>
            {ocupado ? 'Guardando…' : categoria ? 'Guardar' : 'Crear'}
          </Boton>
        </div>
      </form>
    </div>
  )
}
