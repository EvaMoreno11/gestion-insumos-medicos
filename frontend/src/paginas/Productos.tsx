import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { categorias, productos } from '../api/recursos'
import { Aviso, Boton, BotonEnlace, Buscador, Campo, Cargando, ESTILO_CAMPO, EstadoCarga, Etiqueta, Tarjeta, Vacio } from '../componentes/basicos'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga, useRetardo } from '../hooks'
import { dinero, leerDecimal, leerEntero, unir } from '../util/formato'

// ------------------------------------------------------------------ lista

export function Productos() {
  const [texto, setTexto] = useState('')
  const buscado = useRetardo(texto.trim())
  const { datos, cargando, error } = useCarga(() => productos.buscar(buscado, { tamano: 50 }), buscado)

  return (
    <Pantalla
      titulo="Productos"
      volver="/"
      ancho="amplio"
      pie={
        <BotonEnlace a="/productos/nuevo" ancho>
          + Nuevo producto
        </BotonEnlace>
      }
    >
      <div className="space-y-3">
        <Buscador valor={texto} onCambio={setTexto} placeholder="Buscar producto…" />
        <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
          {datos && datos.totalElementos > datos.contenido.length && (
            <p className="text-sm text-slate-500">
              Se muestran {datos.contenido.length} de {datos.totalElementos}. Escribe para afinar la búsqueda.
            </p>
          )}
          <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
            {datos?.contenido.map((p) => (
              <li key={p.id}>
                <Link
                  to={`/productos/${p.id}`}
                  className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50"
                >
                  <span className="min-w-0 flex-1">
                    <span className="block font-semibold leading-snug">{p.nombre}</span>
                    <span className="block text-sm text-slate-500">{unir(' · ', p.presentacion, p.marca)}</span>
                  </span>
                  {p.costo === null ? (
                    <Etiqueta color="ambar">Sin costo</Etiqueta>
                  ) : (
                    <span className="shrink-0 text-sm text-slate-600">Costo {dinero(p.costo)}</span>
                  )}
                </Link>
              </li>
            ))}
          </ul>
          {datos?.contenido.length === 0 && (
            <Vacio>{buscado ? `No hay productos que coincidan con “${buscado}”.` : 'Aún no hay productos.'}</Vacio>
          )}
        </EstadoCarga>
      </div>
    </Pantalla>
  )
}

// ------------------------------------------------------------------ formulario

const UNIDADES = ['Caja', 'Paquete', 'Unidad', 'Docena', 'Rollo', 'Frasco', 'Bolsa', 'Par', 'Sobre', 'Display']

export function ProductoFormulario() {
  const parametro = useParams().id
  const id = parametro ? Number(parametro) : null
  const navegar = useNavigate()
  const listaCategorias = useCarga(() => categorias.listar())

  const [cargado, setCargado] = useState(id === null)
  const [codigo, setCodigo] = useState('')
  const [nombre, setNombre] = useState('')
  const [marca, setMarca] = useState('')
  const [categoriaId, setCategoriaId] = useState('')
  const [presentacion, setPresentacion] = useState('')
  const [unidadMedida, setUnidadMedida] = useState('Caja')
  const [stockMinimo, setStockMinimo] = useState('')
  const [costo, setCosto] = useState('')
  // Se conservan tal cual: esta pantalla básica no los edita, pero al guardar no deben perderse
  const [codigosBarras, setCodigosBarras] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    if (id === null) {
      return
    }
    productos
      .obtener(id)
      .then((p) => {
        setCodigo(p.codigo)
        setNombre(p.nombre)
        setMarca(p.marca)
        setCategoriaId(String(p.categoriaId))
        setPresentacion(p.presentacion)
        setUnidadMedida(p.unidadMedida)
        setStockMinimo(p.stockMinimo === 0 ? '' : String(p.stockMinimo))
        setCosto(p.costo === null ? '' : String(p.costo))
        setCodigosBarras(p.codigosBarras)
        setCargado(true)
      })
      .catch((e: unknown) => setError(mensajeDe(e)))
  }, [id])

  const costoValido = costo.trim() === '' ? null : leerDecimal(costo)
  const costoMalEscrito = costo.trim() !== '' && costoValido === null
  const listo = nombre.trim() !== '' && marca.trim() !== '' && categoriaId !== '' && presentacion.trim() !== '' && !costoMalEscrito

  async function guardar() {
    setOcupado(true)
    setError(null)
    try {
      const datos = {
        codigo,
        nombre,
        marca,
        categoriaId: Number(categoriaId),
        presentacion,
        unidadMedida,
        stockMinimo: leerEntero(stockMinimo) ?? 0,
        costo: costoValido,
        codigosBarras,
      }
      if (id === null) {
        await productos.crear(datos)
      } else {
        await productos.actualizar(id, datos)
      }
      navegar(-1)
    } catch (e) {
      setError(mensajeDe(e))
      setOcupado(false)
    }
  }

  const unidades = UNIDADES.includes(unidadMedida) ? UNIDADES : [unidadMedida, ...UNIDADES]

  return (
    <Pantalla titulo={id === null ? 'Nuevo producto' : 'Editar producto'} volver="/productos">
      {!cargado ? (
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
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">Categoría</span>
              <select className={ESTILO_CAMPO} value={categoriaId} onChange={(e) => setCategoriaId(e.target.value)} required>
                <option value="">Elige una categoría</option>
                {listaCategorias.datos?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nombre}
                  </option>
                ))}
              </select>
            </label>
            <Campo
              etiqueta="Nombre del producto"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              ayuda="Tipo y medidas, sin la marca. Ej.: Jeringa descartable 5 ml con aguja 21G"
              required
            />
            <Campo etiqueta="Marca o laboratorio" value={marca} onChange={(e) => setMarca(e.target.value)} required />
            <div className="grid grid-cols-2 gap-3">
              <Campo
                etiqueta="Presentación"
                placeholder="Caja x 100"
                value={presentacion}
                onChange={(e) => setPresentacion(e.target.value)}
                required
              />
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">Se cuenta por</span>
                <select className={ESTILO_CAMPO} value={unidadMedida} onChange={(e) => setUnidadMedida(e.target.value)}>
                  {unidades.map((u) => (
                    <option key={u}>{u}</option>
                  ))}
                </select>
              </label>
            </div>
          </Tarjeta>

          <Tarjeta className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <Campo
                etiqueta="Costo"
                inputMode="decimal"
                placeholder="0.00"
                value={costo}
                onChange={(e) => setCosto(e.target.value.replace(/[^\d.,]/g, ''))}
                error={costoMalEscrito ? 'Escribe un número, p. ej. 8.50' : undefined}
              />
              <Campo
                etiqueta="Stock mínimo"
                inputMode="numeric"
                placeholder="0"
                value={stockMinimo}
                onChange={(e) => setStockMinimo(e.target.value.replace(/\D/g, ''))}
              />
            </div>
            <p className="text-sm text-slate-500">
              El precio de venta se calcula solo: costo más el porcentaje de cada cliente. Con el stock mínimo la app
              avisa cuando queda poco.
            </p>
            {id !== null && <p className="text-sm text-slate-500">Código interno: {codigo}</p>}
          </Tarjeta>

          {id === null && (
            <Aviso>El producto se crea sin unidades. Para ingresar stock, usa “Registrar entrada”.</Aviso>
          )}

          <Boton type="submit" ancho disabled={ocupado || !listo}>
            {ocupado ? 'Guardando…' : 'Guardar producto'}
          </Boton>
        </form>
      )}
    </Pantalla>
  )
}
