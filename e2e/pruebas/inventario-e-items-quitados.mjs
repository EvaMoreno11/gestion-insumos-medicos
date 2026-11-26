// Inventario: "🛒 Agregar" chico (celular y PC), barra lateral más angosta, y los ítems quitados en una corrección
// que ya no se ven en el pedido ni en Corregir (y vuelven si se agrega otra vez ese producto).
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
  p.on('dialog', (d) => d.accept())
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
  const producto = async (nombre) => {
    const prod = await api('/api/productos', 'POST', {
      nombre, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja x 100', unidadMedida: 'Caja', costo: 10,
    })
    await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `Q${s}${prod.id}`, cantidad: 50, costo: 10 })
    return prod
  }
  const gasa = await producto(`GASA INVENT E2E${s}`)
  const venda = await producto(`VENDA INVENT E2E${s}`)
  await api(`/api/pedidos/por-comprar/a-mano/${venda.id}`, 'PUT', { cantidad: 5 })

  // ------------------------------------------------------------------ inventario en el celular
  await p.goto(`${BASE}/inventario?buscar=invent%20e2e${s}`)
  const tarjeta = (prod) => p.locator('li').filter({ hasText: prod.nombre })
  await tarjeta(gasa).getByRole('button', { name: '🛒 Agregar' }).waitFor()
  await tarjeta(venda).getByText('✓ Agregado').waitFor()
  await tarjeta(gasa).getByRole('button', { name: '🛒 Agregar' }).click()
  await tarjeta(gasa).getByText('✓ Agregado').waitFor()
  if (!p.url().includes('/inventario?')) throw new Error('el botón abrió el producto: ' + p.url())
  await p.screenshot({ path: path.join(DIR, 'inventario-agregar-celular.png') })
  await tarjeta(gasa).getByText(gasa.nombre).click()
  await p.waitForURL(`**/inventario/${gasa.id}`)
  const enLista = (await api('/api/pedidos/por-comprar')).productos.find((x) => x.productoId === gasa.id)
  igual(enLista?.aMano?.cantidad ?? 'sin cantidad', 'sin cantidad', 'gasa en Por comprar')
  ok('celular: "🛒 Agregar" chico lo agrega sin abrir el producto y pasa a "✓ Agregado"; tocar el nombre sí lo abre')

  // ------------------------------------------------------------------ inventario en la PC
  const pc = await navegador.newContext({ viewport: { width: 1366, height: 768 }, locale: 'es-PE', storageState: await contexto.storageState() })
  const q = await pc.newPage()
  q.on('pageerror', (e) => errores.push(String(e)))
  await q.goto(`${BASE}/inventario?buscar=invent%20e2e${s}`)
  const fila = (prod) => q.locator('tr').filter({ hasText: prod.nombre })
  await fila(gasa).getByText('✓ En Por comprar').waitFor()
  await fila(venda).getByText('✓ En Por comprar').waitFor()
  await q.getByRole('columnheader', { name: 'Por comprar' }).waitFor()
  const otra = await producto(`ALGODON INVENT E2E${s}`)
  await q.reload()
  await fila(otra).getByRole('button', { name: '🛒 Agregar' }).click()
  await fila(otra).getByText('✓ En Por comprar').waitFor()
  if (!q.url().includes('/inventario?')) throw new Error('el botón de la tabla abrió el producto: ' + q.url())
  const ancho = await q.evaluate(() => document.querySelector('aside').getBoundingClientRect().width)
  const alturas = await q.evaluate(() => [...document.querySelectorAll('aside nav a')].map((a) => Math.round(a.getBoundingClientRect().height)))
  // Mide el ancho de las letras: solo vale con las de Windows (las de la PC de la app). En Linux (GitHub)
  // la letra del sistema es más ancha y "Registrar entrada" no entra en una línea.
  const conLetrasDeWindows = process.platform === 'win32'
  if (conLetrasDeWindows && new Set(alturas).size !== 1) throw new Error('algún botón de la barra se partió en dos líneas: ' + alturas)
  const tablaSeSale = await q.evaluate(() => { const t = document.querySelector('table'); return t.scrollWidth > t.parentElement.clientWidth + 1 })
  igual(tablaSeSale, false, 'la tabla se sale')
  await q.screenshot({ path: path.join(DIR, 'inventario-agregar-pc.png') })
  ok(`PC: columna "Por comprar" con su botón (no abre el producto); barra lateral de ${ancho} px${conLetrasDeWindows ? ' sin textos partidos' : ''}; la tabla entra`)

  // ------------------------------------------------------------------ ítem quitado en una corrección
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Quitados E2E${s}`, porcentaje: 10, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: gasa.id, cantidad: 5 })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: venda.id, cantidad: 3 })
  await api(`/api/pedidos/${pedido.id}/despachar`, 'POST')

  await p.goto(`${BASE}/pedidos/${pedido.id}`)
  await p.getByRole('button', { name: 'Corregir' }).click()
  await p.getByLabel(`Cantidad de ${venda.nombre}`).fill('0')
  await p.getByRole('button', { name: 'Guardar corrección' }).click()
  await p.getByText(/VENDA INVENT E2E\d+: 3 → 0 \(quitado\)/).waitFor()
  igual(await p.getByText('Quitado en una corrección').count(), 0, 'etiqueta "Quitado"')
  // (el historial de correcciones sí la nombra: "3 → 0 (quitado)"; las filas de ítems no llevan "→")
  igual(await p.locator('li').filter({ hasText: venda.nombre }).filter({ hasNotText: '→' }).count(), 0, 'la venda en la lista del pedido')
  await p.locator('footer').getByText('1 ítem', { exact: true }).waitFor()
  await p.screenshot({ path: path.join(DIR, 'pedido-sin-quitados.png'), fullPage: true })
  ok('al quitar la venda, ya no aparece en el pedido ni cuenta ("1 ítem"); queda en el historial: "3 → 0 (quitado)"')

  await p.getByRole('button', { name: 'Corregir' }).click()
  await p.getByLabel(`Cantidad de ${gasa.nombre}`).waitFor()
  igual(await p.getByLabel(`Cantidad de ${venda.nombre}`).count(), 0, 'la venda en Corregir')
  await p.getByPlaceholder('Agregar un producto al pedido…').fill(venda.nombre)
  await p.getByRole('button', { name: new RegExp(venda.nombre) }).click()
  igual(await p.getByLabel(`Cantidad de ${venda.nombre}`).inputValue(), '1', 'la venda vuelve con 1')
  await p.getByLabel(`Cantidad de ${venda.nombre}`).fill('2')
  await p.getByRole('button', { name: 'Guardar corrección' }).click()
  await p.getByText(/VENDA INVENT E2E\d+: 0 → 2/).waitFor()
  await p.locator('footer').getByText('2 ítems', { exact: true }).waitFor()
  ok('en Corregir la venda quitada no aparece; si se vuelve a agregar, regresa su línea y se entregan 2 (ya son "2 ítems")')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
