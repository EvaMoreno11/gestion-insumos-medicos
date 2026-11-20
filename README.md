# Gestión de Insumos Médicos · Inventario y Pedidos

[![CI](https://github.com/EvaMoreno11/gestion-insumos-medicos/actions/workflows/ci.yml/badge.svg)](https://github.com/EvaMoreno11/gestion-insumos-medicos/actions/workflows/ci.yml)

Aplicación web *mobile-first* para gestionar el inventario y los pedidos de un pequeño negocio de distribución de insumos médicos (jeringas, agujas, guantes, gasas, etc.).

Nace de un problema real: al recibir un pedido por llamada o WhatsApp, el repartidor no siempre sabe si tiene el producto en stock, ni de qué lote o con qué fecha de vencimiento. Luego el pedido se formaliza a mano en Excel.

## Funcionalidades

**Disponibles**
- Catálogo de productos por categoría, marca y presentación, con uno o más códigos de barras.
- **Categorías** administrables desde la app: se crean al registrar un producto (botón "+ Nueva") o en Productos → Categorías, donde se renombran y se eliminan las que no tienen productos. Cada una muestra cuántos productos tiene, y no se repiten nombres aunque cambien las mayúsculas o las tildes ("ALGODON" = "Algodón").
- Código interno automático por categoría (`JER-00001`, `GUA-00002`…) o manual.
- Detección de duplicados aunque estén escritos distinto ("Gasa estéril 10x10" = "GASA ESTERIL 10 x 10").
- Buscador "¿tengo esto?" que no distingue mayúsculas ni tildes: `jer 5ml` encuentra "Jeringa 5 ml…" ([cómo funciona](docs/modelo-datos.md#cómo-funciona-el-buscador)).
- Búsqueda por código de barras.
- Stock **por lote** con fecha de vencimiento. Las salidas usan **FEFO** (*First Expired, First Out*): primero sale el lote que vence antes. Los lotes vencidos nunca se despachan. Entre lotes que vencen el mismo mes se parte lo menos posible: si uno alcanza solo, sale todo de ese (el más chico que alcance), así no se abre una caja cerrada cuando bastan las sueltas, y un pedido de una caja entera se lleva la caja.
- **Corregir un lote** mal registrado, en la app o en el Excel importado: número, vencimiento y cantidad. El cambio de cantidad queda como ajuste en el historial, con su motivo. Si el número nuevo ya es de otro lote del producto, la app avisa y los une en uno solo, sumando las unidades. Las notas ya despachadas muestran el lote corregido.
- Entradas (compras) con su **costo** y un botón para reutilizar el lote de la última compra, salidas y ajustes con historial (kárdex), y un registro de las **últimas entradas** de todo el inventario. El producto toma el último costo de compra y cada lote guarda su costo promedio, así se sabe cuánto costó lo vendido. Las salidas simultáneas no pueden usar las mismas unidades, porque los lotes se bloquean con `SELECT … FOR UPDATE`.
- Semáforo de stock (agotado, bajo, normal) en el buscador.
- **Descargar inventario y catálogo** (desde Consultar inventario): el inventario completo en Excel, con todos los productos aunque no tengan stock, sus costos y una hoja de lotes (uso interno); y el catálogo para clientes en PDF o Excel, por categoría, sin precios o con la lista de precios de un cliente (costo + su porcentaje). El catálogo incluye solo los productos con stock mínimo.
- **Corregir un pedido despachado** (devoluciones, entregas de más o de menos, precios): se cambian cantidades, precios e ítems con un motivo. Lo devuelto vuelve al lote del que salió (si salió de varios, la app pregunta a cuál), lo agregado sale del inventario, la nota mantiene su número y queda un historial de correcciones. Si el cliente ya había pagado más que el nuevo total, se muestra su saldo a favor.
- **Cobranzas:** cada pedido despachado queda por cobrar. Después se anota el plazo (contado o crédito a N días, que queda como el habitual del cliente) y los pagos parciales o totales (efectivo, Yape, Plin o transferencia). La pantalla separa lo vencido, lo que está por cobrar y lo que aún no tiene plazo, y el inicio avisa cuántos clientes tienen cobros vencidos.
- **Resumen ejecutivo:** ventas, ganancia bruta con margen, lo cobrado y compras del periodo (este mes, el mes pasado, este año o un rango de fechas), comparados con el periodo anterior, y un gráfico de ventas de los últimos 12 meses. En **Más detalles**: productos más vendidos, mejores clientes, clientes que dejaron de comprar, valor del inventario, productos sin movimiento, vencido sin vender y pendiente por entregar.
- Pantalla de **Alertas** con tres pestañas: productos con stock igual o menor a su mínimo (para saber qué comprar; los que no tienen mínimo no avisan, por ejemplo si están descontinuados), lotes que vencen en el próximo año y lotes vencidos con unidades.
- **Importar desde Excel** (Configuración → Importar desde Excel): el inventario con la [plantilla de inventario](docs/plantilla_inventario.xlsx) y los clientes con la [plantilla de clientes](docs/plantilla_clientes.xlsx). Primero se valida sin guardar nada y se ve el reporte de errores por fila; la importación es todo o nada.
- Vencimientos por mes y año: un lote que vence en 03/2027 se puede usar durante todo marzo.
- **Precios por cliente:** cada producto guarda su costo y cada cliente un porcentaje. Precio = costo + porcentaje, redondeado a 2 decimales.
- **Categorías de precio** internas (A = 12 %, B = 15 %, C = 20 %, ampliables) que nunca se muestran al cliente.
- **Clientes** con uno o varios locales de entrega. Se buscan por razón social, RUC, nombre del local (p. ej. el de la botica) o contacto.
- **Pedidos por cliente:** se arman como borrador, con el precio de cada línea calculado (costo + porcentaje) y editable, y el total siempre actualizado. También admiten ítems escritos a mano que aún no están en el catálogo.
- **Faltantes con prioridad:** se puede pedir más de lo que hay. El stock se separa para el pedido más antiguo, así dos pedidos nunca aparecen completos contando las mismas unidades.
- **Lista "Por comprar":** lo que falta de cada producto sumando todos los pedidos pendientes, y cuánto costaría comprarlo todo (lo que falta × el último costo de cada producto).
- **Despacho en una transacción:** descuenta los lotes por FEFO, asigna el número de nota y deja registrado qué lotes se entregaron.
- **Entregas parciales:** si faltan unidades, se puede entregar lo que hay. Lo que falta pasa a un pedido nuevo (saldo) con los mismos precios y la prioridad del original. La app muestra lo pedido, lo entregado y lo pendiente; el cliente solo recibe la nota de lo que se le entrega.
- **Nota de pedido en PDF y Excel**, con una fila por lote y lista para imprimir en A4. El total tiene espacio para montos de hasta 6 cifras. El lote cabe en una sola línea hasta unos 11 caracteres. No incluye costos ni porcentajes. En el celular, el PDF (y el del catálogo) se abre en una **vista previa** dentro de la app, como en WhatsApp, con el botón Compartir (WhatsApp, Imprimir, Archivos); en la PC se abre en otra pestaña del navegador.
- **Pedidos "Solo tabla":** se entregan con un documento que solo tiene la tabla de productos y el total (sin nombres, fecha ni número). Descuentan el inventario igual que cualquier pedido y no gastan la numeración de las notas.
- **Ítems escritos a mano → productos:** un ítem que no estaba en el inventario se registra como producto desde el pedido (con el formulario ya lleno) o se cambia por uno existente, y la línea conserva su cantidad y su precio.
- **Aplicación para el celular** (se instala desde el navegador): consultar inventario, armar pedidos con el total en vivo, registrar entradas, ver lo que falta comprar y administrar clientes y productos. Cuando se publica una versión nueva, la app lo avisa y se actualiza con un toque (en el iPhone, la app instalada queda abierta con la versión anterior).
- **Inicio de sesión** con Spring Security: contraseñas con BCrypt, opción "Recordarme" por 30 días, bloqueo tras 5 intentos fallidos, protección CSRF y cambio de contraseña ([reglas](docs/modelo-datos.md#fase-5-inicio-de-sesión)).
- El nombre del negocio y la serie y el número de las notas se configuran desde la propia app; no están en el código.
- **Lista para publicar en internet:** una sola imagen Docker entrega la aplicación web y la API, con Render y Neon en sus planes gratuitos ([guía paso a paso](docs/despliegue.md)) y un script de copias de seguridad.

**A futuro**
- Reconocer listas pegadas desde WhatsApp.
- Escaneo de códigos de barras.
- Facturación electrónica SUNAT.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 25, Spring Boot 4, Spring Data JPA, Spring Security, Bean Validation, Apache POI (Excel), OpenPDF |
| Base de datos | PostgreSQL 17, migraciones con Flyway |
| Documentación API | OpenAPI / Swagger UI (springdoc) |
| Frontend | React 19, React Router, TypeScript, Vite, Tailwind CSS 4, pdf.js (vista previa de PDF, se carga solo al usarla); instalable en el celular (manifiesto web) |
| Tests | JUnit 5, MockMvc y Testcontainers (backend); Vitest (frontend) |
| Publicación | Docker (con caché AOT de Java para arrancar más rápido), Render y Neon |

## Estructura

```
├── backend/            API REST (Spring Boot)
├── frontend/           Aplicación web para el celular (React)
├── docs/               Documentación, plantillas de carga y guía de publicación
├── scripts/            Copia de seguridad de la base de producción
├── docker-compose.yml  PostgreSQL para desarrollo local
├── Dockerfile          Imagen de producción (aplicación web + API)
└── render.yaml         Configuración del servidor en Render
```

El backend está organizado **por funcionalidad**: cada paquete (`categoria`, `producto`, …) contiene su entidad, repositorio, servicio, controlador y DTOs. Ver el [modelo de datos](docs/modelo-datos.md).

El frontend separa la comunicación con la API (`src/api`: tipos y una función por operación), los componentes reutilizables (`src/componentes`) y una pantalla por ruta (`src/paginas`). Las reglas de negocio viven en el backend: el frontend solo muestra lo que la API calcula (precios, faltantes, totales).

## API

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/auth/estado` | ¿Hay sesión? ¿Falta crear la primera cuenta? (público) |
| `POST` | `/api/auth/login` · `/configurar` · `/salir` | Entrar, crear la primera cuenta, cerrar sesión |
| `PUT` | `/api/auth/clave` | Cambiar la contraseña |
| `GET` | `/api/categorias` | Listar categorías, con cuántos productos tiene cada una |
| `POST` / `PUT` / `DELETE` | `/api/categorias[/{id}]` | Crear, renombrar o eliminar (solo si no tiene productos) |
| `GET` | `/api/productos?buscar=&categoriaId=&pagina=&tamano=` | Buscar productos (paginado) |
| `GET` | `/api/productos/{id}` | Detalle de un producto |
| `GET` | `/api/productos/codigo-barras/{codigo}` | Buscar por código de barras |
| `POST` / `PUT` | `/api/productos[/{id}]` | Registrar o actualizar |
| `PATCH` | `/api/productos/{id}/desactivar` · `/activar` | Desactivar o reactivar |
| `POST` | `/api/movimientos/entrada` | Ingresar unidades a un lote (lo crea si no existe) |
| `POST` | `/api/movimientos/salida` | Sacar unidades por FEFO, o de un lote específico |
| `POST` | `/api/movimientos/ajuste` | Corregir un lote a la cantidad real |
| `PUT` | `/api/lotes/{id}` | Corregir número, vencimiento y cantidad de un lote; con `"unir": true` lo une con otro del mismo número |
| `GET` | `/api/movimientos?productoId=` | Historial de un producto |
| `GET` | `/api/alertas/stock-bajo` · `/por-vencer?dias=` · `/vencidos` | Alertas |
| `POST` | `/api/pedidos/{id}/correcciones` | Corregir un pedido despachado (cantidades, precios e ítems, con un motivo) |
| `PUT` | `/api/pedidos/{id}/plazo` | Anotar cómo paga un pedido despachado (contado o crédito a N días) |
| `POST` / `DELETE` | `/api/pedidos/{id}/pagos[/{pagoId}]` | Registrar un pago, o borrar uno mal registrado |
| `GET` | `/api/exportar/inventario.xlsx` · `/catalogo.pdf` · `/catalogo.xlsx?clienteId=` | Inventario completo (interno) y catálogo para clientes |
| `GET` | `/api/cobranzas` | Notas con saldo pendiente: vencidas, por cobrar y sin plazo |
| `GET` | `/api/resumen?periodo=` · `/api/resumen/detalles?periodo=` | Resumen ejecutivo (`MES`, `MES_ANTERIOR`, `ANIO` o `RANGO` con `desde` y `hasta`) |
| `POST` | `/api/importacion/inventario?soloValidar=` | Importar la plantilla de inventario (por defecto solo valida) |
| `POST` | `/api/importacion/clientes?soloValidar=` | Importar la plantilla de clientes (por defecto solo valida) |
| `GET` / `POST` / `PUT` | `/api/clientes[/{id}]` | Buscar (por nombre, RUC, local o contacto), registrar y actualizar clientes y sus locales |
| `PATCH` | `/api/clientes/{id}/desactivar` · `/activar` | Desactivar o reactivar un cliente |
| `GET` / `POST` / `PUT` | `/api/categorias-precio[/{id}]` | Categorías de precio (letra y porcentaje) |
| `GET` / `POST` / `PUT` | `/api/pedidos[/{id}]` | Listar, crear y editar pedidos (borradores) |
| `POST` / `PUT` / `DELETE` | `/api/pedidos/{id}/items[/{itemId}]` | Agregar, cambiar o quitar ítems; devuelve el pedido con su total |
| `PUT` | `/api/pedidos/{id}/items/{itemId}/producto` | Convertir un ítem escrito a mano en un producto del inventario |
| `POST` | `/api/pedidos/{id}/despachar` · `/anular` | Despachar (descuenta lotes y numera la nota) o anular. Con `{"entregarLoDisponible": true}` entrega lo que hay y deja el resto en un saldo |
| `GET` | `/api/pedidos/por-comprar` | Lista universal de lo que falta comprar |
| `GET` | `/api/pedidos/{id}/nota.pdf` · `/nota.xlsx` | Nota de pedido para imprimir, enviar o facturar |
| `GET` / `PUT` | `/api/pedidos/numeracion` | Número de la próxima nota de pedido |
| `GET` / `PUT` | `/api/negocio` | Datos del negocio para el encabezado de la nota |

Todas las rutas, salvo `/api/health` y las de `/api/auth` marcadas como públicas, exigen sesión (`401` sin ella). Las que cambian datos exigen además el token CSRF.

Los errores siguen el estándar *Problem Details* (RFC 9457). Por ejemplo, un `400` indica qué campo falló y por qué.

## Cómo ejecutarlo en local

Requisitos: **JDK 25** y **Docker Desktop**. Maven no es necesario, porque se usa el wrapper `mvnw`.

```bash
# 1. Levantar la base de datos (puerto 5433)
docker compose up -d

# 2. Ejecutar la API (puerto 8080)
cd backend
./mvnw spring-boot:run        # en Windows: mvnw.cmd spring-boot:run
```

- Estado de la API: http://localhost:8080/api/health

`spring-boot:run` usa el perfil **dev**, que carga productos, lotes, clientes y pedidos de demostración con nombres ficticios. Incluye todos los casos: stock bajo, agotado, por vencer y vencido. El `.jar` de producción no los carga.

### Aplicación (frontend)

Requisito: **Node 24**. Con la API corriendo:

```bash
cd frontend
npm install
npm run dev
```

- En la PC: http://localhost:5173
- **En el celular**, conectado al mismo wifi: abre la dirección *Network* que muestra `npm run dev` (por ejemplo `http://192.168.1.20:5173`). En iPhone, para instalarla: Safari → Compartir → **Agregar a inicio**.

En desarrollo, Vite reenvía las llamadas a `/api` al backend (puerto 8080), así que no hace falta configurar nada más.

La primera vez, la app pide **crear la cuenta** (usuario y contraseña). Con la sesión iniciada, la documentación interactiva de la API queda en http://localhost:5173/swagger-ui.html.

### Variables de entorno (producción)

| Variable | Para qué sirve |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión a PostgreSQL (`jdbc:postgresql://HOST/BASE?sslmode=require`). |
| `APP_CLAVE_RECORDAR` | Clave secreta larga para firmar la cookie "Recordarme". Si cambia, todos deben volver a entrar. |
| `APP_CODIGO_CONFIGURACION` | Opcional. Código que se pide para crear la primera cuenta. |

En desarrollo todas tienen valores por defecto; en producción `APP_CLAVE_RECORDAR` debe definirse siempre. El perfil **prod** (que activa el `Dockerfile`) agrega lo propio del servidor: cookies solo por HTTPS, encabezados del proxy de Render y conexiones que se cierran solas para que Neon pueda dormir.

### Publicar en internet

Ver la [guía de publicación](docs/despliegue.md): base de datos en Neon, servidor en Render (con `render.yaml`), primera configuración, importación de los datos, instalación en el celular y copias de seguridad. Para probar la imagen en local:

```bash
docker build -t inventario .
docker run -p 8080:8080 -e DB_URL=jdbc:postgresql://host.docker.internal:5433/inventario -e APP_CLAVE_RECORDAR=una-clave-larga inventario
```

### Tests

```bash
cd backend
./mvnw test
```

Los tests de integración levantan su propio PostgreSQL temporal con Testcontainers, así que Docker debe estar corriendo.

```bash
cd frontend
npm run lint && npm test && npm run build
```

En GitHub, la CI ejecuta ambos grupos en cada *pull request*.

## Hoja de ruta

- [x] **Fase 0:** entorno, estructura del repositorio y API base
- [x] **Fase 1:** catálogo de productos y categorías, buscador e integración continua
- [x] **Fase 2:** lotes, entradas, salidas FEFO, alertas e importación desde Excel
- [x] **Precios y clientes:** costo por producto, porcentaje por cliente, locales e importación de clientes
- [x] **Fase 3:** pedidos con precios por cliente, faltantes, lista por comprar y nota de pedido en PDF y Excel
- [x] **Fase 4:** aplicación para el celular
- [x] **Fase 5a:** inicio de sesión
- [x] **Fase 5b:** preparación para publicar en internet (Docker, Render, Neon e importación desde la app)

## Autora

**Eva Moreno**, estudiante de la Universidad Nacional Mayor de San Marcos.
[GitHub: @EvaMoreno11](https://github.com/EvaMoreno11)
