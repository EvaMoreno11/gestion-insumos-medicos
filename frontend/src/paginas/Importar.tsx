import { useState } from 'react'
import { mensajeDe } from '../api/http'
import { importacion } from '../api/recursos'
import type { Observacion, ResultadoImportacion, ResultadoImportacionClientes } from '../api/tipos'
import { Aviso, Boton, Tarjeta } from '../componentes/basicos'
import { Pantalla } from '../componentes/Pantalla'

type Tipo = 'inventario' | 'clientes'
type Resultado = ResultadoImportacion | ResultadoImportacionClientes

const esInventario = (r: Resultado): r is ResultadoImportacion => 'productosCreados' in r

/**
 * Carga inicial desde las plantillas de Excel: primero se valida (no guarda nada y muestra los errores por
 * fila) y, si no hay errores, se importa. Todo o nada: si una fila tiene error, no se guarda ninguna.
 */
export default function Importar() {
  const [tipo, setTipo] = useState<Tipo>('inventario')
  const [archivo, setArchivo] = useState<File | null>(null)
  const [validacion, setValidacion] = useState<Resultado | null>(null)
  const [importado, setImportado] = useState<Resultado | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  function reiniciar() {
    setValidacion(null)
    setImportado(null)
    setError(null)
  }

  async function enviar(soloValidar: boolean) {
    if (!archivo) {
      return
    }
    setOcupado(true)
    setError(null)
    try {
      const resultado =
        tipo === 'inventario'
          ? await importacion.inventario(archivo, soloValidar)
          : await importacion.clientes(archivo, soloValidar)
      if (soloValidar) {
        setValidacion(resultado)
      } else {
        setImportado(resultado)
      }
    } catch (e) {
      setError(mensajeDe(e))
    } finally {
      setOcupado(false)
    }
  }

  const sinErrores = validacion !== null && validacion.errores.length === 0

  return (
    <Pantalla titulo="Importar desde Excel" volver="/configuracion">
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-1 rounded-xl bg-slate-200 p-1" role="tablist" aria-label="Qué importar">
          {(['inventario', 'clientes'] as Tipo[]).map((t) => (
            <button
              key={t}
              type="button"
              role="tab"
              aria-selected={tipo === t}
              onClick={() => {
                setTipo(t)
                setArchivo(null)
                reiniciar()
              }}
              className={`min-h-10 rounded-lg text-sm font-semibold ${tipo === t ? 'bg-white text-marca shadow-sm' : 'text-slate-600'}`}
            >
              {t === 'inventario' ? 'Inventario' : 'Clientes'}
            </button>
          ))}
        </div>

        <Aviso tipo="alerta">
          {tipo === 'inventario'
            ? 'Importa el inventario una sola vez: si importas el mismo archivo otra vez, las cantidades se suman de nuevo.'
            : 'Los clientes que ya están registrados (mismo nombre o RUC) no se modifican.'}
        </Aviso>
        {error && <Aviso tipo="error">{error}</Aviso>}

        <Tarjeta className="space-y-3">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Archivo de {tipo === 'inventario' ? 'inventario' : 'clientes'} (.xlsx)
            </span>
            <input
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={(e) => {
                setArchivo(e.target.files?.[0] ?? null)
                reiniciar()
              }}
              className="block w-full text-sm file:mr-3 file:min-h-11 file:rounded-xl file:border-0 file:bg-marca-claro file:px-4 file:font-semibold file:text-marca"
            />
          </label>
          <Boton ancho variante="secundario" disabled={!archivo || ocupado || importado !== null} onClick={() => void enviar(true)}>
            {ocupado && !validacion ? 'Revisando…' : '1. Validar (no guarda nada)'}
          </Boton>
          {ocupado && <EsperaLarga />}
        </Tarjeta>

        {validacion && !importado && (
          <Tarjeta className="space-y-3">
            <h2 className="font-semibold">{sinErrores ? 'Listo para importar' : 'Hay que corregir el archivo'}</h2>
            <Resumen resultado={validacion} futuro />
            <Observaciones titulo="Errores" lista={validacion.errores} tipo="error" />
            <Observaciones titulo="Avisos" lista={validacion.advertencias} tipo="alerta" />
            {sinErrores ? (
              <Boton
                ancho
                disabled={ocupado}
                onClick={() => {
                  if (window.confirm('¿Importar ahora? Los datos se guardarán en la app.')) {
                    void enviar(false)
                  }
                }}
              >
                {ocupado ? 'Importando…' : '2. Importar'}
              </Boton>
            ) : (
              <p className="text-sm text-slate-600">Corrige esas filas en el Excel, guárdalo y vuelve a elegirlo.</p>
            )}
          </Tarjeta>
        )}

        {importado && (
          <Aviso tipo="exito">
            <p className="font-semibold">Importación terminada.</p>
            <Resumen resultado={importado} />
          </Aviso>
        )}
      </div>
    </Pantalla>
  )
}

/** En el servidor gratuito, un inventario de cientos de filas tarda unos minutos en revisarse. */
function EsperaLarga() {
  return (
    <p className="text-sm text-slate-600" role="status">
      Puede tardar unos minutos. No cierres ni recargues esta página hasta que aparezca el resultado.
    </p>
  )
}

function Resumen({ resultado, futuro }: { resultado: Resultado; futuro?: boolean }) {
  const filas: [string, number][] = esInventario(resultado)
    ? [
        ['Filas leídas', resultado.filasLeidas],
        [futuro ? 'Productos nuevos' : 'Productos creados', resultado.productosCreados],
        ['Productos que ya existían', resultado.productosExistentes],
        [futuro ? 'Categorías nuevas' : 'Categorías creadas', resultado.categoriasCreadas],
        [futuro ? 'Lotes a ingresar' : 'Lotes ingresados', resultado.entradasRegistradas],
        ['Unidades', resultado.unidadesIngresadas],
      ]
    : [
        ['Filas leídas', resultado.filasLeidas],
        [futuro ? 'Clientes nuevos' : 'Clientes creados', resultado.clientesCreados],
        [futuro ? 'Locales' : 'Locales creados', resultado.localesCreados],
        ['Clientes que ya existían', resultado.clientesExistentes],
      ]
  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
      {filas.map(([etiqueta, valor]) => (
        <div key={etiqueta} className="contents">
          <dt className="text-slate-600">{etiqueta}</dt>
          <dd className="text-right font-semibold tabular-nums">{valor.toLocaleString('en-US')}</dd>
        </div>
      ))}
    </dl>
  )
}

function Observaciones({ titulo, lista, tipo }: { titulo: string; lista: Observacion[]; tipo: 'error' | 'alerta' }) {
  if (lista.length === 0) {
    return null
  }
  return (
    <div>
      <h3 className={`text-sm font-semibold ${tipo === 'error' ? 'text-red-700' : 'text-amber-800'}`}>
        {titulo} ({lista.length})
      </h3>
      <ul className="mt-1 max-h-80 space-y-1 overflow-y-auto text-sm">
        {lista.map((o, i) => (
          <li key={i} className={tipo === 'error' ? 'text-red-800' : 'text-amber-900'}>
            {o.fila > 0 ? <strong>Fila {o.fila}: </strong> : null}
            {o.mensaje}
          </li>
        ))}
      </ul>
    </div>
  )
}
