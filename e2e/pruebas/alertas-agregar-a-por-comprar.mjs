// Alertas → Stock bajo: "🛒 Agregar a Por comprar" de un toque (sin cantidad), "✓ En Por comprar" si ya está,
// y que no pise la cantidad de uno ya agregado.
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

  // Una categoría propia para encontrar los productos fácil en la lista de Stock bajo
  const categoria = await api('/api/categorias', 'POST', { nombre: `Alertas E2E${s}` })
  const producto = async (nombre) => {
    const prod = await api('/api/productos', 'POST', {
      nombre, marca: 'MARCA E2E', categoriaId: categoria.id, presentacion: 'Caja x 100', unidadMedida: 'Caja', stockMinimo: 10,
    })
    await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `B${s}${prod.id}`, cantidad: 3, costo: 5 })
    return prod
  }
  const libre = await producto(`GASA BAJO E2E${s}`)
  const conPedido = await producto(`JERINGA BAJO E2E${s}`)
  const conCantidad = await producto(`ALCOHOL BAJO E2E${s}`)
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Alertas E2E${s}`, porcentaje: 10 })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: conPedido.id, cantidad: 8 }) // faltan 5
  await api(`/api/pedidos/por-comprar/a-mano/${conCantidad.id}`, 'PUT', { cantidad: 20, nota: 'Urgente' })

  await p.goto(`${BASE}/alertas?categoria=${categoria.id}`)
  const tarjeta = (prod) => p.locator('li').filter({ hasText: prod.nombre })
  await tarjeta(libre).waitFor()
  igual(await p.getByText('Llegó: registrar entrada').count(), 0, 'botones "Llegó" en Alertas')
  await tarjeta(conPedido).getByText('✓ En Por comprar').waitFor()
  await tarjeta(conCantidad).getByText('✓ En Por comprar').waitFor()
  igual(await tarjeta(libre).getByRole('button', { name: '🛒 Agregar a Por comprar' }).count(), 1, 'botón en la gasa')
  ok('Stock bajo ya no tiene "Llegó: registrar entrada"; lo que ya está en Por comprar (por pedido o a mano) dice "✓ En Por comprar"')

  await tarjeta(libre).getByRole('button', { name: '🛒 Agregar a Por comprar' }).click()
  await tarjeta(libre).getByText('✓ En Por comprar').waitFor()
  await p.screenshot({ path: path.join(DIR, 'alertas-agregar-por-comprar.png') })
  ok('un toque en "🛒 Agregar a Por comprar" lo agrega y el botón cambia a "✓ En Por comprar"')

  await p.reload()
  await tarjeta(libre).getByText('✓ En Por comprar').waitFor()
  const lista = await api('/api/pedidos/por-comprar')
  const gasa = lista.productos.find((x) => x.productoId === libre.id)
  igual(gasa?.aMano?.cantidad ?? 'sin cantidad', 'sin cantidad', 'cantidad de la gasa')
  const alcohol = lista.productos.find((x) => x.productoId === conCantidad.id)
  igual(alcohol?.aMano?.cantidad, 20, 'cantidad del alcohol')
  ok('al volver sigue "✓ En Por comprar"; la gasa quedó sin cantidad y el alcohol conserva sus 20')

  await p.goto(BASE + '/por-comprar')
  const enLista = p.locator('section').filter({ hasText: 'Agregados a mano' }).locator('li').filter({ hasText: libre.nombre })
  await enLista.getByText('sin cantidad').waitFor()
  ok('en Por comprar aparece en "Agregados a mano" con "sin cantidad"')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
