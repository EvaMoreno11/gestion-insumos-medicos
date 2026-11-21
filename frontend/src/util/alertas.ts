import type { ProductoResumen } from '../api/tipos'
import { ordenarPorNombre } from './formato'

/** Categorías que tienen productos con stock bajo, con cuántos tiene cada una, en orden alfabético. */
export function categoriasConStockBajo(productos: ProductoResumen[]) {
  const porId = new Map<number, { id: number; nombre: string; cantidad: number }>()
  for (const p of productos) {
    const categoria = porId.get(p.categoriaId)
    if (categoria) {
      categoria.cantidad++
    } else {
      porId.set(p.categoriaId, { id: p.categoriaId, nombre: p.categoria, cantidad: 1 })
    }
  }
  return ordenarPorNombre([...porId.values()])
}
