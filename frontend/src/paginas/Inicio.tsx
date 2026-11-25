import { Link } from 'react-router'
import { alertas, cobranzas, pedidos, proveedores } from '../api/recursos'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { useSesion } from '../sesion-contexto'

interface Opcion {
  a: string
  icono: string
  titulo: string
  aviso?: number
  /** El aviso va en rojo: algo ya se pasó de fecha */
  urgente?: boolean
  /** Qué cuenta el aviso, para los lectores de pantalla */
  textoAviso?: (cantidad: number) => string
}

export default function Inicio() {
  const { negocio } = useSesion()
  // Contadores para los avisos de los botones; si fallan, la pantalla igual funciona
  const pendientes = useCarga(() => pedidos.listar('BORRADOR', 1))
  const porComprar = useCarga(() => pedidos.porComprar())
  const stockBajo = useCarga(() => alertas.stockBajo())
  const porVencer = useCarga(() => alertas.porVencer())
  const vencidos = useCarga(() => alertas.vencidos())
  const cobros = useCarga(() => cobranzas.obtener())
  const deudas = useCarga(() => proveedores.resumen())
  const totalAlertas =
    stockBajo.datos && porVencer.datos && vencidos.datos
      ? stockBajo.datos.length + porVencer.datos.length + vencidos.datos.length
      : undefined

  const opciones: Opcion[] = [
    { a: '/inventario', icono: '🔎', titulo: 'Consultar inventario' },
    {
      a: '/pedidos',
      icono: '🧾',
      titulo: 'Pedidos',
      aviso: pendientes.datos?.totalElementos,
    },
    { a: '/entrada', icono: '📦', titulo: 'Registrar entrada' },
    {
      a: '/por-comprar',
      icono: '🛒',
      titulo: 'Por comprar',
      aviso: porComprar.datos ? porComprar.datos.productos.length + porComprar.datos.noRegistrados.length : undefined,
    },
    {
      a: '/cobranzas',
      icono: '💰',
      titulo: 'Cobranzas',
      aviso: cobros.datos?.clientesVencidos,
      urgente: true,
      textoAviso: (n) => `${n} ${n === 1 ? 'cliente' : 'clientes'} con cobros vencidos`,
    },
    {
      a: '/proveedores',
      icono: '🏭',
      titulo: 'Proveedores',
      aviso: deudas.datos?.deudasPorPagar,
      textoAviso: (n) => `${n} ${n === 1 ? 'deuda' : 'deudas'} por pagar`,
    },
    {
      a: '/alertas',
      icono: '🔔',
      titulo: 'Alertas',
      aviso: totalAlertas,
    },
    { a: '/resumen', icono: '📊', titulo: 'Resumen' },
    { a: '/clientes', icono: '👥', titulo: 'Clientes' },
    { a: '/productos', icono: '🏷️', titulo: 'Productos' },
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
            className="relative flex min-h-24 flex-col justify-between gap-2 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm active:bg-slate-50"
          >
            <span className="text-3xl" aria-hidden="true">
              {o.icono}
            </span>
            <span className="block font-semibold leading-tight text-slate-900">{o.titulo}</span>
            {o.aviso !== undefined && o.aviso > 0 && (
              <span
                className={`absolute right-3 top-3 flex h-7 min-w-7 items-center justify-center rounded-full px-2 text-sm font-bold text-white ${
                  o.urgente ? 'bg-red-600' : 'bg-amber-500'
                }`}
                aria-label={o.textoAviso?.(o.aviso)}
              >
                {o.aviso}
              </span>
            )}
          </Link>
        ))}
      </nav>
    </Pantalla>
  )
}
