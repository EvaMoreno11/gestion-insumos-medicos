// Corregir: cambiar de qué lotes salió un producto ("Todo de este" y repartir entre varios), motivo opcional.
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

  // 3 sueltas (vencen antes), caja abierta, caja sellada y otra caja de 60
  const categoriaId = (await api('/api/categorias'))[0].id
  const producto = await api('/api/productos', 'POST', {
    nombre: `JERINGA 5 ML E2E${s}`, marca: 'MARCA E2E', categoriaId, presentacion: 'Unidad', unidadMedida: 'Unidad', costo: 0.5,
  })
  const entrada = (numeroLote, fechaVencimiento, cantidad) =>
    api('/api/movimientos/entrada', 'POST', { productoId: producto.id, numeroLote, fechaVencimiento, cantidad, costo: 0.5 })
  await entrada('SUELTAS', '2027-03-01', 3)
  await entrada('ABIERTA', '2028-05-01', 100)
  await entrada('SELLADA', '2029-01-01', 100)
  await entrada('OTRA60', '2029-06-01', 60)
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Reparto E2E ${s}`, porcentaje: 15, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: producto.id, cantidad: 100 })
  const despachado = await api(`/api/pedidos/${pedido.id}/despachar`, 'POST')
  const salio = despachado.items[0].lotes.map((l) => `${l.numeroLote} ${l.cantidad}`).join(', ')
  if (salio !== 'SUELTAS 3, ABIERTA 97') throw new Error('despacho: ' + salio)

  // ---------------------------------------------------------------- todo de la caja sellada
  await p.goto(`${BASE}/pedidos/${pedido.id}`)
  await p.getByRole('button', { name: 'Corregir', exact: true }).click()
  await p.getByText('Salió de: SUELTAS (3) · ABIERTA (97)').waitFor()
  await p.getByRole('button', { name: 'Cambiar lotes' }).click()
  await p.getByText('¿De qué lotes salen las 100?').waitFor()
  await p.getByText('Asignadas 100 de 100 ✓').waitFor() // empieza como se entregó
  const todos = await p.getByRole('button', { name: 'Todo de este' }).count()
  if (todos !== 2) throw new Error('"Todo de este" debería salir solo en ABIERTA y SELLADA (alcanzan 100): ' + todos)
  const filaAbierta = await p.getByRole('listitem').filter({ hasText: 'Lote ABIERTA' }).innerText()
  if (!filaAbierta.includes('hay 100')) throw new Error('ABIERTA debería decir hay 100 (3 + 97 que vuelven): ' + filaAbierta)
  await p.getByRole('listitem').filter({ hasText: 'Lote SELLADA' }).getByRole('button', { name: 'Todo de este' }).click()
  await p.getByText('Asignadas 100 de 100 ✓').waitFor()
  await p.screenshot({ path: path.join(DIR, 'cambiar-lotes.png'), fullPage: true })
  p.once('dialog', (d) => {
    if (!d.message().includes('El total no cambia')) errores.push('confirmación: ' + d.message())
    void d.accept()
  })
  await p.getByRole('button', { name: 'Guardar corrección' }).click()
  await p.getByText(/100 · lote SELLADA/).waitFor()
  let actual = await api(`/api/pedidos/${pedido.id}`)
  if (actual.items[0].lotes.map((l) => `${l.numeroLote} ${l.cantidad}`).join() !== 'SELLADA 100') throw new Error(JSON.stringify(actual.items[0].lotes))
  if (actual.correcciones[0].motivo !== 'Cambio de lote') throw new Error('motivo: ' + actual.correcciones[0].motivo)
  await p.getByText('Cambio de lote').first().waitFor()
  await p.screenshot({ path: path.join(DIR, 'lote-cambiado.png'), fullPage: true })
  ok(`"Todo de este" en SELLADA, sin motivo → sale SELLADA 100; historial: "${actual.correcciones[0].motivo}: ${actual.correcciones[0].detalle[0]}"`)

  // ---------------------------------------------------------------- repartir entre dos (ninguno alcanza solo)
  await p.getByRole('button', { name: 'Corregir', exact: true }).click()
  await p.getByRole('button', { name: 'Cambiar lotes' }).click()
  await p.getByText('Asignadas 100 de 100 ✓').waitFor()
  await p.getByLabel(/del lote SELLADA$/).fill('40')
  await p.getByText('Faltan 60 por asignar').waitFor()
  if (!(await p.getByRole('button', { name: 'Guardar corrección' }).isDisabled())) throw new Error('con faltantes no se guarda')
  await p.getByLabel(/del lote OTRA60$/).fill('70')
  await p.getByText('Sobran 10').waitFor()
  await p.getByText('del lote OTRA60 pueden salir hasta 60').waitFor()
  await p.getByLabel(/del lote OTRA60$/).fill('60')
  await p.getByText('Asignadas 100 de 100 ✓').waitFor()
  await p.screenshot({ path: path.join(DIR, 'repartir-lotes.png'), fullPage: true })
  await p.getByLabel('Motivo (opcional)').fill('La caja de 60 estaba abierta')
  p.once('dialog', (d) => void d.accept())
  await p.getByRole('button', { name: 'Guardar corrección' }).click()
  await p.getByText(/60 · lote OTRA60/).waitFor()
  actual = await api(`/api/pedidos/${pedido.id}`)
  const repartido = actual.items[0].lotes.map((l) => `${l.numeroLote} ${l.cantidad}`).join(', ')
  if (repartido !== 'SELLADA 40, OTRA60 60') throw new Error('reparto: ' + repartido)
  ok(`repartir: SELLADA 40 + OTRA60 60, con avisos "Faltan 60", "Sobran 10" y "pueden salir hasta 60"; motivo escrito: "${actual.correcciones[0].motivo}"`)

  // ---------------------------------------------------------------- el stock quedó bien
  const detalle = await api(`/api/productos/${producto.id}`)
  const stock = detalle.lotes.map((l) => `${l.numeroLote} ${l.cantidad}`).join(', ')
  if (stock !== 'SUELTAS 3, ABIERTA 100, SELLADA 60') throw new Error('stock: ' + stock)
  ok(`inventario después: ${stock} (OTRA60 quedó en 0)`)

  // ---------------------------------------------------------------- No cambiar
  await p.getByRole('button', { name: 'Corregir', exact: true }).click()
  await p.getByRole('button', { name: 'Cambiar lotes' }).click()
  await p.getByRole('button', { name: 'No cambiar' }).click()
  await p.getByRole('button', { name: 'Cambiar lotes' }).waitFor()
  await p.getByText('Todavía no hay cambios.').waitFor()
  ok('"No cambiar" cierra el reparto y no queda ningún cambio')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
