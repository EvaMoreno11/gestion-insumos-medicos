/** Una función por cada operación de la API. Las pantallas usan esto y no llaman a fetch directamente. */
import { api, descargar } from './http'
import type {
  Categoria,
  CategoriaPrecio,
  ClienteDatos,
  ClienteDetalle,
  ClienteResumen,
  EntradaDatos,
  EstadoPedido,
  Negocio,
  Numeracion,
  Pagina,
  Pedido,
  PedidoResumen,
  PorComprar,
  ProductoDatos,
  ProductoDetalle,
  ProductoResumen,
  ResultadoMovimiento,
} from './tipos'

export const productos = {
  buscar: (buscar: string, opciones: { categoriaId?: number | null; pagina?: number; tamano?: number } = {}) =>
    api<Pagina<ProductoResumen>>('/api/productos', { params: { buscar, tamano: 30, ...opciones } }),
  obtener: (id: number) => api<ProductoDetalle>(`/api/productos/${id}`),
  crear: (datos: ProductoDatos) => api<ProductoDetalle>('/api/productos', { metodo: 'POST', cuerpo: datos }),
  actualizar: (id: number, datos: ProductoDatos) =>
    api<ProductoDetalle>(`/api/productos/${id}`, { metodo: 'PUT', cuerpo: datos }),
}

export const categorias = {
  listar: () => api<Categoria[]>('/api/categorias'),
}

export const categoriasPrecio = {
  listar: () => api<CategoriaPrecio[]>('/api/categorias-precio'),
}

export const clientes = {
  buscar: (buscar: string) => api<Pagina<ClienteResumen>>('/api/clientes', { params: { buscar, tamano: 50 } }),
  obtener: (id: number) => api<ClienteDetalle>(`/api/clientes/${id}`),
  crear: (datos: ClienteDatos) => api<ClienteDetalle>('/api/clientes', { metodo: 'POST', cuerpo: datos }),
  actualizar: (id: number, datos: ClienteDatos) =>
    api<ClienteDetalle>(`/api/clientes/${id}`, { metodo: 'PUT', cuerpo: datos }),
}

export const movimientos = {
  entrada: (datos: EntradaDatos) =>
    api<ResultadoMovimiento>('/api/movimientos/entrada', { metodo: 'POST', cuerpo: datos }),
}

export interface ItemNuevo {
  productoId?: number
  descripcion?: string
  presentacion?: string
  marca?: string
  cantidad: number
  precio?: number | null
}

export interface ItemCambio {
  cantidad: number
  precio?: number | null
  precioAutomatico?: boolean
}

export const pedidos = {
  listar: (estado: EstadoPedido, tamano = 50) =>
    api<Pagina<PedidoResumen>>('/api/pedidos', { params: { estado, tamano } }),
  obtener: (id: number) => api<Pedido>(`/api/pedidos/${id}`),
  crear: (datos: { clienteId: number; localId?: number; porcentaje?: number | null; guardarPorcentajeEnCliente?: boolean }) =>
    api<Pedido>('/api/pedidos', { metodo: 'POST', cuerpo: datos }),
  actualizar: (id: number, datos: { porcentaje?: number; guardarPorcentajeEnCliente?: boolean; nota?: string | null }) =>
    api<Pedido>(`/api/pedidos/${id}`, { metodo: 'PUT', cuerpo: datos }),
  agregarItem: (id: number, item: ItemNuevo) =>
    api<Pedido>(`/api/pedidos/${id}/items`, { metodo: 'POST', cuerpo: item }),
  cambiarItem: (id: number, itemId: number, cambio: ItemCambio) =>
    api<Pedido>(`/api/pedidos/${id}/items/${itemId}`, { metodo: 'PUT', cuerpo: cambio }),
  quitarItem: (id: number, itemId: number) =>
    api<Pedido>(`/api/pedidos/${id}/items/${itemId}`, { metodo: 'DELETE' }),
  despachar: (id: number) => api<Pedido>(`/api/pedidos/${id}/despachar`, { metodo: 'POST' }),
  anular: (id: number) => api<Pedido>(`/api/pedidos/${id}/anular`, { metodo: 'POST' }),
  porComprar: () => api<PorComprar>('/api/pedidos/por-comprar'),
  nota: (id: number, formato: 'pdf' | 'xlsx') => descargar(`/api/pedidos/${id}/nota.${formato}`),
  numeracion: () => api<Numeracion>('/api/pedidos/numeracion'),
  fijarNumeracion: (siguiente: number) =>
    api<Numeracion>('/api/pedidos/numeracion', { metodo: 'PUT', cuerpo: { siguiente } }),
}

export const negocio = {
  obtener: () => api<Negocio>('/api/negocio'),
  guardar: (datos: Negocio) => api<Negocio>('/api/negocio', { metodo: 'PUT', cuerpo: datos }),
}
