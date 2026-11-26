// Costo y ganancia en el pedido y en las listas, precio especial en Corregir y Proveedores en la barra lateral.
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
  const nuevo = (nombre) =>
    api('/api/productos', 'POST', { nombre, marca: 'MARCA E2E', categoriaId, presentacion: 'Unidad', unidadMedida: 'Unidad', costo: 10 })
  const gasa = await nuevo(`GASA COSTO E2E${s}`)
  const alcohol = await nuevo(`ALCOHOL COSTO E2E${s}`)
  for (const prod of [gasa, alcohol]) {
    await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `C${s}`, fechaVencimiento: '2029-01-01', cantidad: 50, costo: 10 })
  }
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Costo E2E${s}`, porcentaje: 15, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })

  // Borrador: 4 gasas a S/ 11.50 → costo S/ 40, gana S/ 6
  const borrador = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${borrador.id}/items`, 'POST', { productoId: gasa.id, cantidad: 4 })
  await p.goto(`${BASE}/pedidos/${borrador.id}`)
  await p.getByText('costo S/ 40.00').waitFor()
  await p.getByText('gana S/ 6.00').waitFor()
  await p.screenshot({ path: path.join(DIR, 'costo-borrador.png') })
  ok('borrador: en la barra de abajo, "costo S/ 40.00 · gana S/ 6.00" junto al total')

  await p.goto(`${BASE}/pedidos`)
  const tarjeta = p.getByRole('listitem').filter({ hasText: `Botica Costo E2E${s}` })
  await tarjeta.getByText('costo S/ 40.00').waitFor()
  await tarjeta.getByText('gana S/ 6.00').waitFor()
  ok('Pendientes: la tarjeta muestra costo y ganancia debajo del total')

  // Precio por debajo del costo: "pierde" en rojo
  const item = (await api(`/api/pedidos/${borrador.id}`)).items[0]
  await api(`/api/pedidos/${borrador.id}/items/${item.id}`, 'PUT', { cantidad: 4, precio: 9 })
  await p.goto(`${BASE}/pedidos/${borrador.id}`)
  const pierde = p.getByText('pierde S/ 4.00')
  await pierde.waitFor()
  const color = await pierde.evaluate((e) => getComputedStyle(e).color)
  await p.screenshot({ path: path.join(DIR, 'costo-pierde.png') })
  ok(`precio por debajo del costo → "pierde S/ 4.00" (color ${color})`)

  // Despachado con el precio especial (S/ 9): en Despachados, costo real
  await api(`/api/pedidos/${borrador.id}/despachar`, 'POST')
  await p.goto(`${BASE}/pedidos?estado=DESPACHADO`)
  const despachada = p.getByRole('listitem').filter({ hasText: `Botica Costo E2E${s}` })
  await despachada.getByText('costo S/ 40.00').waitFor()
  await despachada.getByText('pierde S/ 4.00').waitFor()
  await p.screenshot({ path: path.join(DIR, 'costo-despachados.png') })
  ok('Despachados: costo real de los lotes (S/ 40.00) y "pierde S/ 4.00"')

  // Corregir: agregar el alcohol, al que ya se le dio un precio especial en otro pedido
  const otro = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  const conAlcohol = await api(`/api/pedidos/${otro.id}/items`, 'POST', { productoId: alcohol.id, cantidad: 1 })
  await api(`/api/pedidos/${otro.id}/items/${conAlcohol.items[0].id}`, 'PUT', { cantidad: 1, precio: 14.2 })
  await p.goto(`${BASE}/pedidos/${borrador.id}`)
  await p.getByRole('button', { name: 'Corregir', exact: true }).click()
  await p.getByPlaceholder('Agregar un producto al pedido…').fill(`alcohol costo e2e${s}`)
  await p.getByRole('button', { name: new RegExp(`^ALCOHOL COSTO E2E${s}`) }).first().click()
  await p.getByText('El precio es el especial que ya se le dio a este cliente.').waitFor()
  const precio = await p.getByRole('textbox', { name: 'Precio (con IGV)' }).inputValue()
  if (precio !== '14.20') throw new Error('el precio sugerido debería ser 14.20: ' + precio)
  await p.screenshot({ path: path.join(DIR, 'corregir-precio-especial.png'), fullPage: true })
  ok('Corregir → agregar producto: propone S/ 14.20, el precio especial de ese cliente')

  // En la PC: Proveedores en la barra lateral
  const pc = await navegador.newContext({ viewport: { width: 1366, height: 768 }, locale: 'es-PE', storageState: await contexto.storageState() })
  const p2 = await pc.newPage()
  await p2.goto(`${BASE}/`)
  const enlace = p2.getByRole('complementary').getByRole('link', { name: 'Proveedores' })
  await enlace.waitFor()
  const todoVisible = await p2.evaluate(() => {
    const nav = document.querySelector('aside nav')
    return nav.scrollHeight <= nav.clientHeight
  })
  await p2.screenshot({ path: path.join(DIR, 'barra-lateral-pc.png') })
  await enlace.click()
  await p2.waitForURL(/\/proveedores$/)
  ok(`PC (1366×768): Proveedores en la barra lateral; ${todoVisible ? 'entran todas las opciones sin desplazar' : 'hay que desplazar la barra'}`)

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
