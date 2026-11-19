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
  /** Cuántos productos tiene; solo se puede eliminar con 0 */
  productos: number
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

// ---------------------------------------------------------------- resumen ejecutivo

export type TipoPeriodo = 'MES' | 'MES_ANTERIOR' | 'ANIO' | 'RANGO'

export interface Periodo {
  desde: string
  hasta: string
  anteriorDesde: string
  anteriorHasta: string
  nombre: string
  nombreAnterior: string
}

export interface Resumen {
  periodo: Periodo
  /** cobrado: de lo vendido en el periodo, lo que se ha cobrado hasta hoy */
  ventas: { total: number; pedidos: number; totalAnterior: number; pedidosAnterior: number; cobrado: number }
  /** Plata que entró en el periodo, y lo que se debe hoy */
  cobrado: { total: number; pagos: number; totalAnterior: number; porCobrar: number; vencido: number }
  ganancia: {
    ganancia: number
    /** % sobre las ventas con costo; null si no hay */
    margen: number | null
    ventasConCosto: number
    costoVendido: number
    /** Ventas que no entran en la ganancia (ítems escritos a mano o lotes sin costo) */
    ventasSinCosto: number
    gananciaAnterior: number
    margenAnterior: number | null
  }
  compras: { total: number; entradas: number; entradasSinCosto: number; totalAnterior: number }
  ventasPorMes: { mes: string; ventas: number; ganancia: number; cobrado: number }[]
}

export interface DetallesResumen {
  periodo: Periodo
  productosMasVendidos: { productoId: number; nombre: string; presentacion: string; marca: string; unidades: number; ventas: number }[]
  mejoresClientes: { clienteId: number; nombre: string; pedidos: number; ventas: number }[]
  clientesInactivos: { clienteId: number; nombre: string; ultimoPedido: string; diasSinComprar: number; totalHistorico: number }[]
  diasInactivo: number
  inventario: { valor: number; unidades: number; productosSinCosto: number; valorVencido: number; lotesVencidos: number }
  sinMovimiento: {
    productoId: number
    nombre: string
    presentacion: string
    marca: string
    stock: number
    valor: number | null
    ultimaSalida: string | null
  }[]
  diasSinMovimiento: number
  pendiente: { total: number; pedidos: number; saldos: number }
}

/** Lote por vencer o vencido, con los datos de su producto. */
export interface LoteAlerta {
  loteId: number
  numeroLote: string | null
  fechaVencimiento: string
  /** Negativo si ya venció */
  diasParaVencer: number
  cantidad: number
  productoId: number
  productoCodigo: string
  productoNombre: string
  marca: string
  presentacion: string
}

export interface Lote {
  id: number
  numeroLote: string | null
  fechaVencimiento: string | null
  cantidad: number
  /** Promedio del costo de compra de sus unidades */
  costoPromedio: number | null
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
  /** Lote de la última compra, aunque ya no tenga unidades; null si nunca hubo entradas */
  ultimoLote: Lote | null
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
  /** Locales cuyo nombre o contacto coincidió con la búsqueda (vacío si solo coincidió el nombre o el RUC) */
  localesEncontrados?: LocalEncontrado[]
}

export interface LocalEncontrado {
  nombre: string | null
  contacto: string | null
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
  /** Plazo de pago habitual: null = sin definir; 0 = contado; N = crédito a N días */
  plazoDias: number | null
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
  plazoDias: number | null
  locales?: LocalDatos[]
}

// ---------------------------------------------------------------- stock

export interface EntradaDatos {
  productoId: number
  numeroLote: string | null
  /** aaaa-mm-dd; del vencimiento solo cuentan el mes y el año */
  fechaVencimiento: string | null
  cantidad: number
  /** Costo de compra por unidad (de la boleta); pasa a ser el costo del producto */
  costo: number
  nota?: string
}

export interface Movimiento {
  id: number
  tipo: 'ENTRADA' | 'SALIDA' | 'AJUSTE' | 'DEVOLUCION'
  cantidad: number
  /** Entrada: costo de compra. Salida o ajuste: costo promedio del lote */
  costoUnitario: number | null
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

/** NOTA: nota de pedido con nombres y número. SOLO_TABLA: solo la tabla con el total (sin nombres ni número). */
export type DocumentoPedido = 'NOTA' | 'SOLO_TABLA'

export type Disponibilidad = 'DISPONIBLE' | 'PARCIAL' | 'SIN_STOCK' | 'NO_REGISTRADO' | 'DESPACHADO'

export interface LoteEntregado {
  loteId: number
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
  documento: DocumentoPedido
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
  /** Tiene faltantes, pero hay algo que entregar ya: lo que falta pasaría a un saldo */
  puedeEntregarParcial: boolean
  /** Pedido original, si este es un saldo */
  origenId: number | null
  origenNumero: string | null
  /** El pedido original y sus saldos (solo si se entregó por partes) */
  grupo: GrupoPedido | null
  creadoEn: string
  despachadoEn: string | null
  /** Pagos y plazo; null si el pedido no está despachado */
  cobro: Cobro | null
  /** Correcciones hechas después de despachar (solo para uso interno) */
  correcciones: CorreccionPedido[]
}

export interface CorreccionPedido {
  id: number
  fecha: string
  motivo: string
  /** Una línea por cambio */
  detalle: string[]
  totalAnterior: number
  totalNuevo: number
}

// ---------------------------------------------------------------- cobranzas

export type MedioPago = 'EFECTIVO' | 'YAPE' | 'PLIN' | 'TRANSFERENCIA'

/** SIN_PLAZO: se debe, pero nadie anotó todavía cómo paga el cliente */
export type EstadoCobro = 'PAGADO' | 'POR_COBRAR' | 'VENCIDO' | 'SIN_PLAZO' | 'A_FAVOR'

export interface PagoRegistrado {
  id: number
  fecha: string
  monto: number
  medio: MedioPago
  nota: string | null
}

export interface Cobro {
  total: number
  pagado: number
  saldo: number
  /** null = sin anotar; 0 = contado; N = crédito a N días desde el despacho */
  plazoDias: number | null
  vence: string | null
  estado: EstadoCobro
  /** Días que faltan para que venza (negativo: días de atraso) */
  dias: number | null
  pagos: PagoRegistrado[]
}

/** Una nota con saldo pendiente. */
export interface Deuda {
  pedidoId: number
  numero: string | null
  documento: DocumentoPedido
  clienteId: number
  cliente: string
  local: string | null
  despachado: string
  total: number
  pagado: number
  saldo: number
  plazoDias: number | null
  vence: string | null
  estado: EstadoCobro
  dias: number | null
}

export interface Cobranzas {
  totalPorCobrar: number
  totalVencido: number
  /** Clientes con al menos un cobro vencido */
  clientesVencidos: number
  vencidos: Deuda[]
  porCobrar: Deuda[]
  sinPlazo: Deuda[]
  /** Lo que los clientes pagaron de más (p. ej. tras una devolución) */
  totalAFavor: number
  /** Notas con saldo a favor del cliente (saldo negativo) */
  aFavor: Deuda[]
}

export interface GrupoPedido {
  /** Lo que pidió el cliente: entregado + pendiente + anulado */
  pedido: number
  entregado: number
  pendiente: number
  /** Saldos anulados: lo que finalmente no se entregó */
  anulado: number
  pedidos: PedidoDelGrupo[]
}

export interface PedidoDelGrupo {
  id: number
  numero: string | null
  estado: EstadoPedido
  documento: DocumentoPedido
  total: number
  esSaldo: boolean
  creadoEn: string
  despachadoEn: string | null
}

export interface PedidoResumen {
  id: number
  numero: string | null
  estado: EstadoPedido
  documento: DocumentoPedido
  clienteId: number
  cliente: string
  local: string | null
  /** Observaciones internas */
  nota: string | null
  total: number
  cantidadItems: number
  itemsConFaltante: number
  origenId: number | null
  origenNumero: string | null
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
  itemId: number
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

// ---------------------------------------------------------------- sesión

export interface EstadoSesion {
  autenticado: boolean
  usuario: string | null
  /** true si aún no existe ninguna cuenta: hay que crear la primera */
  requiereConfiguracion: boolean
  /** true si para crear la primera cuenta se pide el código de configuración */
  requiereCodigo: boolean
  negocio: string
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

// ---------------------------------------------------------------- importación desde Excel

/** Un error o aviso de la importación. fila = número de fila en Excel (0 si es del archivo completo). */
export interface Observacion {
  fila: number
  mensaje: string
}

export interface ResultadoImportacion {
  importado: boolean
  soloValidar: boolean
  filasLeidas: number
  productosCreados: number
  productosExistentes: number
  categoriasCreadas: number
  entradasRegistradas: number
  unidadesIngresadas: number
  errores: Observacion[]
  advertencias: Observacion[]
}

export interface ResultadoImportacionClientes {
  importado: boolean
  soloValidar: boolean
  filasLeidas: number
  clientesCreados: number
  localesCreados: number
  clientesExistentes: number
  errores: Observacion[]
  advertencias: Observacion[]
}
