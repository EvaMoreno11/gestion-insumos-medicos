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
  /** Todos los que compraron en el periodo, del que más compró al que menos */
  mejoresClientes: ClienteTop[]
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

/** Corrección de un lote mal registrado. El costo no se corrige aquí. */
export interface CorreccionLote {
  numeroLote: string | null
  /** aaaa-mm-dd; null si no vence */
  fechaVencimiento: string | null
  /** Unidades que realmente hay; si cambia, queda un ajuste en el historial */
  cantidad: number
  /** Motivo (opcional) */
  nota?: string
  /** Si ya hay otro lote del producto con ese número, unirlos en uno solo */
  unir?: boolean
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

/** Una entrada de la lista "Entradas registradas" */
export interface EntradaRegistrada {
  id: number
  fecha: string
  cantidad: number
  costoUnitario: number | null
  nota: string | null
  numeroLote: string | null
  fechaVencimiento: string | null
  productoId: number
  productoCodigo: string
  productoNombre: string
  productoPresentacion: string
  productoMarca: string
}

/** Entradas de una búsqueda, con los totales de todo el resultado (no solo de la página) */
export interface EntradasRegistradas {
  entradas: number
  /** Cantidad × costo de cada una; las que no tienen costo no suman */
  total: number
  sinCosto: number
  dias: DiaEntradas[]
  lista: Pagina<EntradaRegistrada>
}

export interface DiaEntradas {
  /** aaaa-mm-dd (día de Lima) */
  fecha: string
  entradas: number
  total: number
  sinCosto: number
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
  /** Costo y ganancia (uso interno); null si está anulado */
  costo: CostoPedido | null
}

/**
 * Cuánto costó un pedido y cuánto se gana (uso interno, nunca va en la nota). Despachado: costo real de los
 * lotes; borrador: estimado con el costo actual de cada producto.
 */
export interface CostoPedido {
  costo: number
  /** Negativa si se vende por debajo del costo */
  ganancia: number
  /** % de ganancia sobre lo vendido con costo (1 decimal, como en el Resumen); null si nada tiene costo */
  margen: number | null
  /** Ítems que no entran en la cuenta (sin costo conocido o sin precio) */
  itemsSinCosto: number
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

/** Lo que suma un día en la lista de despachados */
export interface DiaDespacho {
  /** aaaa-mm-dd (día de Lima) */
  fecha: string
  pedidos: number
  total: number
}

/** Pedidos despachados de una búsqueda, con los totales de todo el resultado (no solo de la página) */
export interface Despachados {
  pedidos: number
  total: number
  dias: DiaDespacho[]
  lista: Pagina<PedidoResumen>
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
  /** Costo y ganancia (uso interno); null si está anulado */
  costo: CostoPedido | null
}

export interface PedidoQueEspera {
  pedidoId: number
  cliente: string
  local: string | null
  cantidad: number
  faltante: number
}

/** Un producto de "Por comprar": lo que falta para pedidos, lo agregado a mano, o las dos cosas (una sola vez) */
export interface ProductoPorComprar {
  productoId: number
  codigo: string
  nombre: string
  marca: string
  presentacion: string
  stockDisponible: number
  /** 0 si no tiene mínimo */
  stockMinimo: number
  /** Total pedido en los pedidos pendientes (0 si solo se agregó a mano) */
  pedido: number
  /** Lo que falta para los pedidos */
  faltante: number
  /** Lo que hay que comprar: el faltante más lo agregado a mano (si tiene cantidad) */
  comprar: number
  pedidos: PedidoQueEspera[]
  /** Si se agregó a mano; null si solo lo esperan pedidos */
  aMano: AgregadoAMano | null
}

/** Producto agregado a mano a "Por comprar": un recordatorio, con la cantidad opcional */
export interface AgregadoAMano {
  cantidad: number | null
  nota: string | null
  creadoEn: string
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
  /** Cuánto costaría comprar lo que falta: faltante × último costo de cada producto */
  costoTotal: number
  /** Productos que faltan pero no tienen costo: no están en costoTotal */
  productosSinCosto: number
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

// ------------------------------------------------------------------ proveedores

export interface Proveedor {
  id: number
  nombre: string
}

/** Lo que se le debe a un proveedor por una compra, y los pagos que se le van haciendo */
export interface DeudaProveedor {
  id: number
  proveedorId: number
  proveedor: string
  /** Día de la compra (aaaa-mm-dd) */
  fecha: string
  monto: number
  nota: string | null
  pagado: number
  /** Lo que falta pagar (0 si ya está pagada) */
  saldo: number
  /** Día del último pago, si ya está pagada del todo */
  pagadaEl: string | null
  pagos: PagoRegistrado[]
}

export interface DeudaProveedorDatos {
  /** Nombre del proveedor: si no existe (sin importar tildes ni mayúsculas), se crea */
  proveedor: string
  fecha: string
  monto: number
  nota?: string
}

export interface ResumenProveedores {
  porPagar: number
  deudasPorPagar: number
}

// ------------------------------------------------------------------ ganancia por cliente

export interface ClienteTop {
  clienteId: number
  nombre: string
  pedidos: number
  ventas: number
  /** Ventas con costo conocido menos su costo real */
  ganancia: number
  /** % de ganancia sobre esas ventas; null si ninguna tiene costo */
  margen: number | null
}

/** Lo que compró un cliente en lo que va del año (para su ficha) */
export interface ResumenCliente {
  desde: string
  hasta: string
  ventas: number
  pedidos: number
  ganancia: number
  margen: number | null
  /** Lo vendido que no entra en la ganancia (ítems a mano o de lotes sin costo) */
  ventasSinCosto: number
  /** Día de su último despacho; null si nunca compró */
  ultimoPedido: string | null
}
