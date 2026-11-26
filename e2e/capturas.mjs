// Capturas de pantalla para el README, SOLO con los datos de demostración (productos y clientes inventados).
//
// Necesita la app con el perfil dev sobre una base de datos NUEVA (vacía al arrancar): el perfil carga los
// 24 productos y 3 clientes de ejemplo. Este script agrega compras, pedidos de los últimos meses y pagos
// (para que el resumen y las cobranzas tengan algo que mostrar) y guarda las capturas en docs/capturas/.
// Si la base tiene cualquier otro dato, se detiene sin tocar nada.
//
//   BASE=http://localhost:8080 E2E_DB=postgresql://... node capturas.mjs
import path from 'node:path'
import { chromium, devices } from 'playwright-core'
import { BASE, CANAL, entrar, esperarApp, sql, cerrarSql } from './util.mjs'

const SALIDA = path.join(import.meta.dirname, '..', 'docs', 'capturas')
const CLIENTES_DEMO = ['Botica Demo S.A.C.', 'Clínica Ejemplo', 'Consultorio Dr. Pérez']

// --------------------------------------------------------------------------- 1. solo con datos de ejemplo
await esperarApp()
// La app ya responde mientras termina de cargar los datos de demostración (los 2 pedidos van al final)
const contar = async (tabla) => (await sql(`SELECT count(*)::int AS n FROM ${tabla}`))[0].n
for (let i = 0; i < 30 && (await contar('pedido')) < 2; i++) await new Promise((r) => setTimeout(r, 1000))
const clientes = await sql('SELECT nombre FROM cliente')
if (clientes.some((c) => !CLIENTES_DEMO.includes(c.nombre)) || (await contar('producto')) !== 24 || (await contar('pedido')) !== 2) {
  await cerrarSql()
  throw new Error('Esta base no es una base nueva con los datos de demostración: no se sacan capturas.')
}

const navegador = await chromium.launch({ channel: CANAL, headless: true })
const celular = await navegador.newContext({ ...devices['iPhone 14'], deviceScaleFactor: 2, locale: 'es-PE', timezoneId: 'America/Lima' })
const p = await celular.newPage()
p.setDefaultTimeout(20000)
await entrar(p, 'demo')

const api = (ruta, metodo = 'GET', cuerpo) =>
  p.evaluate(
    async ({ ruta, metodo, cuerpo }) => {
      const token = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1]
      const r = await fetch(ruta, {
        method: metodo,
        headers: { 'Content-Type': 'application/json', ...(token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}) },
        body: cuerpo ? JSON.stringify(cuerpo) : undefined,
      })
      const texto = await r.text()
      if (!r.ok) throw new Error(`${metodo} ${ruta}: ${r.status} ${texto}`)
      return texto ? JSON.parse(texto) : null
    },
    { ruta, metodo, cuerpo },
  )

// --------------------------------------------------------------------------- 2. datos de ejemplo
await api('/api/negocio', 'PUT', { nombre: 'Distribuidora Demo', direccion: 'Av. Principal 123, Lima', telefono: null })

// Los productos de demostración, en el mismo orden que DatosDemoLoader
const todos = (await api('/api/productos?buscar=&tamano=100')).contenido
const producto = (nombre, marca) => {
  const encontrado = todos.find((x) => x.nombre === nombre && x.marca === marca)
  if (!encontrado) throw new Error(`no está el producto de demostración ${nombre} (${marca})`)
  return encontrado
}
const P = {
  guantesS: producto('Guantes de látex para examen talla S', 'Marca B'),
  guantesM: producto('Guantes de látex para examen talla M', 'Marca B'),
  quirurgicos: producto('Guantes quirúrgicos estériles talla 7 1/2', 'Marca C'),
  gasa: producto('Gasa estéril 10 x 10 cm', 'Marca D'),
  algodon: producto('Algodón hidrófilo', 'Marca D'),
  esparadrapo: producto('Esparadrapo de tela 2.5 cm x 5 m', 'Marca B'),
  venoclisis: producto('Equipo de venoclisis macrogotero', 'Marca C'),
  mascarilla: producto('Mascarilla quirúrgica descartable 3 pliegues', 'Marca B'),
  alcohol: producto('Alcohol etílico 70°', 'Marca D'),
  jeringaB: producto('Jeringa descartable 5 ml con aguja 21G x 1 1/2', 'Marca B'),
  aguja: producto('Aguja hipodérmica 21G x 1 1/2', 'Marca A'),
}

// Compras de este mes (también llenan "Entradas registradas" y el historial)
const anio = new Date().getFullYear() + 2
for (const [prod, lote, cantidad] of [
  [P.guantesM, 'GM-5710', 40],
  [P.venoclisis, 'V-78001', 100],
  [P.esparadrapo, 'E25-460', 60],
  [P.mascarilla, 'M3-2301', 30],
]) {
  await api('/api/movimientos/entrada', 'POST', {
    productoId: prod.id, numeroLote: lote, fechaVencimiento: `${anio}-06-30`, cantidad, costo: prod.costo,
  })
}

const clienteDemo = {}
for (const nombre of CLIENTES_DEMO) {
  const { contenido } = await api(`/api/clientes?buscar=${encodeURIComponent(nombre)}&tamano=5`)
  const id = contenido.find((c) => c.nombre === nombre).id
  // Si tiene varios locales, el pedido va al primero
  clienteDemo[nombre] = { id, localId: (await api(`/api/clientes/${id}`)).locales[0]?.id ?? null }
}

// Pedidos despachados en los últimos 6 meses: [meses atrás, cliente, [producto, cantidad]..., pago]
const historia = [
  [5, 'Botica Demo S.A.C.', [[P.esparadrapo, 6], [P.venoclisis, 10], [P.mascarilla, 3]], 'pagado'],
  [4, 'Clínica Ejemplo', [[P.guantesM, 5], [P.venoclisis, 12], [P.gasa, 4]], 'pagado'],
  [4, 'Consultorio Dr. Pérez', [[P.alcohol, 2], [P.algodon, 3]], 'pagado'],
  [3, 'Botica Demo S.A.C.', [[P.guantesS, 4], [P.esparadrapo, 8], [P.mascarilla, 4], [P.venoclisis, 8]], 'pagado'],
  [2, 'Clínica Ejemplo', [[P.guantesM, 6], [P.gasa, 6], [P.quirurgicos, 2]], 'pagado'],
  [2, 'Botica Demo S.A.C.', [[P.venoclisis, 15], [P.jeringaB, 3]], 'pagado'],
  [1, 'Consultorio Dr. Pérez', [[P.alcohol, 3], [P.algodon, 4], [P.esparadrapo, 6]], 'a cuenta'],
  [1, 'Clínica Ejemplo', [[P.guantesM, 8], [P.mascarilla, 5], [P.venoclisis, 10]], 'debe'],
  [0, 'Botica Demo S.A.C.', [[P.guantesS, 5], [P.esparadrapo, 10], [P.venoclisis, 12], [P.aguja, 2]], 'debe'],
  [0, 'Clínica Ejemplo', [[P.guantesM, 6], [P.gasa, 5]], 'debe'],
]
let ultimoDespachado
for (const [indice, [meses, cliente, items, pago]] of historia.entries()) {
  const pedido = await api('/api/pedidos', 'POST', {
    clienteId: clienteDemo[cliente].id,
    localId: clienteDemo[cliente].localId,
    porcentaje: cliente === 'Consultorio Dr. Pérez' ? 20 : null,
    guardarPorcentajeEnCliente: cliente === 'Consultorio Dr. Pérez' ? true : null,
  })
  for (const [prod, cantidad] of items) {
    await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: prod.id, cantidad })
  }
  await api(`/api/pedidos/${pedido.id}/despachar`, 'POST')
  await api(`/api/pedidos/${pedido.id}/plazo`, 'PUT', { plazoDias: 30 })

  // Se lleva el despacho a su mes (los de este mes: hace unos días)
  const cuando = meses === 0 ? `now() - interval '${indice % 2 === 0 ? 3 : 0} days' - interval '2 hours'`
    : `date_trunc('month', now()) - interval '${meses} months' + interval '${8 + indice} days 15 hours'`
  await sql(`UPDATE pedido SET despachado_en = ${cuando}, creado_en = ${cuando} - interval '1 hour' WHERE id = $1`, [pedido.id])
  await sql(
    `UPDATE movimiento SET fecha = (SELECT despachado_en FROM pedido WHERE id = $1)
     WHERE pedido_item_id IN (SELECT id FROM pedido_item WHERE pedido_id = $1)`,
    [pedido.id],
  )

  if (pago !== 'debe') {
    const { saldo } = (await api(`/api/pedidos/${pedido.id}`)).cobro
    // Pagan unas semanas después del despacho; el pago "a cuenta" es de hoy
    const [{ fecha }] = await sql(
      `SELECT to_char(LEAST((despachado_en AT TIME ZONE 'America/Lima')::date + $2::int, current_date), 'YYYY-MM-DD') AS fecha
       FROM pedido WHERE id = $1`,
      [pedido.id, pago === 'pagado' ? 25 : 365],
    )
    const monto = pago === 'pagado' ? saldo : Math.round(saldo * 50) / 100
    await api(`/api/pedidos/${pedido.id}/pagos`, 'POST', { monto, fecha, medio: indice % 3 === 0 ? 'YAPE' : 'TRANSFERENCIA' })
  }
  ultimoDespachado = pedido.id
}

// El borrador de demostración de la botica (con un producto agotado y un ítem escrito a mano)
const borrador = (await api('/api/pedidos?estado=BORRADOR&tamano=10')).contenido.find((x) => x.cliente === 'Botica Demo S.A.C.')

// --------------------------------------------------------------------------- 3. capturas
const foto = async (pagina, nombre) => {
  await pagina.waitForLoadState('networkidle')
  await pagina.waitForTimeout(600) // animaciones y gráficos
  await pagina.screenshot({ path: path.join(SALIDA, `${nombre}.png`) })
  console.log('  ✓', nombre)
}

await p.goto(BASE + '/')
await foto(p, 'inicio')

await p.goto(BASE + '/inventario')
await foto(p, 'inventario')

await p.goto(`${BASE}/inventario/${P.guantesM.id}`)
await p.getByRole('heading', { name: 'Historial', exact: true }).waitFor()
await foto(p, 'producto')

await p.goto(`${BASE}/pedidos/${borrador.id}`)
await foto(p, 'pedido')

await p.goto(`${BASE}/pedidos/${ultimoDespachado}`)
await p.getByRole('button', { name: 'PDF', exact: true }).click()
await p.getByRole('dialog').locator('canvas').first().waitFor()
await foto(p, 'nota-pdf')

await p.goto(BASE + '/cobranzas')
await foto(p, 'cobranzas')

await p.goto(BASE + '/resumen')
await foto(p, 'resumen')

await p.goto(BASE + '/alertas')
await foto(p, 'alertas')

const pc = await (await navegador.newContext({
  viewport: { width: 1366, height: 940 }, locale: 'es-PE', timezoneId: 'America/Lima', storageState: await celular.storageState(),
})).newPage()
await pc.goto(BASE + '/resumen')
await pc.getByText('Ver más detalles').waitFor()
// La ventana tan alta como la página, para que entre el gráfico y la barra lateral llegue hasta abajo
await pc.setViewportSize({ width: 1366, height: await pc.evaluate(() => document.documentElement.scrollHeight) })
await foto(pc, 'pc-resumen')
await pc.setViewportSize({ width: 1366, height: 940 })
await pc.goto(`${BASE}/pedidos/${borrador.id}`)
await foto(pc, 'pc-pedido')

await navegador.close()
await cerrarSql()
console.log(`\nCapturas en ${SALIDA}`)
