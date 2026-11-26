// Historial del producto, "Dar de baja" en Vencidos, lo dado de baja en Más detalles y los dos textos quitados.
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
  const prod = await api('/api/productos', 'POST', { nombre: `GASA HISTORIAL E2E${s}`, marca: 'MARCA E2E', categoriaId, presentacion: 'Paquete x 100', unidadMedida: 'Paquete', costo: 12 })
  const vigente = (await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `H${s}`, fechaVencimiento: '2029-01-31', cantidad: 30, costo: 12 })).movimientos[0].loteId
  const vencido = (await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `V${s}`, fechaVencimiento: '2025-10-31', cantidad: 3, costo: 8 })).movimientos[0].loteId
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Historial E2E${s}`, porcentaje: 10, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: prod.id, cantidad: 5 })
  const despachado = await api(`/api/pedidos/${pedido.id}/despachar`, 'POST')
  await api(`/api/pedidos/${pedido.id}/correcciones`, 'POST', { cambios: [{ itemId: despachado.items[0].id, cantidad: 4 }] })
  await api('/api/movimientos/ajuste', 'POST', { loteId: vigente, cantidadReal: 24, nota: 'Conteo físico: faltaban 2' })

  // ------------------------------------------------------------------ Alertas → Vencidos → Dar de baja
  await p.goto(`${BASE}/alertas?ver=vencidos`)
  const tarjeta = p.locator('li').filter({ hasText: prod.nombre })
  await tarjeta.getByRole('button', { name: 'Dar de baja' }).click()
  await tarjeta.waitFor({ state: 'detached' })
  ok('Alertas → Vencidos: "Dar de baja" (con confirmación) lo deja en 0 y desaparece de la lista')

  // ------------------------------------------------------------------ historial en la ficha
  await p.goto(`${BASE}/inventario/${prod.id}`)
  const historial = p.locator('div').filter({ has: p.getByRole('heading', { name: 'Historial', exact: true }) }).last()
  await historial.getByText('Baja por vencido').waitFor()
  const texto = await historial.innerText()
  for (const parte of ['Baja por vencido', 'venció en 10/2025', 'Ajuste', '«Conteo físico: faltaban 2»', 'Devolución',
    `Corrección de la nota ${despachado.numero} · Botica Historial E2E${s}`, 'Salida', `Nota ${despachado.numero} · Botica Historial E2E${s}`,
    'Entrada', 'S/ 12.00 c/u', 'quedan']) {
    if (!texto.includes(parte)) throw new Error(`falta «${parte}» en el historial:\n${texto}`)
  }
  igual(await p.getByText('Toca un lote para corregir').count(), 0, 'texto de Lotes')
  igual(await p.getByText('Uso interno: costo más').count(), 0, 'texto de Precios')
  await p.screenshot({ path: path.join(DIR, 'historial-celular.png'), fullPage: true })
  await historial.getByRole('link', { name: new RegExp(`Nota ${despachado.numero}`) }).first().click()
  await p.waitForURL(`**/pedidos/${pedido.id}`)
  ok('ficha del producto: historial con baja, ajuste, devolución y salida (con nota y cliente, abre el pedido) y entrada; sin los dos textos')

  // ------------------------------------------------------------------ PC: en la columna derecha
  const pc = await (await navegador.newContext({ viewport: { width: 1366, height: 900 }, locale: 'es-PE', timezoneId: 'America/Lima', storageState: await contexto.storageState() })).newPage()
  await pc.goto(`${BASE}/inventario/${prod.id}`)
  await pc.getByRole('heading', { name: 'Historial', exact: true }).waitFor()
  const xHistorial = (await pc.getByRole('heading', { name: 'Historial', exact: true }).boundingBox()).x
  const xLotes = (await pc.getByRole('heading', { name: 'Lotes' }).boundingBox()).x
  if (!(xHistorial > xLotes + 100)) throw new Error('en la PC el historial debería ir en la columna derecha')
  await pc.screenshot({ path: path.join(DIR, 'historial-pc.png'), fullPage: true })
  ok('PC: el historial va en la columna derecha, debajo de los botones')

  // ------------------------------------------------------------------ Más detalles
  const detalles = await api('/api/resumen/detalles?periodo=MES')
  if (!(detalles.inventario.valorDadoDeBaja >= 24)) throw new Error('dado de baja: ' + detalles.inventario.valorDadoDeBaja)
  await p.goto(`${BASE}/resumen/detalles`)
  await p.getByText(/Dado de baja este mes: S\/ /).waitFor()
  ok(`Más detalles: "Dado de baja este mes: S/ ${detalles.inventario.valorDadoDeBaja.toFixed(2)}" (incluye los 3 × S/ 8)`)

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
