// Ganancia por cliente: Mejores clientes (ordenar por ganancia, ver todos) y la ficha del cliente.
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')

export default async function prueba(navegador) {
  const contexto = await navegador.newContext({ ...devices['iPhone 14'], locale: 'es-PE' })
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
  const producto = await api('/api/productos', 'POST', {
    nombre: `GUANTE GANANCIA E2E${s}`, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja', unidadMedida: 'Caja', costo: 10,
  })
  await api('/api/movimientos/entrada', 'POST', { productoId: producto.id, numeroLote: `G${s}`, fechaVencimiento: '2029-01-01', cantidad: 100, costo: 10 })
  const vender = async (nombre, cantidad, precio) => {
    const cliente = await api('/api/clientes', 'POST', { nombre, porcentaje: 15, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
    const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
    const conItem = await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: producto.id, cantidad })
    await api(`/api/pedidos/${pedido.id}/items/${conItem.items[0].id}`, 'PUT', { cantidad, precio })
    await api(`/api/pedidos/${pedido.id}/despachar`, 'POST')
    return cliente
  }
  // Mucho con poco margen vs. poco con mucho margen
  const grande = await vender(`Botica Grande E2E${s}`, 20, 10.5) // 210, costó 200: gana 10 (4.8 %)
  const chica = await vender(`Botica Chica E2E${s}`, 5, 15) //       75, costó 50: gana 25 (33.3 %)

  await p.goto(`${BASE}/resumen/detalles`)
  const tarjeta = p.getByRole('heading', { name: 'Mejores clientes' }).locator('xpath=ancestor::div[contains(@class, "rounded-2xl")][1]')
  await tarjeta.getByText(`Botica Grande E2E${s}`).waitFor()
  const orden = async () => {
    const nombres = await tarjeta.getByRole('listitem').allInnerTexts()
    return [nombres.findIndex((t) => t.includes(`Botica Grande E2E${s}`)), nombres.findIndex((t) => t.includes(`Botica Chica E2E${s}`))]
  }
  if (await tarjeta.getByText(`Botica Chica E2E${s}`).count() === 0) {
    await tarjeta.getByRole('button', { name: /Ver todos los clientes/ }).click()
  }
  let [g, c] = await orden()
  if (!(g >= 0 && c >= 0 && g < c)) throw new Error(`por ventas, la grande va antes que la chica (${g}, ${c})`)
  const filaChica = await tarjeta.getByRole('listitem').filter({ hasText: `Botica Chica E2E${s}` }).innerText()
  if (!filaChica.includes('gana S/ 25.00 · 33.3 %')) throw new Error('fila: ' + filaChica)
  ok(`por ventas: la grande (S/ 210) antes que la chica; la chica muestra "gana S/ 25.00 · 33.3 %"`)

  await tarjeta.getByRole('radio', { name: 'Ganancia' }).click()
  ;[g, c] = await orden()
  if (!(c < g)) throw new Error(`por ganancia, la chica va antes (${g}, ${c})`)
  await p.screenshot({ path: path.join(DIR, 'mejores-clientes-ganancia.png'), fullPage: true })
  ok('Ordenar por Ganancia: la chica (gana S/ 25) pasa antes que la grande (gana S/ 10)')

  const total = (await api('/api/resumen/detalles')).mejoresClientes.length
  if (total > 10) {
    const visibles = await tarjeta.getByRole('listitem').count()
    ok(`"Ver todos" muestra los ${visibles} clientes del periodo (de ${total})`)
  }

  await p.goto(`${BASE}/clientes/${chica.id}`)
  await p.getByText('Este año').waitFor()
  await p.getByText('Compró S/ 75.00').waitFor()
  await p.getByText('gana S/ 25.00 · 33.3 %').waitFor()
  await p.getByText(/Último pedido hoy/).waitFor()
  await p.screenshot({ path: path.join(DIR, 'ficha-cliente-ganancia.png') })
  ok('ficha del cliente: "Este año · Compró S/ 75.00 · 1 pedido · gana S/ 25.00 · 33.3 % · Último pedido hoy"')

  const nuevo = await api('/api/clientes', 'POST', { nombre: `Clínica Sin Compras E2E${s}`, porcentaje: 15 })
  await p.goto(`${BASE}/clientes/${nuevo.id}`)
  await p.getByText('Este año todavía no tiene pedidos despachados').waitFor()
  ok('un cliente sin compras dice "Este año todavía no tiene pedidos despachados"')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
