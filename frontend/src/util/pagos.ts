import type { MedioPago } from '../api/tipos'

/** Medios de pago, de clientes y a proveedores. */
export const MEDIOS: { valor: MedioPago; titulo: string }[] = [
  { valor: 'EFECTIVO', titulo: 'Efectivo' },
  { valor: 'YAPE', titulo: 'Yape' },
  { valor: 'PLIN', titulo: 'Plin' },
  { valor: 'TRANSFERENCIA', titulo: 'Transferencia' },
]

export function nombreMedio(medio: MedioPago): string {
  return MEDIOS.find((m) => m.valor === medio)?.titulo ?? medio
}
