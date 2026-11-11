import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { categorias, productos } from '../api/recursos'
import type { ProductoResumen } from '../api/tipos'
import { Aviso, Boton, Buscador, Cargando, ESTILO_CAMPO, Vacio } from '../componentes/basicos'
import { EtiquetaStock, ResumenStock } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga, useRetardo } from '../hooks'
import { unir } from '../util/formato'

/** Productos adicionales cargados con "Ver más" para una búsqueda concreta. */
interface Extra {
  clave: string
  productos: ProductoResumen[]
  pagina: number
}

/**
 * Buscador "¿tengo esto?". La búsqueda vive en la URL (?buscar=jer+5ml): al volver
 * del detalle de un producto, la lista sigue donde estaba.
 */
export default function Inventario() {
  const [params, setParams] = useSearchParams()
  const [texto, setTexto] = useState(params.get('buscar') ?? '')
  const categoriaId = params.get('categoria') ? Number(params.get('categoria')) : null
  const buscado = useRetardo(texto.trim())
  const clave = `${buscado}|${categoriaId ?? ''}`

  const primera = useCarga(() => productos.buscar(buscado, { categoriaId, pagina: 0 }), clave)
  const listaCategorias = useCarga(() => categorias.listar())

  const [extra, setExtra] = useState<Extra>()
  const [cargandoMas, setCargandoMas] = useState(false)
  const [errorMas, setErrorMas] = useState<string | null>(null)

  // Refleja la búsqueda en la URL
  useEffect(() => {
    setParams(
      (actual) => {
        const nuevos = new URLSearchParams(actual)
        if (buscado) {
          nuevos.set('buscar', buscado)
        } else {
          nuevos.delete('buscar')
        }
        return nuevos
      },
      { replace: true },
    )
  }, [buscado, setParams])

  // Lo cargado con "Ver más" solo vale para la búsqueda en la que se pidió
  const mas = extra?.clave === clave && !primera.cargando ? extra : undefined
  const lista = [...(primera.datos?.contenido ?? []), ...(mas?.productos ?? [])]
  const total = primera.datos?.totalElementos ?? 0
  const error = primera.error ?? errorMas

  async function verMas() {
    setCargandoMas(true)
    setErrorMas(null)
    try {
      const pagina = (mas?.pagina ?? 0) + 1
      const r = await productos.buscar(buscado, { categoriaId, pagina })
      setExtra({ clave, productos: [...(mas?.productos ?? []), ...r.contenido], pagina })
    } catch (e) {
      setErrorMas(mensajeDe(e))
    } finally {
      setCargandoMas(false)
    }
  }

  function elegirCategoria(valor: string) {
    setParams(
      (actual) => {
        const nuevos = new URLSearchParams(actual)
        if (valor) {
          nuevos.set('categoria', valor)
        } else {
          nuevos.delete('categoria')
        }
        return nuevos
      },
      { replace: true },
    )
  }

  return (
    <Pantalla titulo="Inventario" volver="/">
      <div className="space-y-3">
        <Buscador valor={texto} onCambio={setTexto} placeholder="Buscar: jer 5ml, guantes m…" autoFocus />
        <select
          aria-label="Categoría"
          className={ESTILO_CAMPO}
          value={categoriaId ?? ''}
          onChange={(e) => elegirCategoria(e.target.value)}
        >
          <option value="">Todas las categorías</option>
          {listaCategorias.datos?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nombre}
            </option>
          ))}
        </select>

        {error && <Aviso tipo="error">{error}</Aviso>}

        {!primera.error && (
          <p className="text-sm text-slate-500">
            {primera.cargando ? 'Buscando…' : `${total} ${total === 1 ? 'producto' : 'productos'}`}
          </p>
        )}

        <ul className="space-y-2">
          {lista.map((p) => (
            <li key={p.id}>
              <Link
                to={`/inventario/${p.id}`}
                className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50"
              >
                <span className="min-w-0 flex-1">
                  <span className="block font-semibold leading-snug">{p.nombre}</span>
                  <span className="block text-sm text-slate-500">{unir(' · ', p.presentacion, p.marca)}</span>
                  {p.stockSeparado > 0 && (
                    <span className="mt-1 block text-sm text-slate-700">
                      <ResumenStock producto={p} />
                    </span>
                  )}
                </span>
                <span className="flex shrink-0 flex-col items-end gap-1">
                  <span className="text-2xl font-bold leading-none">{p.stockDisponible}</span>
                  <EtiquetaStock estado={p.estadoStock} />
                </span>
              </Link>
            </li>
          ))}
        </ul>

        {!primera.cargando && !primera.error && lista.length === 0 && (
          <Vacio>{buscado ? `No hay productos que coincidan con “${buscado}”.` : 'Aún no hay productos registrados.'}</Vacio>
        )}
        {cargandoMas && <Cargando />}
        {!primera.cargando && !cargandoMas && lista.length < total && (
          <Boton variante="secundario" ancho onClick={verMas}>
            Ver más
          </Boton>
        )}
      </div>
    </Pantalla>
  )
}
