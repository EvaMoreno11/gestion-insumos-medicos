/** Tipos de los datos que devuelve y recibe la API (reflejan los DTO del backend). */

export interface Pagina<T> {
  contenido: T[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

// ---------------------------------------------------------------- catálogo

export interface Categoria {
  id: number
  nombre: string
}

export type EstadoStock = 'AGOTADO' | 'BAJO' | 'NORMAL'

export interface ProductoResumen {
  id: number
  codigo: string
  nombre: string
  marca: string
  presentacion: string
  unidadMedida: string
  categoriaId: number
  categoria: string
  stockMinimo: number
  stockDisponible: number
  /** De lo disponible, lo que esperan los pedidos pendientes */
  stockSeparado: number
  stockLibre: number
  estadoStock: EstadoStock
  costo: number | null
  activo: boolean
}

export interface Lote {
  id: number
  numeroLote: string | null
  fechaVencimiento: string | null
  cantidad: number
  vencido: boolean
  diasParaVencer: number | null
}

export interface PrecioCategoria {
  letra: string
  porcentaje: number
  precio: number
}

export interface ProductoDetalle extends ProductoResumen {
  stockVencido: number
  precios: PrecioCategoria[]
  codigosBarras: string[]
  lotes: Lote[]
}

export interface ProductoDatos {
  codigo?: string
  nombre: string
  marca: string
  categoriaId: number
  presentacion: string
  unidadMedida: string
  stockMinimo: number
  costo: number | null
  codigosBarras: string[]
}

// ---------------------------------------------------------------- precios y clientes

export interface CategoriaPrecio {
  id: number
  letra: string
  porcentaje: number
}

export interface ClienteResumen {
  id: number
  nombre: string
  rucDni: string | null
  porcentaje: number | null
  /** Letra de su categoría de precio (A, B, C...) o null */
  categoria: string | null
  activo: boolean
}

export interface Local {
  id: number
  nombre: string | null
  direccion: string | null
  contacto: string | null
  telefono: string | null
}

export interface ClienteDetalle extends ClienteResumen {
  observaciones: string | null
  locales: Local[]
}

export interface LocalDatos {
  id?: number
  nombre: string
  direccion: string
  contacto: string
  telefono: string
}

export interface ClienteDatos {
  nombre: string
  rucDni: string
  porcentaje: number | null
  observaciones: string
  locales?: LocalDatos[]
}

// ---------------------------------------------------------------- stock

export interface EntradaDatos {
  productoId: number
  numeroLote: string | null
  /** aaaa-mm-dd; del vencimiento solo cuentan el mes y el año */
  fechaVencimiento: string | null
  cantidad: number
  nota?: string
}

export interface Movimiento {
  id: number
  tipo: 'ENTRADA' | 'SALIDA' | 'AJUSTE'
  cantidad: number
  numeroLote: string | null
  fechaVencimiento: string | null
  productoNombre: string
}

export interface ResultadoMovimiento {
  productoId: number
  movimientos: Movimiento[]
  stockDisponible: number
}

// ---------------------------------------------------------------- pedidos

export type EstadoPedido = 'BORRADOR' | 'DESPACHADO' | 'ANULADO'

export type Disponibilidad = 'DISPONIBLE' | 'PARCIAL' | 'SIN_STOCK' | 'NO_REGISTRADO' | 'DESPACHADO'

export interface LoteEntregado {
  numeroLote: string | null
  fechaVencimiento: string | null
  cantidad: number
}

export interface PedidoItem {
  id: number
  productoId: number | null
  codigo: string | null
  descripcion: string
  presentacion: string | null
  marca: string | null
  cantidad: number
  precioUnitario: number | null
  precioManual: boolean
  total: number | null
  disponibilidad: Disponibilidad | null
  disponible: number | null
  faltante: number | null
  lotes: LoteEntregado[]
}

export interface Pedido {
  id: number
  numero: string | null
  estado: EstadoPedido
  clienteId: number
  cliente: string
  localId: number
  local: string | null
  direccion: string | null
  contacto: string | null
  telefono: string | null
  porcentaje: number
  categoria: string | null
  nota: string | null
  items: PedidoItem[]
  total: number
  itemsConFaltante: number
  itemsSinPrecio: number
  listoParaDespachar: boolean
  creadoEn: string
  despachadoEn: string | null
}

export interface PedidoResumen {
  id: number
  numero: string | null
  estado: EstadoPedido
  clienteId: number
  cliente: string
  local: string | null
  total: number
  cantidadItems: number
  itemsConFaltante: number
  creadoEn: string
  despachadoEn: string | null
}

export interface PedidoQueEspera {
  pedidoId: number
  cliente: string
  local: string | null
  cantidad: number
  faltante: number
}

export interface ProductoPorComprar {
  productoId: number
  codigo: string
  nombre: string
  marca: string
  presentacion: string
  stockDisponible: number
  pedido: number
  faltante: number
  pedidos: PedidoQueEspera[]
}

export interface ItemNoRegistrado {
  pedidoId: number
  cliente: string
  local: string | null
  descripcion: string
  presentacion: string | null
  marca: string | null
  cantidad: number
}

export interface PorComprar {
  productos: ProductoPorComprar[]
  noRegistrados: ItemNoRegistrado[]
}

// ---------------------------------------------------------------- configuración

export interface Negocio {
  nombre: string
  direccion: string | null
  telefono: string | null
}

export interface Numeracion {
  serie: string
  siguiente: number
  proximoNumero: string
}
