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

**Planificadas para la primera versión**
- Stock **por lote** con fecha de vencimiento. Las salidas usan **FEFO** (*First Expired, First Out*): primero sale el lote que vence antes.
- Alertas de stock bajo y de productos próximos a vencer.
- Pedidos ítem por ítem que muestran en tiempo real qué está disponible, qué es parcial y qué no hay.
- Despacho de pedidos en una sola transacción y exportación a Excel con lote y F.V.
- Importación del inventario inicial desde Excel.

**A futuro**
- Reconocer listas pegadas desde WhatsApp.
- Escaneo de códigos de barras.
- Facturación electrónica SUNAT.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 25, Spring Boot 4, Spring Data JPA, Bean Validation |
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

`spring-boot:run` usa el perfil **dev**, que carga unos 20 productos de demostración con marcas ficticias. El `.jar` de producción no los carga.

### Tests

```bash
cd backend
./mvnw test
```

Los tests de integración levantan su propio PostgreSQL temporal con Testcontainers, así que Docker debe estar corriendo. En GitHub, la CI los ejecuta en cada *pull request*.

## Hoja de ruta

- [x] **Fase 0:** entorno, estructura del repositorio y API base
- [x] **Fase 1:** catálogo de productos y categorías, buscador e integración continua
- [ ] **Fase 2:** lotes, entradas, salidas FEFO, alertas e importación desde Excel
- [ ] **Fase 3:** pedidos, verificación de disponibilidad y exportación a Excel
- [ ] **Fase 4:** frontend móvil (PWA)
- [ ] **Fase 5:** autenticación y despliegue

## Autora

**Eva Moreno**, estudiante de la Universidad Nacional Mayor de San Marcos.
[GitHub: @EvaMoreno11](https://github.com/EvaMoreno11)
