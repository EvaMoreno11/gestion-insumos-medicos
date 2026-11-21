import { describe, expect, it } from 'vitest'
import type { ProductoResumen } from '../api/tipos'
import { categoriasConStockBajo } from './alertas'

const producto = (id: number, categoriaId: number, categoria: string) =>
  ({ id, categoriaId, categoria }) as ProductoResumen

describe('stock bajo por categoría', () => {
  it('lista solo las categorías con productos en stock bajo, con cuántos, en orden alfabético', () => {
    const lista = [producto(1, 7, 'Jeringas'), producto(2, 3, 'Agujas'), producto(3, 7, 'Jeringas'), producto(4, 9, 'Apósitos')]

    expect(categoriasConStockBajo(lista)).toEqual([
      { id: 3, nombre: 'Agujas', cantidad: 1 },
      { id: 9, nombre: 'Apósitos', cantidad: 1 },
      { id: 7, nombre: 'Jeringas', cantidad: 2 },
    ])
  })

  it('sin productos no hay categorías', () => {
    expect(categoriasConStockBajo([])).toEqual([])
  })
})
