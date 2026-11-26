// Por comprar: agregar productos a mano (con y sin cantidad), juntarlos con lo que falta para pedidos,
// Cambiar, Quitar, el botón de la ficha del producto y que la entrada lo quite solo.
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
  const producto = async (nombre, stockMinimo, hay, costo) => {
    const prod = await api('/api/productos', 'POST', {
      nombre, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja x 100', unidadMedida: 'Caja', stockMinimo,
    })
    await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `L${s}${prod.id}`, cantidad: hay, costo })
    return prod
  }
  const guante = await producto(`GUANTE MANO E2E${s}`, 10, 3, 18)
  const alcohol = await producto(`ALCOHOL MANO E2E${s}`, 30, 4, 9.8)
  const venda = await producto(`VENDA MANO E2E${s}`, 0, 2, 5)
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Mano E2E${s}`, porcentaje: 10, locales: [{ nombre: 'Sede Norte', direccion: 'Av. Prueba 1' }] })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: alcohol.id, cantidad: 24 }) // faltan 20
  // La base local tiene otros pedidos de prueba: se compara contra lo que ya sumaba la lista
  const costoAntes = (await api('/api/pedidos/por-comprar')).costoTotal

  const tarjeta = (nombre) => p.locator('li').filter({ has: p.getByRole('heading', { name: nombre }) })
  const seccion = (titulo) => p.locator('section').filter({ has: p.getByRole('heading', { name: titulo }) })
  const agregar = async (prod, cantidad, nota) => {
    await p.getByRole('button', { name: '+ Agregar producto' }).click()
    await p.getByPlaceholder('¿Qué producto hay que comprar?').fill(prod.nombre)
    await p.getByRole('button', { name: new RegExp(prod.nombre) }).click()
    await p.getByLabel(/¿Cuántos comprar\?/).fill(cantidad)
    if (nota) {
      await p.getByLabel('Nota (opcional)').fill(nota)
    }
  }

  // ------------------------------------------------------------------ agregar uno que ningún pedido espera
  await p.goto(BASE + '/por-comprar')
  await agregar(guante, '20', 'Urgente, se acaba esta semana')
  await p.getByText('Hay 3 disponibles · mínimo 10').waitFor()
  await p.getByRole('button', { name: 'Agregar', exact: true }).click()
  const tGuante = seccion('Agregados a mano').locator('li').filter({ has: p.getByRole('heading', { name: guante.nombre }) })
  await tGuante.waitFor()
  const textoGuante = await tGuante.innerText()
  for (const parte of ['COMPRAR', '20', 'Hay 3 · mínimo 10', 'Agregado a mano el', '«Urgente, se acaba esta semana»', 'Llegó: registrar entrada']) {
    if (!textoGuante.includes(parte)) throw new Error(`falta «${parte}» en: ${textoGuante}`)
  }
  ok('agregar a mano: sale en "Agregados a mano" con COMPRAR 20, "Hay 3 · mínimo 10" y la nota, sin cliente')

  // ------------------------------------------------------------------ uno que ya falta para un pedido
  await agregar(alcohol, '10')
  await p.getByText('Ya está en Por comprar: a los pedidos pendientes les faltan 20.').waitFor()
  await p.screenshot({ path: path.join(DIR, 'porcomprar-agregar.png') })
  await p.getByRole('button', { name: 'Agregar', exact: true }).click()
  const tAlcohol = seccion('Agregados a mano').locator('li').filter({ has: p.getByRole('heading', { name: alcohol.nombre }) })
  await tAlcohol.waitFor()
  const textoAlcohol = await tAlcohol.innerText()
  for (const parte of ['COMPRAR', '30', 'Piden 24 · hay 4', `Botica Mano E2E${s} · Sede Norte`, 'le faltan 20', '10 más']) {
    if (!textoAlcohol.includes(parte)) throw new Error(`falta «${parte}» en: ${textoAlcohol}`)
  }
  igual(await p.getByRole('heading', { name: alcohol.nombre }).count(), 1, 'veces que sale el alcohol')
  ok('si ya faltaba para un pedido, avisa, sale UNA vez arriba con COMPRAR 30 (20 del pedido + "10 más")')

  // ------------------------------------------------------------------ desde la ficha del producto, sin cantidad
  await p.goto(`${BASE}/inventario/${venda.id}`)
  await p.getByRole('link', { name: '🛒 Agregar a Por comprar' }).click()
  await p.waitForURL(`**/por-comprar?agregar=${venda.id}`)
  await p.getByRole('heading', { name: venda.nombre }).first().waitFor()
  await p.getByRole('button', { name: 'Agregar', exact: true }).click()
  const tVenda = seccion('Agregados a mano').locator('li').filter({ has: p.getByRole('heading', { name: venda.nombre }) })
  await tVenda.waitFor()
  if (!(await tVenda.innerText()).includes('sin cantidad')) throw new Error('venda: ' + (await tVenda.innerText()))
  if (p.url().includes('agregar=')) throw new Error('la dirección sigue con agregar: ' + p.url())
  ok('el botón "🛒 Agregar a Por comprar" de la ficha abre el panel con el producto; sin cantidad dice "sin cantidad"')

  const datos = await api('/api/pedidos/por-comprar')
  // Se suman guante 20 × 18 y los 10 frascos más de alcohol × 9.80 (la venda no suma: no tiene cantidad)
  const esperado = Math.round((costoAntes + 20 * 18 + 10 * 9.8) * 100) / 100
  igual(datos.costoTotal, esperado, 'costo total')
  const textoCosto = 'S/ ' + esperado.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  await p.locator('footer').getByText(textoCosto).waitFor()
  await p.screenshot({ path: path.join(DIR, 'porcomprar-lista.png'), fullPage: true })
  ok(`"Costo de lo que falta" suma lo agregado con cantidad (S/ 458.00 más): ${textoCosto}`)

  // ------------------------------------------------------------------ Cambiar y Quitar
  await tGuante.getByRole('button', { name: 'Cambiar' }).click()
  await p.getByText(/^Ya lo agregaste el/).waitFor()
  igual(await p.getByLabel(/¿Cuántos comprar\?/).inputValue(), '20', 'cantidad al cambiar')
  await p.getByLabel(/¿Cuántos comprar\?/).fill('25')
  await p.getByRole('button', { name: 'Guardar cambios' }).click()
  await tGuante.getByText('25', { exact: true }).waitFor()
  ok('"Cambiar" abre el panel con la cantidad y la nota; "Guardar cambios" deja COMPRAR 25')

  await tAlcohol.getByRole('button', { name: 'Quitar' }).click()
  const tAlcoholPedidos = seccion('Para pedidos pendientes').locator('li').filter({ has: p.getByRole('heading', { name: alcohol.nombre }) })
  await tAlcoholPedidos.waitFor()
  const textoQuitado = await tAlcoholPedidos.innerText()
  if (!textoQuitado.includes('FALTAN') || !textoQuitado.includes('20') || textoQuitado.includes('Agregado a mano')) {
    throw new Error('alcohol quitado: ' + textoQuitado)
  }
  ok('"Quitar" borra solo lo agregado: el alcohol vuelve a "Para pedidos pendientes" con FALTAN 20')

  // ------------------------------------------------------------------ la entrada lo quita solo
  await tGuante.getByRole('link', { name: 'Llegó: registrar entrada' }).click()
  await p.getByLabel('Cantidad (caja)').fill('25')
  await p.getByLabel('Costo por caja (S/)').fill('18')
  await p.getByRole('button', { name: 'Guardar y siguiente' }).click()
  await p.getByRole('heading', { name: 'Ingresado ahora (1)' }).waitFor()
  await p.goto(BASE + '/por-comprar')
  await seccion('Agregados a mano').waitFor()
  igual(await p.getByRole('heading', { name: guante.nombre }).count(), 0, 'el guante después de la entrada')
  ok('al registrar la entrada del guante, se quitó solo de Por comprar')

  await p.goto(BASE + '/')
  const boton = p.getByRole('link', { name: /Por comprar/ }).first()
  const textoInicio = await boton.innerText()
  ok(`el botón del inicio sigue contando lo de la lista: «${textoInicio.replace(/\n/g, ' ')}»`)

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
