# Modelo de datos

El esquema se crea con migraciones de Flyway en [`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration).

## Fase 1: Catálogo

```mermaid
erDiagram
    CATEGORIA ||--o{ PRODUCTO : agrupa
    PRODUCTO ||--o{ PRODUCTO_CODIGO_BARRAS : tiene

    CATEGORIA {
        bigint id PK
        varchar nombre "único, sin importar mayúsculas"
    }
    PRODUCTO {
        bigint id PK
        varchar codigo "único; automático si no se indica (JER-00001)"
        varchar nombre "tipo + medidas, sin la marca"
        varchar marca
        varchar presentacion "Caja x 100, Paquete x 10..."
        varchar unidad_medida "en qué se cuenta el stock"
        int stock_minimo "para alertas (>= 0)"
        boolean activo "no se borra: se desactiva"
        bigint categoria_id FK
        varchar texto_busqueda "normalizado para el buscador"
        varchar clave_unica "nombre|marca|presentación normalizados, único"
        timestamptz creado_en
        timestamptz actualizado_en
    }
    PRODUCTO_CODIGO_BARRAS {
        bigint id PK
        bigint producto_id FK
        varchar codigo "único: un código = un producto"
    }
```

### Decisiones

- **Cada marca o presentación es un producto distinto.** "Jeringa 5 ml · Marca A · Caja x 100" y "Jeringa 5 ml · Marca B · Caja x 100" son dos productos, porque los clientes piden marcas y el precio cambia.
- **Sin duplicados escritos de otra forma.** La columna `clave_unica` guarda nombre, marca y presentación *normalizados*, así que "Gasa estéril 10x10 cm" y "GASA ESTERIL 10 x 10 CM" se detectan como el mismo producto.
- **Un producto puede tener varios códigos de barras.** Por ejemplo, si el proveedor cambia el empaque. El código de barras identifica el producto, **no el lote**.
- **Los productos no se eliminan, se desactivan.** Más adelante tendrán historial de movimientos y pedidos.

### Cómo funciona el buscador

Al guardar un producto se calcula `texto_busqueda`: el código, el nombre, la marca y la presentación en minúsculas y sin tildes, con los números separados de las letras ("5ml" → "5 ml"). La búsqueda aplica la misma normalización y exige que **todas** las palabras coincidan:

| Se busca | Regla | Ejemplo |
|---|---|---|
| Palabra normal | Coincide con el **inicio** de una palabra | `jer` → **jer**inga |
| Número o letra suelta | Debe ser la palabra **completa** | `5` → "5 ml", pero no "x 50"; `m` → "talla M", pero no "Marca" |

## Fase 2: Stock por lote

```mermaid
erDiagram
    PRODUCTO ||--o{ LOTE : tiene
    LOTE ||--o{ MOVIMIENTO : registra

    LOTE {
        bigint id PK
        bigint producto_id FK
        varchar numero_lote "opcional; único por producto (en mayúsculas)"
        date fecha_vencimiento "opcional"
        int cantidad_actual ">= 0"
        timestamptz fecha_ingreso
    }
    MOVIMIENTO {
        bigint id PK
        bigint lote_id FK
        varchar tipo "ENTRADA | SALIDA | AJUSTE"
        int cantidad "positiva; en AJUSTE lleva signo"
        int stock_resultante "cantidad del lote después del movimiento"
        varchar nota
        timestamptz fecha
    }
```

### Reglas

- **Stock de un producto = suma de sus lotes.** Solo cuenta como *disponible* lo que no está vencido.
- **El vencimiento es por mes y año.** Un lote que vence en 03/2027 se puede usar durante todo marzo, así que la fecha se guarda siempre como el último día de su mes, sin importar el día con el que llegue.
- **FEFO:** una salida toma primero del lote que vence antes y los lotes sin vencimiento van al final. Si no alcanza el stock, **no sale nada** (409).
- **Los lotes vencidos nunca se despachan.** Para sacarlos del inventario se registra un **ajuste** a 0 ("baja por vencimiento").
- **Un número de lote no se repite en el mismo producto.** Si llega una entrada con el mismo número y otra fecha de vencimiento, se rechaza, porque probablemente es un error de digitación. Solo puede haber un lote "sin número" por producto (`UNIQUE NULLS NOT DISTINCT`).
- **La cantidad de un lote solo cambia mediante un movimiento.** Los métodos que la modifican son *package-private* y solo los usa `MovimientoService`, así no hay cambios de stock sin historial.
- **Concurrencia:** las salidas bloquean los lotes que van a usar (`SELECT … FOR UPDATE`). Si dos personas despachan a la vez, la segunda espera a la primera y no se descuentan las mismas unidades dos veces.

### Importación desde Excel

`POST /api/importacion/inventario` lee la hoja **Inventario** de la [plantilla](plantilla_inventario.xlsx), con una fila por lote:

1. **Valida cada fila:** columnas obligatorias, números enteros, costo y vencimiento (`mm/aaaa`; si trae día, se ignora).
2. **Crea lo que falta:** categorías (reconocidas sin importar tildes) y productos (reconocidos por código o por nombre, marca y presentación).
3. **Registra una entrada** por cada fila con cantidad, y guarda el **costo** del producto si la fila lo trae. El costo es del producto, no del lote: dos filas del mismo producto con costos distintos son un error.
4. **Todo o nada:** todo ocurre en una transacción. Si una sola fila tiene error, no se guarda ninguna y el reporte indica fila y motivo.
5. **`soloValidar=true`** (el valor por defecto) ejecuta los mismos pasos y al final deshace todo. Así la vista previa detecta exactamente los mismos problemas que la importación real.

## Precios y clientes

```mermaid
erDiagram
    CLIENTE ||--|{ CLIENTE_LOCAL : "entrega en"
    PRODUCTO {
        numeric costo "costo de compra (opcional)"
    }
    CATEGORIA_PRECIO {
        bigint id PK
        varchar letra "A, B, C... (única)"
        numeric porcentaje "10, 15, 20... (único)"
    }
    CLIENTE {
        bigint id PK
        varchar nombre "único sin importar tildes ni mayúsculas"
        varchar ruc_dni "opcional; único si existe"
        numeric porcentaje "opcional: incremento sobre el costo"
        varchar observaciones
        boolean activo
    }
    CLIENTE_LOCAL {
        bigint id PK
        bigint cliente_id FK
        varchar nombre "p. ej. el nombre de la botica; opcional si es el único local"
        varchar direccion
        varchar contacto
        varchar telefono
        varchar texto_busqueda "nombre y contacto normalizados"
    }
```

### Reglas

- **Precio de venta = costo × (1 + porcentaje del cliente)**, redondeado a 2 decimales (`HALF_UP`). Ejemplo: costo 4.80 y 15 % → 5.52.
- **El porcentaje se guarda en el cliente como número.** Las categorías de precio solo le ponen una letra a un porcentaje (A = 10 %) para mostrarlo de forma discreta en la app. Un cliente con un porcentaje que no corresponde a ninguna letra, por ejemplo 12.5, es válido.
- **Las categorías son una tabla**, no un valor fijo en el código: se puede agregar una D o cambiar un porcentaje sin programar.
- **El costo, el porcentaje y la letra son datos internos**: no aparecerán en la nota de pedido que recibe el cliente.
- **Un cliente sin porcentaje** es válido: la app lo pedirá en su primer pedido.
- **Todo cliente tiene al menos un local.** Si tiene varios, cada uno necesita un nombre distinto; así el pedido siempre se registra para un punto de entrega con su dirección y su contacto.
- **Los clientes no se eliminan, se desactivan**, igual que los productos.
- **Buscador de clientes.** Encuentra por razón social, RUC o DNI, nombre del local o contacto, con las mismas reglas que el de productos: sin tildes ni mayúsculas, por inicio de palabra, y cada palabra puede estar en cualquiera de esos datos. Muchas boticas tienen un RUC a nombre de una persona, así que buscar "santa rosa" encuentra al cliente por su local "Botica Santa Rosa". Cada resultado indica qué locales coincidieron (`localesEncontrados`), pero el local del pedido siempre se elige a mano.

### Importación de clientes

`POST /api/importacion/clientes` lee la hoja **Clientes** de la [plantilla](plantilla_clientes.xlsx), con **una fila por local**: las filas con el mismo nombre forman un solo cliente. El RUC y el porcentaje son del cliente, así que no pueden diferir entre sus filas. Funciona igual que la de inventario: valida primero, todo o nada, y los clientes que ya existen no se modifican.

## Fase 3: Pedidos

```mermaid
erDiagram
    CLIENTE ||--o{ PEDIDO : hace
    CLIENTE_LOCAL ||--o{ PEDIDO : "se entrega en"
    PEDIDO ||--o{ PEDIDO_ITEM : contiene
    PRODUCTO ||--o{ PEDIDO_ITEM : "se pide en"
    PEDIDO_ITEM ||--o{ MOVIMIENTO : "sale en"

    PEDIDO {
        bigint id PK
        bigint cliente_id FK
        bigint cliente_local_id FK
        numeric porcentaje "copia del porcentaje del cliente, editable"
        varchar estado "BORRADOR | DESPACHADO | ANULADO"
        varchar numero "0001-007323; se asigna al despachar"
        varchar nota
        timestamptz despachado_en
    }
    PEDIDO_ITEM {
        bigint id PK
        bigint pedido_id FK
        bigint producto_id FK "NULL = ítem agregado a mano"
        varchar descripcion "copia del nombre del producto"
        varchar presentacion
        varchar marca
        int cantidad "> 0; puede superar el stock"
        numeric precio_unitario "NULL = falta definirlo"
        boolean precio_manual "true = lo escribió el usuario"
    }
    NUMERACION_NOTA {
        varchar serie
        bigint siguiente
    }
    NEGOCIO {
        varchar nombre
        varchar direccion
        varchar telefono
    }
```

### Ciclo de vida

```mermaid
stateDiagram-v2
    [*] --> BORRADOR: crear
    BORRADOR --> BORRADOR: agregar, cambiar o quitar ítems
    BORRADOR --> DESPACHADO: despachar (completo y con precios)
    BORRADOR --> ANULADO: anular
    DESPACHADO --> [*]
    ANULADO --> [*]
```

### Reglas

- **El precio se guarda en el pedido.** Al agregar un ítem se calcula (costo + porcentaje del pedido) y queda escrito en la línea. Cambiar después el costo del producto o el porcentaje del cliente no altera los pedidos ya hechos.
- **Precio a mano.** Cualquier línea admite un precio escrito por el usuario (`precio_manual`). Al cambiar el porcentaje del pedido se recalculan solo los precios automáticos.
- **Primer pedido de un cliente.** Si el cliente aún no tiene porcentaje, hay que indicarlo al crear el pedido y se le guarda para los siguientes.
- **Se puede pedir más de lo que hay.** El total se calcula con la cantidad pedida y el ítem indica cuánto falta. El pedido queda en borrador hasta que llegue la mercadería.
- **Prioridad por orden de llegada.** El stock disponible se reparte entre los borradores del más antiguo al más nuevo (`RepartoDeStock`). Con 10 unidades, si el primer pedido pide 8 y el segundo 6, el primero está completo y al segundo le faltan 4. Es un cálculo, no una reserva física: el stock real solo baja al despachar.
- **El inventario muestra lo separado.** Cada producto informa `stockDisponible`, `stockSeparado` (lo que esperan los borradores) y `stockLibre`. El inventario conoce ese dato mediante la interfaz `StockComprometido`, que implementa el módulo de pedidos: así `producto` no depende de `pedido`.
- **Ítems a mano.** Un ítem sin `producto_id` es algo que aún no está en el catálogo: aparece en la nota y en "Por comprar", pero no mueve stock.
- **Despachar es todo o nada.** Exige ítems, precios y cero faltantes. En una sola transacción toma el número de nota (bloqueando la fila de numeración), genera las salidas por FEFO vinculadas a cada ítem y marca el pedido como despachado.
- **Los lotes se eligen al despachar**, no al tomar el pedido: así no importa que entre tanto hayan llegado lotes nuevos.
- **Un pedido despachado no se modifica.** Solo los borradores se editan o se anulan.

### Nota de pedido

`GET /api/pedidos/{id}/nota.pdf` y `/nota.xlsx` generan el mismo documento (`NotaPedido`) en dos formatos. Cuando un ítem salió de varios lotes hay **una fila por lote**. No incluye costo, porcentaje ni categoría del cliente. Los datos del encabezado (nombre, dirección y teléfono del negocio) se guardan en la tabla `negocio` y se editan por API: no están en el código.

## Fase 5: Inicio de sesión

```mermaid
erDiagram
    USUARIO {
        bigint id PK
        varchar usuario "único sin distinguir mayúsculas"
        varchar clave_hash "BCrypt, con prefijo {bcrypt}"
        boolean activo
        int intentos_fallidos
        timestamptz bloqueado_hasta
        timestamptz ultimo_ingreso
    }
```

### Reglas

- **Toda la API pide sesión**, salvo `/api/health` y las rutas de entrada (`/api/auth/estado`, `/login`, `/configurar`). La documentación Swagger también.
- **Primera cuenta.** Si la tabla está vacía, la app pide crear la cuenta (`POST /api/auth/configurar`). Después esa ruta responde `409`: las siguientes cuentas no se pueden crear desde fuera. Si el servidor define `APP_CODIGO_CONFIGURACION`, crear la primera cuenta exige ese código, para que nadie se adelante al publicarla.
- **Contraseñas.** Mínimo 8 caracteres. Se guardan con BCrypt, nunca en texto plano. El mensaje de error es siempre "Usuario o contraseña incorrectos", sin revelar si el usuario existe.
- **Bloqueo.** Tras 5 intentos fallidos la cuenta se bloquea 15 minutos (`429`).
- **Sesión.** Cookie `JSESSIONID` (`HttpOnly`, `SameSite=Lax`) que vence tras 12 horas sin uso. Con "Recordarme" se agrega la cookie `recordar`, firmada con `APP_CLAVE_RECORDAR`, que dura 30 días. Cambiar la contraseña invalida las cookies `recordar` de los demás dispositivos.
- **CSRF.** Toda operación que cambia datos debe reenviar en la cabecera `X-XSRF-TOKEN` el token de la cookie `XSRF-TOKEN`. El frontend lo hace en `src/api/http.ts`.
- **Permisos.** Todas las cuentas tienen los mismos. La tabla admite más usuarios para el futuro.

## Próximas fases

- **Fase 5 (segunda parte):** publicación en internet.
