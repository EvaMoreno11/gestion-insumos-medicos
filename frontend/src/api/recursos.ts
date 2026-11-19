/** Una función por cada operación de la API. Las pantallas usan esto y no llaman a fetch directamente. */
import { api, descargar } from './http'
import type {
  Categoria,
  CategoriaPrecio,
  ClienteDatos,
  ClienteDetalle,
  ClienteResumen,
  Cobranzas,
  DetallesResumen,
  DocumentoPedido,
  EntradaDatos,
  EstadoSesion,
  EstadoPedido,
  LoteAlerta,
  MedioPago,
  Negocio,
  Numeracion,
  Pagina,
  Pedido,
  PedidoResumen,
  PorComprar,
  ProductoDatos,
  ProductoDetalle,
  ProductoResumen,
  ResultadoImportacion,
  ResultadoImportacionClientes,
  ResultadoMovimiento,
  Resumen,
  TipoPeriodo,
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
  crear: (nombre: string) => api<Categoria>('/api/categorias', { metodo: 'POST', cuerpo: { nombre } }),
  renombrar: (id: number, nombre: string) =>
    api<Categoria>(`/api/categorias/${id}`, { metodo: 'PUT', cuerpo: { nombre } }),
  /** Solo si no tiene productos */
  eliminar: (id: number) => api<void>(`/api/categorias/${id}`, { metodo: 'DELETE' }),
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
  crear: (datos: {
    clienteId: number
    localId?: number
    porcentaje?: number | null
    guardarPorcentajeEnCliente?: boolean
    nota?: string | null
    documento?: DocumentoPedido
  }) => api<Pedido>('/api/pedidos', { metodo: 'POST', cuerpo: datos }),
  /** La nota (observaciones) siempre se envía: si va vacía, se borra */
  actualizar: (
    id: number,
    datos: { porcentaje?: number; guardarPorcentajeEnCliente?: boolean; nota: string | null; documento?: DocumentoPedido },
  ) => api<Pedido>(`/api/pedidos/${id}`, { metodo: 'PUT', cuerpo: datos }),
  agregarItem: (id: number, item: ItemNuevo) =>
    api<Pedido>(`/api/pedidos/${id}/items`, { metodo: 'POST', cuerpo: item }),
  cambiarItem: (id: number, itemId: number, cambio: ItemCambio) =>
    api<Pedido>(`/api/pedidos/${id}/items/${itemId}`, { metodo: 'PUT', cuerpo: cambio }),
  quitarItem: (id: number, itemId: number) =>
    api<Pedido>(`/api/pedidos/${id}/items/${itemId}`, { metodo: 'DELETE' }),
  /** Con entregarLoDisponible, si faltan unidades se entrega lo que hay y el resto pasa a un saldo */
  despachar: (id: number, entregarLoDisponible = false) =>
    api<Pedido>(`/api/pedidos/${id}/despachar`, { metodo: 'POST', cuerpo: { entregarLoDisponible } }),
  /** Un ítem escrito a mano pasa a ser un producto del inventario */
  vincularProducto: (id: number, itemId: number, productoId: number) =>
    api<Pedido>(`/api/pedidos/${id}/items/${itemId}/producto`, { metodo: 'PUT', cuerpo: { productoId } }),
  anular: (id: number) => api<Pedido>(`/api/pedidos/${id}/anular`, { metodo: 'POST' }),
  /** Cómo paga: null = sin anotar; 0 = contado; N = crédito a N días. Queda como el plazo habitual del cliente */
  definirPlazo: (id: number, plazoDias: number | null) =>
    api<Pedido>(`/api/pedidos/${id}/plazo`, { metodo: 'PUT', cuerpo: { plazoDias } }),
  registrarPago: (id: number, pago: { monto: number; medio: MedioPago; fecha?: string; nota?: string }) =>
    api<Pedido>(`/api/pedidos/${id}/pagos`, { metodo: 'POST', cuerpo: pago }),
  /** Corregir un pedido despachado: nuevas cantidades (0 = quitar), precios e ítems nuevos, con un motivo */
  corregir: (
    id: number,
    datos: {
      motivo: string
      cambios: { itemId: number; cantidad: number; precio?: number; lotes?: { loteId: number; cantidad: number }[] }[]
      nuevos: ItemNuevo[]
    },
  ) => api<Pedido>(`/api/pedidos/${id}/correcciones`, { metodo: 'POST', cuerpo: datos }),
  eliminarPago: (id: number, pagoId: number) =>
    api<Pedido>(`/api/pedidos/${id}/pagos/${pagoId}`, { metodo: 'DELETE' }),
  porComprar: () => api<PorComprar>('/api/pedidos/por-comprar'),
  nota: (id: number, formato: 'pdf' | 'xlsx') => descargar(`/api/pedidos/${id}/nota.${formato}`),
  numeracion: () => api<Numeracion>('/api/pedidos/numeracion'),
  fijarNumeracion: (siguiente: number, serie?: string) =>
    api<Numeracion>('/api/pedidos/numeracion', { metodo: 'PUT', cuerpo: { siguiente, serie } }),
}

/** Archivos para descargar: inventario completo (interno) y catálogo para clientes. */
export const exportar = {
  inventario: () => descargar('/api/exportar/inventario.xlsx'),
  /** Con clienteId: lista de precios de ese cliente; sin él, catálogo sin precios */
  catalogo: (formato: 'pdf' | 'xlsx', clienteId?: number) =>
    descargar(`/api/exportar/catalogo.${formato}${clienteId === undefined ? '' : `?clienteId=${clienteId}`}`),
}

/** Carga inicial desde las plantillas de Excel. Con soloValidar no se guarda nada. */
const subirExcel = (ruta: string, archivo: File, soloValidar: boolean) => {
  const formulario = new FormData()
  formulario.append('archivo', archivo)
  return { ruta, opciones: { metodo: 'POST' as const, formulario, params: { soloValidar } } }
}

export const importacion = {
  inventario: (archivo: File, soloValidar: boolean) => {
    const { ruta, opciones } = subirExcel('/api/importacion/inventario', archivo, soloValidar)
    return api<ResultadoImportacion>(ruta, opciones)
  },
  clientes: (archivo: File, soloValidar: boolean) => {
    const { ruta, opciones } = subirExcel('/api/importacion/clientes', archivo, soloValidar)
    return api<ResultadoImportacionClientes>(ruta, opciones)
  },
}

export const cobranzas = {
  obtener: () => api<Cobranzas>('/api/cobranzas'),
}

/** Días hacia adelante que cuenta "Por vencer". */
export const DIAS_POR_VENCER = 365

export const alertas = {
  stockBajo: () => api<ProductoResumen[]>('/api/alertas/stock-bajo'),
  porVencer: (dias = DIAS_POR_VENCER) => api<LoteAlerta[]>('/api/alertas/por-vencer', { params: { dias } }),
  vencidos: () => api<LoteAlerta[]>('/api/alertas/vencidos'),
}

/** Periodo del resumen: uno de los predefinidos, o un rango con sus fechas (aaaa-mm-dd). */
export interface ConsultaPeriodo {
  periodo: TipoPeriodo
  desde?: string
  hasta?: string
}

export const resumen = {
  obtener: (consulta: ConsultaPeriodo) => api<Resumen>('/api/resumen', { params: { ...consulta } }),
  detalles: (consulta: ConsultaPeriodo) => api<DetallesResumen>('/api/resumen/detalles', { params: { ...consulta } }),
}

export const sesion = {
  estado: () => api<EstadoSesion>('/api/auth/estado'),
  entrar: (usuario: string, clave: string, recordar: boolean) =>
    api<EstadoSesion>('/api/auth/login', { metodo: 'POST', cuerpo: { usuario, clave, recordar } }),
  crearCuenta: (usuario: string, clave: string, codigo: string) =>
    api<void>('/api/auth/configurar', { metodo: 'POST', cuerpo: { usuario, clave, codigo } }),
  cambiarClave: (actual: string, nueva: string) =>
    api<void>('/api/auth/clave', { metodo: 'PUT', cuerpo: { actual, nueva } }),
  salir: () => api<void>('/api/auth/salir', { metodo: 'POST' }),
}

export const negocio = {
  obtener: () => api<Negocio>('/api/negocio'),
  guardar: (datos: Negocio) => api<Negocio>('/api/negocio', { metodo: 'PUT', cuerpo: datos }),
}
