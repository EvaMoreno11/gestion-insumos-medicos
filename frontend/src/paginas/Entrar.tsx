import { useState, type ReactNode } from 'react'
import { mensajeDe } from '../api/http'
import { sesion } from '../api/recursos'
import type { EstadoSesion } from '../api/tipos'
import { Aviso, Boton, Campo } from '../componentes/basicos'

/** Marco común de las pantallas de entrada: logo, nombre del negocio y una tarjeta centrada. */
function Marco({ negocio, subtitulo, children }: { negocio: string; subtitulo: string; children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col items-center bg-slate-100 px-4 pb-10 pt-[max(3rem,env(safe-area-inset-top))]">
      <img src="/icono-192.png" alt="" className="h-20 w-20 rounded-3xl shadow-md" />
      <h1 className="mt-4 text-center text-2xl font-bold text-marca">{negocio}</h1>
      <p className="mt-1 text-center text-slate-600">{subtitulo}</p>
      <div className="mt-6 w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">{children}</div>
    </div>
  )
}

/** Campo de contraseña con botón para mostrarla (útil al escribir en el celular). */
function CampoClave({
  etiqueta,
  valor,
  onCambio,
  autoComplete,
  ayuda,
}: {
  etiqueta: string
  valor: string
  onCambio: (valor: string) => void
  autoComplete: string
  ayuda?: string
}) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="relative">
      <Campo
        etiqueta={etiqueta}
        type={visible ? 'text' : 'password'}
        autoComplete={autoComplete}
        autoCapitalize="none"
        autoCorrect="off"
        value={valor}
        onChange={(e) => onCambio(e.target.value)}
        ayuda={ayuda}
        className="pr-20"
      />
      <button
        type="button"
        onClick={() => setVisible(!visible)}
        className="absolute right-1 top-7 h-11 rounded-lg px-3 text-sm font-semibold text-marca"
      >
        {visible ? 'Ocultar' : 'Mostrar'}
      </button>
    </div>
  )
}

// ------------------------------------------------------------------ iniciar sesión

export function Entrar({ negocio, onListo }: { negocio: string; onListo: (estado: EstadoSesion) => void }) {
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [recordar, setRecordar] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  async function entrar() {
    setOcupado(true)
    setError(null)
    try {
      onListo(await sesion.entrar(usuario, clave, recordar))
    } catch (e) {
      setError(mensajeDe(e))
      setClave('')
      setOcupado(false)
    }
  }

  return (
    <Marco negocio={negocio} subtitulo="Inicia sesión para continuar">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          void entrar()
        }}
      >
        {error && <Aviso tipo="error">{error}</Aviso>}
        <Campo
          etiqueta="Usuario"
          autoComplete="username"
          autoCapitalize="none"
          autoCorrect="off"
          value={usuario}
          onChange={(e) => setUsuario(e.target.value)}
          autoFocus
        />
        <CampoClave etiqueta="Contraseña" valor={clave} onCambio={setClave} autoComplete="current-password" />
        <label className="flex items-center gap-3">
          <input
            type="checkbox"
            className="h-5 w-5 accent-marca"
            checked={recordar}
            onChange={(e) => setRecordar(e.target.checked)}
          />
          <span>Recordarme en este dispositivo (30 días)</span>
        </label>
        <Boton type="submit" ancho disabled={ocupado || usuario.trim() === '' || clave === ''}>
          {ocupado ? 'Entrando…' : 'Entrar'}
        </Boton>
      </form>
    </Marco>
  )
}

// ------------------------------------------------------------------ primera cuenta

export function CrearCuenta({
  negocio,
  pideCodigo,
  onListo,
}: {
  negocio: string
  pideCodigo: boolean
  onListo: (estado: EstadoSesion) => void
}) {
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [repetir, setRepetir] = useState('')
  const [codigo, setCodigo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  const claveCorta = clave !== '' && clave.length < 8
  const noCoinciden = repetir !== '' && repetir !== clave
  const listo =
    usuario.trim().length >= 3 && clave.length >= 8 && repetir === clave && (!pideCodigo || codigo.trim() !== '')

  async function crear() {
    setOcupado(true)
    setError(null)
    try {
      await sesion.crearCuenta(usuario, clave, codigo)
      onListo(await sesion.entrar(usuario, clave, true))
    } catch (e) {
      setError(mensajeDe(e))
      setOcupado(false)
    }
  }

  return (
    <Marco negocio={negocio} subtitulo="Crea la cuenta para entrar a la app">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          void crear()
        }}
      >
        <Aviso>
          Es la primera vez que se abre la app. Elige un usuario y una contraseña: los usarán para entrar desde
          cualquier dispositivo.
        </Aviso>
        {error && <Aviso tipo="error">{error}</Aviso>}
        {pideCodigo && (
          <Campo
            etiqueta="Código de configuración"
            autoComplete="off"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value)}
            ayuda="Lo da quien instaló la app en el servidor"
          />
        )}
        <Campo
          etiqueta="Usuario"
          autoComplete="username"
          autoCapitalize="none"
          autoCorrect="off"
          value={usuario}
          onChange={(e) => setUsuario(e.target.value)}
          ayuda="Ej.: tienda. Sin espacios."
        />
        <CampoClave
          etiqueta="Contraseña"
          valor={clave}
          onCambio={setClave}
          autoComplete="new-password"
          ayuda={claveCorta ? 'Debe tener al menos 8 caracteres' : 'Al menos 8 caracteres'}
        />
        <CampoClave
          etiqueta="Repite la contraseña"
          valor={repetir}
          onCambio={setRepetir}
          autoComplete="new-password"
          ayuda={noCoinciden ? 'No coincide con la contraseña' : undefined}
        />
        <Boton type="submit" ancho disabled={ocupado || !listo}>
          {ocupado ? 'Creando…' : 'Crear cuenta y entrar'}
        </Boton>
      </form>
    </Marco>
  )
}
