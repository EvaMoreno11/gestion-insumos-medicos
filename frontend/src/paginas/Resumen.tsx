import { resumen } from '../api/recursos'
import { BotonEnlace, EstadoCarga } from '../componentes/basicos'
import { Pantalla } from '../componentes/Pantalla'
import { GraficoVentasMes, Indicador, SelectorPeriodo } from '../componentes/resumen'
import { useCarga, usePeriodo } from '../hooks'
import { dinero, rangoFechas } from '../util/formato'

/**
 * Resumen ejecutivo: ventas, ganancia y compras del periodo comparadas con el anterior,
 * y las ventas de los últimos 12 meses. Lo demás está en "Más detalles".
 */
export default function Resumen() {
  const [consulta, setConsulta, textoParams] = usePeriodo()
  const { datos, cargando, error } = useCarga(() => resumen.obtener(consulta), textoParams)

  return (
    <Pantalla titulo="Resumen" volver="/" ancho="amplio">
      <div className="space-y-3">
        <SelectorPeriodo key={textoParams} consulta={consulta} onCambio={setConsulta} />
        <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
          {datos && (
            <>
              <p className={`text-sm text-slate-600 ${cargando ? 'opacity-60' : ''}`}>
                {rangoFechas(datos.periodo.desde, datos.periodo.hasta)} · comparado con{' '}
                {rangoFechas(datos.periodo.anteriorDesde, datos.periodo.anteriorHasta)}
              </p>

              <div className={`space-y-3 lg:grid lg:grid-cols-3 lg:gap-4 lg:space-y-0 ${cargando ? 'opacity-60' : ''}`}>
                <Indicador
                  titulo="Ventas"
                  valor={dinero(datos.ventas.total)}
                  detalle={`${datos.ventas.pedidos} ${datos.ventas.pedidos === 1 ? 'pedido entregado' : 'pedidos entregados'}`}
                  actual={datos.ventas.total}
                  anterior={datos.ventas.totalAnterior}
                  nombreAnterior={datos.periodo.nombreAnterior}
                  subirEsBueno
                />
                <Indicador
                  titulo="Ganancia"
                  valor={dinero(datos.ganancia.ganancia)}
                  detalle={datos.ganancia.margen === null ? 'Sin ventas con costo' : `Margen ${datos.ganancia.margen} %`}
                  actual={datos.ganancia.ganancia}
                  anterior={datos.ganancia.gananciaAnterior}
                  nombreAnterior={datos.periodo.nombreAnterior}
                  subirEsBueno
                  nota={
                    datos.ganancia.ventasSinCosto > 0
                      ? `No incluye ${dinero(datos.ganancia.ventasSinCosto)} vendidos sin costo registrado (ítems escritos a mano o productos sin costo).`
                      : `Ventas ${dinero(datos.ganancia.ventasConCosto)} − costo ${dinero(datos.ganancia.costoVendido)}`
                  }
                />
                <Indicador
                  titulo="Compras"
                  valor={dinero(datos.compras.total)}
                  detalle={`${datos.compras.entradas} ${datos.compras.entradas === 1 ? 'entrada registrada' : 'entradas registradas'}`}
                  actual={datos.compras.total}
                  anterior={datos.compras.totalAnterior}
                  nombreAnterior={datos.periodo.nombreAnterior}
                  nota="No incluye el inventario inicial importado del Excel."
                />
              </div>

              <div className={cargando ? 'opacity-60' : ''}>
                <GraficoVentasMes key={textoParams} meses={datos.ventasPorMes} />
              </div>

              <BotonEnlace a={`/resumen/detalles?${textoParams}`} variante="secundario" ancho>
                Ver más detalles →
              </BotonEnlace>
              <p className="text-sm text-slate-500">
                Montos con IGV. Las ventas son los pedidos despachados (con nota o solo tabla).
              </p>
            </>
          )}
        </EstadoCarga>
      </div>
    </Pantalla>
  )
}
