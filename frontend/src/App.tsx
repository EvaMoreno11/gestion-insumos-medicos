import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { ClienteFormulario, Clientes } from './paginas/Clientes'
import Configuracion from './paginas/Configuracion'
import Entrada from './paginas/Entrada'
import Inicio from './paginas/Inicio'
import Inventario from './paginas/Inventario'
import PedidoDetalle from './paginas/PedidoDetalle'
import PedidoNuevo from './paginas/PedidoNuevo'
import Pedidos from './paginas/Pedidos'
import PorComprar from './paginas/PorComprar'
import ProductoDetalle from './paginas/ProductoDetalle'
import { ProductoFormulario, Productos } from './paginas/Productos'

/** Rutas de la aplicación: cada pantalla tiene su propia dirección. */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Inicio />} />

        <Route path="/inventario" element={<Inventario />} />
        <Route path="/inventario/:id" element={<ProductoDetalle />} />

        <Route path="/pedidos" element={<Pedidos />} />
        <Route path="/pedidos/nuevo" element={<PedidoNuevo />} />
        <Route path="/pedidos/:id" element={<PedidoDetalle />} />
        <Route path="/por-comprar" element={<PorComprar />} />

        <Route path="/entrada" element={<Entrada />} />

        <Route path="/clientes" element={<Clientes />} />
        <Route path="/clientes/nuevo" element={<ClienteFormulario />} />
        <Route path="/clientes/:id" element={<ClienteFormulario />} />

        <Route path="/productos" element={<Productos />} />
        <Route path="/productos/nuevo" element={<ProductoFormulario />} />
        <Route path="/productos/:id" element={<ProductoFormulario />} />

        <Route path="/configuracion" element={<Configuracion />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
