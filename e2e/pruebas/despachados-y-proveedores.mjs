// Despachados por día (7 días, búsqueda en todo el historial, fechas) y Proveedores (deudas y pagos).
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR, sql } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')

const dos = (n) => String(n).padStart(2, '0')
const dia = (d) => `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}`
const haceDias = (n) => {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d
}
const ddmmaaaa = (iso) => iso.split('-').reverse().join('/')

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

  // ================================================================ despachados
  const categoriaId = (await api('/api/categorias'))[0].id
  const producto = await api('/api/productos', 'POST', {
    nombre: `GASA E2E${s}`, marca: 'MARCA E2E', categoriaId, presentacion: 'Unidad', unidadMedida: 'Unidad', costo: 10,
  })
  await api('/api/movimientos/entrada', 'POST', { productoId: producto.id, numeroLote: `G${s}`, fechaVencimiento: '2029-01-01', cantidad: 100, costo: 10 })
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Despachos E2E${s}`, porcentaje: 10, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const despachar = async (cantidad, diasAtras) => {
    const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
    await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: producto.id, cantidad })
    const d = await api(`/api/pedidos/${pedido.id}/despachar`, 'POST')
    await sql(`UPDATE pedido SET despachado_en = now() - interval '${diasAtras} days' WHERE id = ${pedido.id}`)
    return d.numero
  }
  const hoy1 = await despachar(1, 0)
  const hoy2 = await despachar(2, 0)
  const hace3 = await despachar(3, 3)
  const hace20 = await despachar(4, 20)

  await p.goto(`${BASE}/pedidos?estado=DESPACHADO`)
  await p.getByText(/^Últimos 7 días/).waitFor()
  await p.getByRole('heading', { name: /^hoy · /i }).waitFor()
  const textoHoy = await p.getByRole('heading', { name: /^hoy · /i }).innerText()
  if (!(await p.getByText(`N° ${hoy1}`).count()) || !(await p.getByText(`N° ${hace3}`).count())) throw new Error('faltan los de esta semana')
  if (await p.getByText(`N° ${hace20}`).count()) throw new Error('el de hace 20 días no debería verse sin filtros')
  await p.screenshot({ path: path.join(DIR, 'despachados-7-dias.png') })
  ok(`sin filtros: últimos 7 días separados por día ("${textoHoy.replace(/\s+/g, ' ')}"); el de hace 20 días no sale`)

  await p.getByPlaceholder('Buscar cliente o N° de nota…').fill(`despachos e2e${s}`)
  await p.getByText(/^Todo el historial/).waitFor()
  await p.getByText(`N° ${hace20}`).waitFor()
  const resumen = await p.getByRole('status').filter({ hasText: 'Todo el historial' }).innerText()
  if (!resumen.includes('4 pedidos') || !resumen.includes('S/ 110.00')) throw new Error('resumen: ' + resumen)
  ok(`buscar el cliente sin fechas: todo el historial → "${resumen.replace(/\s+/g, ' ')}"`)

  await p.getByPlaceholder('Buscar cliente o N° de nota…').fill(hace3)
  await p.getByRole('status').filter({ hasText: '1 pedido ·' }).waitFor()
  await p.getByText(`N° ${hace3}`).waitFor()
  ok(`buscar por N° de nota (${hace3}): solo ese pedido`)

  await p.getByPlaceholder('Buscar cliente o N° de nota…').fill(`despachos e2e${s}`)
  await p.getByText(/^Todo el historial/).waitFor()
  await p.getByRole('button', { name: '📅 Elegir fechas' }).click()
  const desde = dia(haceDias(25))
  const hasta = dia(haceDias(15))
  await p.getByLabel('Desde').fill(desde)
  await p.getByLabel('Hasta').fill(hasta)
  await p.screenshot({ path: path.join(DIR, 'despachados-elegir-fechas.png') })
  await p.getByRole('button', { name: 'Ver esas fechas' }).click()
  await p.getByText(`Del ${ddmmaaaa(desde)} al ${ddmmaaaa(hasta)}`).waitFor()
  await p.getByRole('status').filter({ hasText: '1 pedido ·' }).waitFor()
  await p.getByText(`N° ${hace20}`).waitFor()
  await p.getByRole('button', { name: `📅 ${ddmmaaaa(desde)} – ${ddmmaaaa(hasta)}` }).waitFor()
  await p.screenshot({ path: path.join(DIR, 'despachados-fechas.png') })
  ok(`fechas ${ddmmaaaa(desde)} – ${ddmmaaaa(hasta)} + cliente: solo el de hace 20 días; el botón muestra las fechas`)

  await p.getByText(`N° ${hace20}`).click()
  await p.waitForURL(/\/pedidos\/\d+$/)
  await p.goBack()
  await p.getByText(`Del ${ddmmaaaa(desde)} al ${ddmmaaaa(hasta)}`).waitFor()
  if ((await p.getByPlaceholder('Buscar cliente o N° de nota…').inputValue()) !== `despachos e2e${s}`) throw new Error('la búsqueda debería seguir')
  ok('al abrir un pedido y volver, la búsqueda y las fechas siguen')

  await p.getByRole('button', { name: 'Quitar fechas' }).click()
  await p.getByText(/^Todo el historial/).waitFor()
  await p.getByPlaceholder('Buscar cliente o N° de nota…').fill('')
  await p.getByText(/^Últimos 7 días/).waitFor()
  ok('× quita las fechas y, sin búsqueda, vuelve a los últimos 7 días')

  for (const t of ['Pendientes', 'Anulados']) {
    await p.getByRole('tab', { name: t }).click()
    await p.getByPlaceholder('Buscar cliente o N° de nota…').waitFor({ state: 'detached', timeout: 5000 })
    if (await p.getByRole('button', { name: '📅 Elegir fechas' }).count()) throw new Error(`${t} no lleva fechas`)
  }
  ok('Pendientes y Anulados quedan igual (sin buscador ni fechas)')

  // ================================================================ proveedores
  await p.goto(BASE + '/')
  await p.getByRole('link', { name: /Proveedores/ }).click()
  await p.getByText('Falta pagar').first().waitFor()
  const antes = await api('/api/proveedores/resumen')
  await p.getByRole('button', { name: '+ Anotar deuda' }).click()
  await p.getByLabel('Proveedor').fill(`Distribuidora E2E ${s}`)
  await p.getByText(`Proveedor nuevo: se agregará «Distribuidora E2E ${s}»`).waitFor()
  await p.getByLabel('Monto (S/)').fill('1500')
  await p.getByLabel('Nota (opcional)').fill(`Factura F001-${s}`)
  await p.screenshot({ path: path.join(DIR, 'proveedores-anotar.png') })
  await p.getByRole('button', { name: 'Guardar' }).click()
  const tarjeta = () => p.getByRole('listitem').filter({ hasText: `Factura F001-${s}` })
  await tarjeta().getByText('S/ 1,500.00').first().waitFor()
  ok('Anotar deuda con un proveedor nuevo: aparece en Por pagar con S/ 1,500.00')

  await p.getByRole('button', { name: '+ Anotar deuda' }).click()
  await p.getByLabel('Proveedor').fill(`distribuidora e2e ${s}`)
  if (await p.getByText(/Proveedor nuevo/).count()) throw new Error('escrito en minúsculas es el mismo proveedor')
  await p.getByLabel('Monto (S/)').fill('80')
  await p.getByLabel('Nota (opcional)').fill(`Para borrar ${s}`)
  await p.getByRole('button', { name: 'Guardar' }).click()
  await p.getByRole('listitem').filter({ hasText: `Para borrar ${s}` }).waitFor()
  const lista = await api('/api/proveedores')
  if (lista.filter((x) => x.nombre.toLowerCase() === `distribuidora e2e ${s}`).length !== 1) throw new Error('no debería repetir el proveedor')
  ok('el mismo proveedor escrito en minúsculas no se repite')

  await tarjeta().getByRole('button', { name: 'Registrar pago' }).click()
  await tarjeta().getByLabel('Monto (S/)').fill('500')
  await tarjeta().getByRole('radio', { name: 'Transferencia' }).click()
  await tarjeta().getByRole('button', { name: 'Guardar pago' }).click()
  await tarjeta().getByText('S/ 1,000.00').first().waitFor()
  await tarjeta().getByText('S/ 500.00 · Transferencia').waitFor()
  await p.screenshot({ path: path.join(DIR, 'proveedores-pago.png'), fullPage: true })
  ok('Registrar pago de S/ 500 → falta S/ 1,000.00 y el pago queda en la lista')

  await tarjeta().getByRole('button', { name: 'Pagó todo' }).click()
  await tarjeta().getByText('Hoy se le pagaron los S/ 1,000.00 que faltaban').waitFor()
  await tarjeta().getByRole('radio', { name: 'Efectivo' }).click()
  await tarjeta().getByRole('button', { name: /Registrar S\/ 1,000.00 como pagado/ }).click()
  await tarjeta().waitFor({ state: 'detached' })
  await p.getByRole('tab', { name: 'Pagadas' }).click()
  await tarjeta().getByText('Pagada', { exact: true }).waitFor()
  await p.screenshot({ path: path.join(DIR, 'proveedores-pagadas.png'), fullPage: true })
  ok('Pagó todo → pasa a Pagadas, con sus dos pagos')

  p.once('dialog', (d) => void d.accept())
  await tarjeta().getByRole('button', { name: /Borrar el pago de S\/ 1,000.00/ }).click()
  await tarjeta().waitFor({ state: 'detached' })
  await p.getByRole('tab', { name: /Por pagar/ }).click()
  await tarjeta().getByText('S/ 1,000.00').first().waitFor()
  ok('borrar un pago mal anotado la vuelve a Por pagar')

  const otra = () => p.getByRole('listitem').filter({ hasText: `Para borrar ${s}` })
  await otra().getByRole('button', { name: 'Editar' }).click()
  await otra().getByLabel('Monto (S/)').fill('95.50')
  await otra().getByRole('button', { name: 'Guardar' }).click()
  await otra().getByText('S/ 95.50').first().waitFor()
  p.once('dialog', (d) => void d.accept())
  await otra().getByRole('button', { name: 'Borrar deuda' }).click()
  await otra().waitFor({ state: 'detached' })
  ok('Editar cambia el monto (S/ 95.50) y "Borrar deuda" la quita (no tenía pagos)')

  const despues = await api('/api/proveedores/resumen')
  if (despues.deudasPorPagar !== antes.deudasPorPagar + 1 || Math.round((despues.porPagar - antes.porPagar) * 100) !== 100000) {
    throw new Error(`resumen: ${JSON.stringify(antes)} → ${JSON.stringify(despues)}`)
  }
  await p.goto(BASE + '/')
  await p.getByRole('link', { name: /Proveedores/ }).getByText(String(despues.deudasPorPagar), { exact: true }).waitFor()
  await p.screenshot({ path: path.join(DIR, 'inicio-proveedores.png') })
  ok(`el botón del inicio muestra ${despues.deudasPorPagar} deuda(s) por pagar`)

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
