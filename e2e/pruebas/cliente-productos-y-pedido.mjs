// Ficha del cliente: pestaña "Productos que compra" (último precio, fecha, cantidad, buscador y todas las ventas).
// Pedido en el celular: buscador después de la lista; al agregar, baja al producto nuevo y lo resalta.
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')
const igual = (real, esperado, que) => {
  if (real !== esperado) throw new Error(`${que}: «${real}» (esperaba «${esperado}»)`)
}

export default async function prueba(navegador) {
  const contexto = await navegador.newContext({ ...devices['iPhone 14'], locale: 'es-PE', timezoneId: 'America/Lima' })
  const p = await contexto.newPage()
  p.setDefaultTimeout(20000)
  const errores = []
  p.on('pageerror', (e) => errores.push(String(e)))
  const s = String(Date.now()).slice(-5)

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

  await entrar(p)

  const categoriaId = (await api('/api/categorias'))[0].id
  const producto = async (nombre, costo) => {
    const prod = await api('/api/productos', 'POST', { nombre, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja x 100', unidadMedida: 'Caja', costo })
    await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `C${s}${prod.id}`, cantidad: 500, costo })
    return prod
  }
  const guante = await producto(`GUANTE CLIENTE E2E${s}`, 10)
  const jeringa = await producto(`JERINGA CLIENTE E2E${s}`, 20)
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Productos E2E${s}`, porcentaje: 10, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const vender = async (items) => {
    const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
    let actual
    for (const [prod, cantidad, precio] of items) {
      actual = await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: prod.id, cantidad })
      if (precio) {
        const item = actual.items.find((i) => i.productoId === prod.id)
        await api(`/api/pedidos/${pedido.id}/items/${item.id}`, 'PUT', { cantidad, precio })
      }
    }
    return api(`/api/pedidos/${pedido.id}/despachar`, 'POST')
  }
  await vender([[guante, 5], [jeringa, 2]]) //  guante 5 × 11.00, jeringa 2 × 22.00
  const segundo = await vender([[guante, 20, 10.5]]) // guante 20 × 10.50 (más reciente)

  // ------------------------------------------------------------------ ficha del cliente
  await p.goto(`${BASE}/clientes/${cliente.id}`)
  await p.getByRole('heading', { name: `Botica Productos E2E${s}` }).waitFor()
  igual(await p.getByRole('tab', { name: 'Productos que compra' }).getAttribute('aria-selected'), 'true', 'pestaña inicial')
  await p.getByText('Este año').waitFor()
  const fila = (prod) => p.locator('li').filter({ hasText: prod.nombre }).first()
  await fila(guante).waitFor()
  const textoGuante = await fila(guante).innerText()
  for (const parte of ['S/ 10.50', 'Última venta:', '(hoy)', '20 cajas']) {
    if (!textoGuante.includes(parte)) throw new Error(`falta «${parte}» en: ${textoGuante}`)
  }
  const nombres = await p.locator('li').allInnerTexts()
  if (nombres.findIndex((t) => t.includes(guante.nombre)) > nombres.findIndex((t) => t.includes(jeringa.nombre))) {
    throw new Error('el guante (más reciente) debería ir antes que la jeringa')
  }
  if (!(await fila(jeringa).innerText()).includes('S/ 22.00')) throw new Error('jeringa: ' + (await fila(jeringa).innerText()))
  await p.screenshot({ path: path.join(DIR, 'cliente-productos.png') })
  ok('al entrar al cliente se abre "Productos que compra": guante S/ 10.50 · hoy · 20 cajas (primero) y jeringa S/ 22.00')

  await fila(guante).getByRole('button').click()
  await fila(guante).getByText('Se le vendió 2 veces').waitFor()
  const historial = await fila(guante).innerText()
  for (const parte of ['20 cajas × S/ 10.50', 'S/ 210.00', '5 cajas × S/ 11.00', 'S/ 55.00', `N° ${segundo.numero}`]) {
    if (!historial.includes(parte)) throw new Error(`falta «${parte}» en el historial: ${historial}`)
  }
  await p.screenshot({ path: path.join(DIR, 'cliente-productos-ventas.png') })
  ok('al tocar el guante se ven sus 2 ventas: "20 cajas × S/ 10.50 = S/ 210.00" y "5 cajas × S/ 11.00 = S/ 55.00"')

  await p.getByPlaceholder('Buscar producto…').fill(`jeringa cliente e2e${s}`)
  await p.getByText(/1 producto con «/).waitFor()
  igual(await p.locator('li').filter({ hasText: guante.nombre }).count(), 0, 'el guante con la búsqueda de jeringa')
  await fila(jeringa).getByRole('button').click()
  await fila(jeringa).getByRole('link').first().click()
  await p.waitForURL('**/pedidos/*')
  await p.goBack()
  await fila(jeringa).waitFor()
  igual(await p.getByPlaceholder('Buscar producto…').inputValue(), `jeringa cliente e2e${s}`, 'búsqueda al volver')
  ok('el buscador filtra; una venta abre su pedido y al volver sigue la búsqueda')

  await p.getByRole('tab', { name: 'Datos del cliente' }).click()
  await p.getByLabel('Nombre o razón social').waitFor()
  igual(await p.getByLabel('Nombre o razón social').inputValue(), `Botica Productos E2E${s}`, 'nombre en el formulario')
  ok('"Datos del cliente" muestra el formulario de siempre')

  // ------------------------------------------------------------------ pedido: buscador abajo y bajar al agregar
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  for (let i = 0; i < 5; i++) {
    const prod = await producto(`ITEM ${i + 1} PEDIDO E2E${s}`, 5)
    await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: prod.id, cantidad: 1 })
  }
  const nuevo = await producto(`NUEVO PEDIDO E2E${s}`, 5)
  await p.goto(`${BASE}/pedidos/${pedido.id}`)
  const buscador = p.getByPlaceholder('Agregar producto: jer 5ml, guantes m…')
  await buscador.waitFor()
  const yBuscador = (await buscador.boundingBox()).y
  const yUltimo = (await p.locator('li').filter({ hasText: `ITEM 5 PEDIDO E2E${s}` }).boundingBox()).y
  if (!(yBuscador > yUltimo)) throw new Error(`el buscador (${yBuscador}) debería ir después del último ítem (${yUltimo})`)
  ok('celular: el buscador "Agregar producto" va después del último producto')

  await buscador.scrollIntoViewIfNeeded()
  await buscador.fill(nuevo.nombre)
  await p.getByRole('button', { name: new RegExp(nuevo.nombre) }).click()
  const filaNueva = p.locator('li').filter({ hasText: nuevo.nombre })
  await filaNueva.getByText('Recién agregado').waitFor()
  await p.waitForTimeout(700) // el desplazamiento es suave
  const caja = await filaNueva.boundingBox()
  const alto = p.viewportSize().height
  if (caja.y < 0 || caja.y + 120 > alto) throw new Error(`el producto nuevo no quedó a la vista: y=${caja.y}`)
  await p.screenshot({ path: path.join(DIR, 'pedido-recien-agregado.png') })
  await filaNueva.getByText('Recién agregado').waitFor({ state: 'detached', timeout: 5000 })
  ok('al agregar, el producto nuevo queda a la vista, resaltado con "Recién agregado" (se quita solo)')

  const pc = await navegador.newContext({ viewport: { width: 1366, height: 768 }, locale: 'es-PE', storageState: await contexto.storageState() })
  const q = await pc.newPage()
  await q.goto(`${BASE}/pedidos/${pedido.id}`)
  const buscadorPc = q.getByPlaceholder('Agregar producto: jer 5ml, guantes m…')
  await buscadorPc.waitFor()
  const xBuscador = (await buscadorPc.boundingBox()).x
  const xItems = (await q.locator('li').filter({ hasText: `ITEM 1 PEDIDO E2E${s}` }).boundingBox()).x
  if (!(xBuscador < xItems)) throw new Error('en la PC el buscador debería seguir a la izquierda')
  await q.screenshot({ path: path.join(DIR, 'pedido-pc.png') })
  ok('PC: el buscador sigue en la columna izquierda, como antes')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
