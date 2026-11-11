import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { categoriasPrecio, clientes } from '../api/recursos'
import type { LocalDatos } from '../api/tipos'
import { Aviso, Boton, BotonEnlace, Buscador, Campo, Cargando, EstadoCarga, Etiqueta, Tarjeta, Vacio } from '../componentes/basicos'
import { SelectorCategoria } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga, useRetardo } from '../hooks'
import { porcentaje as textoPorcentaje } from '../util/formato'

// ------------------------------------------------------------------ lista

export function Clientes() {
  const [texto, setTexto] = useState('')
  const buscado = useRetardo(texto.trim())
  const { datos, cargando, error } = useCarga(() => clientes.buscar(buscado), buscado)

  return (
    <Pantalla
      titulo="Clientes"
      volver="/"
      pie={
        <BotonEnlace a="/clientes/nuevo" ancho>
          + Nuevo cliente
        </BotonEnlace>
      }
    >
      <div className="space-y-3">
        <Buscador valor={texto} onCambio={setTexto} placeholder="Buscar por nombre o RUC…" />
        <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
          <ul className="space-y-2">
            {datos?.contenido.map((c) => (
              <li key={c.id}>
                <Link
                  to={`/clientes/${c.id}`}
                  className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50"
                >
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-semibold">{c.nombre}</span>
                    {c.rucDni && <span className="block text-sm text-slate-500">{c.rucDni}</span>}
                  </span>
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
  const categorias = useCarga(() => categoriasPrecio.listar())

  const [cargado, setCargado] = useState(id === null)
  const [nombre, setNombre] = useState('')
  const [rucDni, setRucDni] = useState('')
  const [porcentaje, setPorcentaje] = useState<number | null>(null)
  const [observaciones, setObservaciones] = useState('')
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
      const datos = { nombre, rucDni, porcentaje, observaciones, locales }
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
    <Pantalla titulo={id === null ? 'Nuevo cliente' : 'Editar cliente'} volver="/clientes">
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
            <Campo etiqueta="Nombre o razón social" value={nombre} onChange={(e) => setNombre(e.target.value)} required />
            <Campo
              etiqueta="RUC o DNI"
              inputMode="numeric"
              value={rucDni}
              onChange={(e) => setRucDni(e.target.value.replace(/\D/g, '').slice(0, 11))}
              ayuda="Opcional: 11 dígitos (RUC) u 8 (DNI)"
            />
            <Campo etiqueta="Observaciones" value={observaciones} onChange={(e) => setObservaciones(e.target.value)} />
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
              {variosLocales && (
                <Campo
                  etiqueta="Nombre del local"
                  placeholder="Local 1, Sede Los Olivos…"
                  value={local.nombre}
                  onChange={(e) => cambiarLocal(i, 'nombre', e.target.value)}
                />
              )}
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
