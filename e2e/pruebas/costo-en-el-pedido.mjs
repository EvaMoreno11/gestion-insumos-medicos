// Costo chico debajo del precio en el pedido (borrador) y en Corregir; el recuadro del precio en rojo si es menor.
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')

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
  const producto = async (nombre, costo) => {
    const prod = await api('/api/productos', 'POST', { nombre, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja x 100', unidadMedida: 'Caja', costo })
    await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `K${s}${prod.id}`, cantidad: 100, costo })
    return prod
  }
  const jeringa = await producto(`JERINGA COSTO E2E${s}`, 20)
  const guante = await producto(`GUANTE COSTO E2E${s}`, 16.8)
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Costo E2E${s}`, porcentaje: 10, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: jeringa.id, cantidad: 2 })

  // ------------------------------------------------------------------ borrador
  await p.goto(`${BASE}/pedidos/${pedido.id}`)
  const fila = p.locator('li').filter({ hasText: jeringa.nombre })
  await fila.getByText('costo S/ 20.00', { exact: true }).waitFor()
  const precio = fila.getByLabel('Precio unitario')
  const rojo = async (campo) => (await campo.evaluate((e) => getComputedStyle(e).borderTopColor)).replace(/ /g, '') === 'rgb(239,68,68)' || (await campo.evaluate((e) => getComputedStyle(e).borderTopColor)).startsWith('oklch(0.637')
  if (await rojo(precio)) throw new Error('con 22.00 no debería estar en rojo')
  await precio.fill('19')
  if (!(await rojo(precio))) throw new Error('con 19.00 el recuadro debería estar en rojo')
  igual(await fila.getByText(/debajo|pierde/).count(), 0, 'texto extra')
  await p.screenshot({ path: path.join(DIR, 'costo-item-pedido.png') })
  await precio.press('Enter')
  ok('borrador: debajo del precio dice "costo S/ 20.00"; con 19.00 solo el recuadro se pone en rojo, sin más texto')

  // ------------------------------------------------------------------ corregir un despachado
  await api(`/api/pedidos/${pedido.id}/items/${(await api(`/api/pedidos/${pedido.id}`)).items[0].id}`, 'PUT', { cantidad: 2, precio: 22 })
  await api(`/api/pedidos/${pedido.id}/despachar`, 'POST')
  await p.goto(`${BASE}/pedidos/${pedido.id}`)
  igual(await p.getByText('costo S/ 20.00', { exact: true }).count(), 0, 'costo en el despachado sin corregir')
  await p.getByRole('button', { name: 'Corregir' }).click()
  const precioCorregir = p.getByLabel(`Precio de ${jeringa.nombre}`)
  await p.getByText('costo S/ 20.00', { exact: true }).waitFor()
  await precioCorregir.fill('18')
  if (!(await rojo(precioCorregir))) throw new Error('en Corregir con 18.00 el recuadro debería estar en rojo')
  await p.getByPlaceholder('Agregar un producto al pedido…').fill(guante.nombre)
  await p.getByRole('button', { name: new RegExp(guante.nombre) }).click()
  await p.getByText('costo S/ 16.80', { exact: true }).waitFor()
  await p.screenshot({ path: path.join(DIR, 'costo-item-corregir.png'), fullPage: true })
  ok('Corregir: "costo S/ 20.00" (con el que salió) en rojo al bajar a 18.00; el producto nuevo muestra "costo S/ 16.80"')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}

function igual(real, esperado, que) {
  if (real !== esperado) throw new Error(`${que}: «${real}» (esperaba «${esperado}»)`)
}
