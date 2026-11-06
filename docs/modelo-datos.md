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

- **Stock de un producto = suma de sus lotes.** Solo cuenta como *disponible* lo que no está vencido. Un lote se puede usar hasta el mismo día de su vencimiento.
- **FEFO:** una salida toma primero del lote que vence antes y los lotes sin vencimiento van al final. Si no alcanza el stock, **no sale nada** (409).
- **Los lotes vencidos nunca se despachan.** Para sacarlos del inventario se registra un **ajuste** a 0 ("baja por vencimiento").
- **Un número de lote no se repite en el mismo producto.** Si llega una entrada con el mismo número y otra fecha de vencimiento, se rechaza, porque probablemente es un error de digitación. Solo puede haber un lote "sin número" por producto (`UNIQUE NULLS NOT DISTINCT`).
- **La cantidad de un lote solo cambia mediante un movimiento.** Los métodos que la modifican son *package-private* y solo los usa `MovimientoService`, así no hay cambios de stock sin historial.
- **Concurrencia:** las salidas bloquean los lotes que van a usar (`SELECT … FOR UPDATE`). Si dos personas despachan a la vez, la segunda espera a la primera y no se descuentan las mismas unidades dos veces.

### Importación desde Excel

`POST /api/importacion/inventario` lee la hoja **Inventario** de la [plantilla](plantilla_inventario.xlsx), con una fila por lote:

1. **Valida cada fila:** columnas obligatorias, números enteros y fechas `dd/mm/aaaa`. Si la fecha es `mm/aaaa`, se toma el último día del mes.
2. **Crea lo que falta:** categorías (reconocidas sin importar tildes) y productos (reconocidos por código o por nombre, marca y presentación).
3. **Registra una entrada** por cada fila con cantidad.
4. **Todo o nada:** todo ocurre en una transacción. Si una sola fila tiene error, no se guarda ninguna y el reporte indica fila y motivo.
5. **`soloValidar=true`** (el valor por defecto) ejecuta los mismos pasos y al final deshace todo. Así la vista previa detecta exactamente los mismos problemas que la importación real.

## Próximas fases

- **Fase 3:** `cliente`, `pedido` y `pedido_item`. Las salidas de un pedido quedarán vinculadas a sus ítems.
