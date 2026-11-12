import { Link } from 'react-router'
import { pedidos } from '../api/recursos'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { useSesion } from '../sesion-contexto'

interface Opcion {
  a: string
  icono: string
  titulo: string
  detalle: string
  aviso?: number
}

export default function Inicio() {
  const { negocio } = useSesion()
  // Contadores para los avisos de los botones; si fallan, la pantalla igual funciona
  const pendientes = useCarga(() => pedidos.listar('BORRADOR', 1))
  const porComprar = useCarga(() => pedidos.porComprar())

  const opciones: Opcion[] = [
    { a: '/inventario', icono: '🔎', titulo: 'Consultar inventario', detalle: '¿Tengo este producto?' },
    {
      a: '/pedidos',
      icono: '🧾',
      titulo: 'Pedidos',
      detalle: 'Registrar y despachar',
      aviso: pendientes.datos?.totalElementos,
    },
    { a: '/entrada', icono: '📦', titulo: 'Registrar entrada', detalle: 'Mercadería que llega' },
    {
      a: '/por-comprar',
      icono: '🛒',
      titulo: 'Por comprar',
      detalle: 'Lo que falta para los pedidos',
      aviso: porComprar.datos ? porComprar.datos.productos.length + porComprar.datos.noRegistrados.length : undefined,
    },
    { a: '/clientes', icono: '👥', titulo: 'Clientes', detalle: 'Agregar y editar' },
    { a: '/productos', icono: '🏷️', titulo: 'Productos', detalle: 'Agregar y editar' },
  ]

  return (
    <Pantalla
      titulo={negocio}
      ancho="amplio"
      accion={
        <Link
          to="/configuracion"
          aria-label="Configuración"
          className="flex h-11 w-11 items-center justify-center rounded-full text-xl active:bg-white/15"
        >
          ⚙️
        </Link>
      }
    >
      <nav className="grid grid-cols-2 gap-3 lg:grid-cols-3 lg:gap-4">
        {opciones.map((o) => (
          <Link
            key={o.a}
            to={o.a}
            className="relative flex min-h-32 flex-col justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm active:bg-slate-50"
          >
            <span className="text-3xl" aria-hidden="true">
              {o.icono}
            </span>
            <span>
              <span className="block font-semibold leading-tight text-slate-900">{o.titulo}</span>
              <span className="mt-0.5 block text-sm leading-tight text-slate-500">{o.detalle}</span>
            </span>
            {o.aviso !== undefined && o.aviso > 0 && (
              <span className="absolute right-3 top-3 flex h-7 min-w-7 items-center justify-center rounded-full bg-amber-500 px-2 text-sm font-bold text-white">
                {o.aviso}
              </span>
            )}
          </Link>
        ))}
      </nav>
    </Pantalla>
  )
}
