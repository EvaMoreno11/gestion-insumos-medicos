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

**Planificadas para la primera versión**
- Pedidos ítem por ítem que muestran en tiempo real qué está disponible, qué es parcial y qué no hay.
- Despacho de pedidos en una sola transacción y exportación a Excel con lote y F.V.

**A futuro**
- Reconocer listas pegadas desde WhatsApp.
- Escaneo de códigos de barras.
- Facturación electrónica SUNAT.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 25, Spring Boot 4, Spring Data JPA, Bean Validation, Apache POI (Excel) |
| Base de datos | PostgreSQL 17, migraciones con Flyway |
| Documentación API | OpenAPI / Swagger UI (springdoc) |
| Tests | JUnit 5, MockMvc, Testcontainers |
| Frontend | React, Vite, TypeScript, Tailwind CSS, PWA *(en desarrollo)* |

## Estructura

```
├── backend/            API REST (Spring Boot)
├── frontend/           Aplicación web (React)  — próximamente
├── docs/               Documentación y plantilla de carga de inventario
└── docker-compose.yml  PostgreSQL para desarrollo local
```

El backend está organizado **por funcionalidad**: cada paquete (`categoria`, `producto`, …) contiene su entidad, repositorio, servicio, controlador y DTOs. Ver el [modelo de datos](docs/modelo-datos.md).

## API

| Método | Endpoint | Descripción |
|---|---|---|
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
- Documentación interactiva: http://localhost:8080/swagger-ui.html

`spring-boot:run` usa el perfil **dev**, que carga productos, lotes y clientes de demostración con nombres ficticios. Incluye todos los casos: stock bajo, agotado, por vencer y vencido. El `.jar` de producción no los carga.

### Tests

```bash
cd backend
./mvnw test
```

Los tests de integración levantan su propio PostgreSQL temporal con Testcontainers, así que Docker debe estar corriendo. En GitHub, la CI los ejecuta en cada *pull request*.

## Hoja de ruta

- [x] **Fase 0:** entorno, estructura del repositorio y API base
- [x] **Fase 1:** catálogo de productos y categorías, buscador e integración continua
- [x] **Fase 2:** lotes, entradas, salidas FEFO, alertas e importación desde Excel
- [x] **Precios y clientes:** costo por producto, porcentaje por cliente, locales e importación de clientes
- [ ] **Fase 3:** pedidos con precios por cliente, faltantes, lista por comprar y nota de pedido en PDF y Excel
- [ ] **Fase 4:** frontend móvil (PWA)
- [ ] **Fase 5:** autenticación y despliegue

## Autora

**Eva Moreno**, estudiante de la Universidad Nacional Mayor de San Marcos.
[GitHub: @EvaMoreno11](https://github.com/EvaMoreno11)
