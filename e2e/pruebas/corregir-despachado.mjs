// Corregir un pedido despachado (devolución de varios lotes, precio, ítem nuevo) en un iPhone emulado.
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')

export default async function prueba(navegador) {
  const contexto = await navegador.newContext({ ...devices['iPhone 14'], deviceScaleFactor: 1, locale: 'es-PE' })
  const p = await contexto.newPage()
  p.setDefaultTimeout(10000)
  const errores = []
  p.on('pageerror', (e) => errores.push(String(e)))
  p.on('dialog', (d) => d.accept())
  const foto = (n) => p.screenshot({ path: path.join(DIR, `correccion-${n}.png`), fullPage: true })
  const sufijo = String(Date.now()).slice(-5)

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

  // Datos: jeringa con 2 lotes (sale 3 del primero y 2 del segundo), guantes para agregar
  const categorias = await api('/api/categorias')
  const nuevo = (nombre, costo) => api('/api/productos', 'POST', {
    nombre: `${nombre} ${sufijo}`, marca: 'Marca C', categoriaId: categorias[0].id,
    presentacion: 'Caja x 100', unidadMedida: 'Caja', costo,
  })
  const jeringa = await nuevo('Jeringa corr', 10)
  const guantes = await nuevo('Guantes corr', 20)
  await api('/api/movimientos/entrada', 'POST', { productoId: jeringa.id, numeroLote: `A-${sufijo}`, fechaVencimiento: '2027-06-30', cantidad: 3, costo: 10 })
  await api('/api/movimientos/entrada', 'POST', { productoId: jeringa.id, numeroLote: `B-${sufijo}`, fechaVencimiento: '2029-06-30', cantidad: 10, costo: 10 })
  await api('/api/movimientos/entrada', 'POST', { productoId: guantes.id, numeroLote: `G-${sufijo}`, fechaVencimiento: '2029-06-30', cantidad: 10, costo: 20 })
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Corr ${sufijo}`, porcentaje: 10 })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: jeringa.id, cantidad: 5 })
  await api(`/api/pedidos/${pedido.id}/despachar`, 'POST', {})
  await api(`/api/pedidos/${pedido.id}/pagos`, 'POST', { monto: 55, medio: 'YAPE' })

  // 1. Corregir: el botón está en el pedido despachado
  await p.goto(`${BASE}/pedidos/${pedido.id}`)
  await p.getByRole('button', { name: 'Corregir' }).click()
  await p.getByRole('heading', { name: /Corregir/ }).waitFor()
  ok('pedido despachado → botón "Corregir" abre la corrección')

  // 2. Bajar de 5 a 3: salió de 2 lotes, pregunta a cuál vuelven
  const cantidad = p.getByLabel(`Cantidad de Jeringa corr ${sufijo}`)
  await cantidad.fill('3')
  await p.getByText('¿A qué lote vuelven las 2 unidades?').waitFor()
  await p.getByText(/indica a qué lote vuelven las 2 unidades/).waitFor()
  await p.getByLabel(`Unidades que vuelven al lote B-${sufijo}`).fill('2')
  ok('al bajar de 5 a 3 pregunta a qué lote vuelven (salió de 2 lotes)')

  // 3. Precio especial y un producto nuevo
  await p.getByLabel(`Precio de Jeringa corr ${sufijo}`).fill('10.50')
  await p.getByPlaceholder('Agregar un producto al pedido…').fill(`Guantes corr ${sufijo}`)
  await p.getByRole('button', { name: new RegExp(`Guantes corr ${sufijo}`) }).first().click()
  await p.getByText('Sale del inventario al guardar.').waitFor()
  await p.getByLabel('Motivo (opcional)').fill('Devolvieron 2 jeringas y pidió una caja de guantes')
  await foto('1-editar')
  const total = await p.getByText('antes S/ 55.00').locator('..').innerText()
  ok(`total antes y después: ${total.replace(/\s+/g, ' ')}`)  // 3 × 10.50 + 1 × 22 = 53.50

  // 4. Guardar
  await p.getByRole('button', { name: 'Guardar corrección' }).click()
  await p.getByRole('heading', { name: 'Correcciones' }).waitFor()
  await p.getByText('Saldo a favor del cliente S/ 1.50').waitFor()
  await foto('2-corregido')
  const historial = await p.getByRole('heading', { name: 'Correcciones' }).locator('..').innerText()
  ok(`guardado: historial "${historial.replace(/\s+/g, ' ').slice(12, 200)}…"`)
  ok('pagó S/ 55 y el total corregido es S/ 53.50: "Saldo a favor del cliente S/ 1.50"')

  // 5. Inventario: las 2 volvieron al lote B; salió 1 caja de guantes
  const j = await api(`/api/productos/${jeringa.id}`)
  const g = await api(`/api/productos/${guantes.id}`)
  const loteB = j.lotes.find((l) => l.numeroLote === `B-${sufijo}`)
  if (loteB.cantidad !== 10 || g.stockDisponible !== 9) throw new Error(`stock: lote B ${loteB.cantidad}, guantes ${g.stockDisponible}`)
  ok('inventario: el lote B volvió a 10 y los guantes bajaron a 9')

  // 6. Cobranzas: pestaña "A favor"
  await p.goto(BASE + '/cobranzas')
  await p.getByRole('tab', { name: /A favor/ }).click()
  await p.getByText(`Botica Corr ${sufijo}`).waitFor()
  await foto('3-cobranzas')
  ok('Cobranzas muestra la pestaña "A favor" con el cliente')

  console.log(errores.length ? 'ERRORES: ' + errores.join(' | ') : 'Sin errores en la consola')
  console.log(`${pasos} comprobaciones OK`)
}
