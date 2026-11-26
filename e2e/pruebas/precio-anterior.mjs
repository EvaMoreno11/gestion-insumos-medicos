// Un pedido nuevo toma el precio que se le puso a mano al mismo cliente la última vez.
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
  const nombre = `AGUJA PRECIO E2E${s} 21G`
  const producto = await api('/api/productos', 'POST', {
    nombre, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja x 100', unidadMedida: 'Caja', costo: 10,
  })
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Precio E2E${s}`, porcentaje: 15, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })

  // Ayer: se le puso a mano S/ 13 (el calculado sería S/ 11.50)
  const ayer = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  const conItem = await api(`/api/pedidos/${ayer.id}/items`, 'POST', { productoId: producto.id, cantidad: 2 })
  await api(`/api/pedidos/${ayer.id}/items/${conItem.items[0].id}`, 'PUT', { cantidad: 2, precio: 13 })

  // Hoy: otro pedido a la misma botica; el producto se agrega desde la pantalla
  const hoy = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await p.goto(`${BASE}/pedidos/${hoy.id}`)
  await p.getByPlaceholder('Agregar producto: jer 5ml, guantes m…').fill(`aguja precio e2e${s}`)
  await p.getByRole('button', { name: new RegExp(`^${nombre}`) }).first().click()
  await p.getByText('Precio especial para este cliente · volver al calculado').waitFor()
  const agregado = (await api(`/api/pedidos/${hoy.id}`)).items[0]
  if (agregado.precioUnitario !== 13 || !agregado.precioManual) throw new Error('precio: ' + JSON.stringify(agregado))
  await p.screenshot({ path: path.join(DIR, 'precio-anterior.png') })
  ok('pedido nuevo a la misma botica: el producto sale a S/ 13.00 (el que se le puso a mano), no a S/ 11.50')

  await p.getByText('Precio especial para este cliente · volver al calculado').click()
  await p.getByText('Precio especial para este cliente · volver al calculado').waitFor({ state: 'detached' })
  const calculado = (await api(`/api/pedidos/${hoy.id}`)).items[0]
  if (calculado.precioUnitario !== 11.5 || calculado.precioManual) throw new Error('calculado: ' + JSON.stringify(calculado))
  ok('"volver al calculado" lo deja en costo + 15 % (S/ 11.50)')

  const otro = await api('/api/clientes', 'POST', { nombre: `Clínica Otra E2E${s}`, porcentaje: 15 })
  const pedidoOtro = await api('/api/pedidos', 'POST', { clienteId: otro.id })
  const r = await api(`/api/pedidos/${pedidoOtro.id}/items`, 'POST', { productoId: producto.id, cantidad: 1 })
  if (r.items[0].precioUnitario !== 11.5) throw new Error('otro cliente no hereda el precio: ' + r.items[0].precioUnitario)
  ok('otro cliente no hereda ese precio (S/ 11.50)')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
