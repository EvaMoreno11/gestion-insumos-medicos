import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import Alertas from './paginas/Alertas'
import { ClienteFormulario, Clientes } from './paginas/Clientes'
import Cobranzas from './paginas/Cobranzas'
import Configuracion from './paginas/Configuracion'
import Descargas from './paginas/Descargas'
import Entrada from './paginas/Entrada'
import Importar from './paginas/Importar'
import Inicio from './paginas/Inicio'
import Inventario from './paginas/Inventario'
import MasDetalles from './paginas/MasDetalles'
import PedidoDetalle from './paginas/PedidoDetalle'
import PedidoNuevo from './paginas/PedidoNuevo'
import Pedidos from './paginas/Pedidos'
import PorComprar from './paginas/PorComprar'
import ProductoDetalle from './paginas/ProductoDetalle'
import { ProductoFormulario, Productos } from './paginas/Productos'
import Resumen from './paginas/Resumen'
import { SesionProvider } from './sesion'

/** Rutas de la aplicación: cada pantalla tiene su propia dirección. Todas exigen haber iniciado sesión. */
export default function App() {
  return (
    <BrowserRouter>
      <SesionProvider>
      <Routes>
        <Route path="/" element={<Inicio />} />

        <Route path="/inventario" element={<Inventario />} />
        <Route path="/inventario/descargar" element={<Descargas />} />
        <Route path="/inventario/:id" element={<ProductoDetalle />} />

        <Route path="/pedidos" element={<Pedidos />} />
        <Route path="/pedidos/nuevo" element={<PedidoNuevo />} />
        <Route path="/pedidos/:id" element={<PedidoDetalle />} />
        <Route path="/por-comprar" element={<PorComprar />} />
        <Route path="/alertas" element={<Alertas />} />
        <Route path="/cobranzas" element={<Cobranzas />} />
        <Route path="/resumen" element={<Resumen />} />
        <Route path="/resumen/detalles" element={<MasDetalles />} />

        <Route path="/entrada" element={<Entrada />} />

        <Route path="/clientes" element={<Clientes />} />
        <Route path="/clientes/nuevo" element={<ClienteFormulario />} />
        <Route path="/clientes/:id" element={<ClienteFormulario />} />

        <Route path="/productos" element={<Productos />} />
        <Route path="/productos/nuevo" element={<ProductoFormulario />} />
        <Route path="/productos/:id" element={<ProductoFormulario />} />

        <Route path="/configuracion" element={<Configuracion />} />
        <Route path="/configuracion/importar" element={<Importar />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      </SesionProvider>
    </BrowserRouter>
  )
}
