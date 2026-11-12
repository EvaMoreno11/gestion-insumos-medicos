# Gestión de Insumos Médicos · Inventario y Pedidos

[![CI](https://github.com/EvaMoreno11/gestion-insumos-medicos/actions/workflows/ci.yml/badge.svg)](https://github.com/EvaMoreno11/gestion-insumos-medicos/actions/workflows/ci.yml)

Aplicación web *mobile-first* para gestionar el inventario y los pedidos de un pequeño negocio de distribución de insumos médicos (jeringas, agujas, guantes, gasas, etc.).

Nace de un problema real: al recibir un pedido por llamada o WhatsApp, el repartidor no siempre sabe si tiene el producto en stock, ni de qué lote o con qué fecha de vencimiento. Luego el pedido se formaliza a mano en Excel.

## Funcionalidades

**Disponibles**
- Catálogo de productos por categoría, marca y presentación, con uno o más códigos de barras.
- Código interno automático por categoría (`JER-00001`, `GUA-00002`…) o manual.
- Detección de duplicados aunque estén escritos distinto ("Gasa estéril 10x10" = "GASA ESTERIL 10 x 10").
- Buscador "¿tengo esto?" que no distingue mayúsculas ni tildes: `jer 5ml` encuentra "Jeringa 5 ml…" ([cómo funciona](docs/modelo-datos.md#cómo-funciona-el-buscador)).
- Búsqueda por código de barras.
- Stock **por lote** con fecha de vencimiento. Las salidas usan **FEFO** (*First Expired, First Out*): primero sale el lote que vence antes. Los lotes vencidos nunca se despachan.
- Entradas, salidas y ajustes con historial (kárdex). Las salidas simultáneas no pueden usar las mismas unidades, porque los lotes se bloquean con `SELECT … FOR UPDATE`.
- Semáforo de stock (agotado, bajo, normal) en el buscador.
- Alertas de stock bajo, lotes por vencer y lotes vencidos.
- Importación del inventario desde la [plantilla de Excel](docs/plantilla_inventario.xlsx): modo de validación, reporte de errores por fila y todo o nada.
- Vencimientos por mes y año: un lote que vence en 03/2027 se puede usar durante todo marzo.
- **Precios por cliente:** cada producto guarda su costo y cada cliente un porcentaje. Precio = costo + porcentaje, redondeado a 2 decimales.
- **Categorías de precio** internas (A = 10 %, B = 15 %, C = 20 %, ampliables) que nunca se muestran al cliente.
- **Clientes** con uno o varios locales de entrega, y carga inicial desde la [plantilla de clientes](docs/plantilla_clientes.xlsx).
- **Pedidos por cliente:** se arman como borrador, con el precio de cada línea calculado (costo + porcentaje) y editable, y el total siempre actualizado. También admiten ítems escritos a mano que aún no están en el catálogo.
- **Faltantes con prioridad:** se puede pedir más de lo que hay. El stock se separa para el pedido más antiguo, así dos pedidos nunca aparecen completos contando las mismas unidades.
- **Lista "Por comprar":** lo que falta de cada producto sumando todos los pedidos pendientes.
- **Despacho en una transacción:** descuenta los lotes por FEFO, asigna el número de nota y deja registrado qué lotes se entregaron.
- **Nota de pedido en PDF y Excel**, con una fila por lote y lista para imprimir en A4. No incluye costos ni porcentajes.
- **Aplicación para el celular** (se instala desde el navegador): consultar inventario, armar pedidos con el total en vivo, registrar entradas, ver lo que falta comprar y administrar clientes y productos.
- **Inicio de sesión** con Spring Security: contraseñas con BCrypt, opción "Recordarme" por 30 días, bloqueo tras 5 intentos fallidos, protección CSRF y cambio de contraseña ([reglas](docs/modelo-datos.md#fase-5-inicio-de-sesión)).
- El nombre del negocio que se muestra en la app se configura desde la propia app; no está en el código.

**Planificadas para la primera versión**
- Publicación en internet.

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
| Frontend | React 19, React Router, TypeScript, Vite, Tailwind CSS 4; instalable en el celular (manifiesto web) |
| Tests | JUnit 5, MockMvc y Testcontainers (backend); Vitest (frontend) |

## Estructura

```
├── backend/            API REST (Spring Boot)
├── frontend/           Aplicación web para el celular (React)
├── docs/               Documentación y plantilla de carga de inventario
└── docker-compose.yml  PostgreSQL para desarrollo local
```

El backend está organizado **por funcionalidad**: cada paquete (`categoria`, `producto`, …) contiene su entidad, repositorio, servicio, controlador y DTOs. Ver el [modelo de datos](docs/modelo-datos.md).

El frontend separa la comunicación con la API (`src/api`: tipos y una función por operación), los componentes reutilizables (`src/componentes`) y una pantalla por ruta (`src/paginas`). Las reglas de negocio viven en el backend: el frontend solo muestra lo que la API calcula (precios, faltantes, totales).

## API

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/auth/estado` | ¿Hay sesión? ¿Falta crear la primera cuenta? (público) |
| `POST` | `/api/auth/login` · `/configurar` · `/salir` | Entrar, crear la primera cuenta, cerrar sesión |
| `PUT` | `/api/auth/clave` | Cambiar la contraseña |
| `GET` | `/api/categorias` | Listar categorías |
| `POST` / `PUT` / `DELETE` | `/api/categorias[/{id}]` | Crear, renombrar o eliminar (solo si no tiene productos) |
| `GET` | `/api/productos?buscar=&categoriaId=&pagina=&tamano=` | Buscar productos (paginado) |
| `GET` | `/api/productos/{id}` | Detalle de un producto |
| `GET` | `/api/productos/codigo-barras/{codigo}` | Buscar por código de barras |
| `POST` / `PUT` | `/api/productos[/{id}]` | Registrar o actualizar |
| `PATCH` | `/api/productos/{id}/desactivar` · `/activar` | Desactivar o reactivar |
| `POST` | `/api/movimientos/entrada` | Ingresar unidades a un lote (lo crea si no existe) |
| `POST` | `/api/movimientos/salida` | Sacar unidades por FEFO, o de un lote específico |
| `POST` | `/api/movimientos/ajuste` | Corregir un lote a la cantidad real |
| `GET` | `/api/movimientos?productoId=` | Historial de un producto |
| `GET` | `/api/alertas/stock-bajo` · `/por-vencer?dias=` · `/vencidos` | Alertas |
| `POST` | `/api/importacion/inventario?soloValidar=` | Importar la plantilla de inventario (por defecto solo valida) |
| `POST` | `/api/importacion/clientes?soloValidar=` | Importar la plantilla de clientes (por defecto solo valida) |
| `GET` / `POST` / `PUT` | `/api/clientes[/{id}]` | Buscar, registrar y actualizar clientes y sus locales |
| `PATCH` | `/api/clientes/{id}/desactivar` · `/activar` | Desactivar o reactivar un cliente |
| `GET` / `POST` / `PUT` | `/api/categorias-precio[/{id}]` | Categorías de precio (letra y porcentaje) |
| `GET` / `POST` / `PUT` | `/api/pedidos[/{id}]` | Listar, crear y editar pedidos (borradores) |
| `POST` / `PUT` / `DELETE` | `/api/pedidos/{id}/items[/{itemId}]` | Agregar, cambiar o quitar ítems; devuelve el pedido con su total |
| `POST` | `/api/pedidos/{id}/despachar` · `/anular` | Despachar (descuenta lotes y numera la nota) o anular |
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
| `APP_CLAVE_RECORDAR` | Clave secreta larga para firmar la cookie "Recordarme". Si cambia, todos deben volver a entrar. |
| `APP_CODIGO_CONFIGURACION` | Opcional. Código que se pide para crear la primera cuenta. |

En desarrollo ambas tienen valores por defecto; en producción `APP_CLAVE_RECORDAR` debe definirse siempre.

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
- [ ] **Fase 5b:** publicación en internet

## Autora

**Eva Moreno**, estudiante de la Universidad Nacional Mayor de San Marcos.
[GitHub: @EvaMoreno11](https://github.com/EvaMoreno11)
