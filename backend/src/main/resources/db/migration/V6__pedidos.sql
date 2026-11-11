-- =====================================================================
-- V6: Pedidos
-- Un pedido nace como BORRADOR (se puede editar y puede tener faltantes),
-- pasa a DESPACHADO cuando se entrega (ahí se descuentan los lotes y se
-- le asigna el número de nota) o a ANULADO.
-- =====================================================================

-- Numeración de la nota de pedido (una sola fila). El número se asigna al despachar.
CREATE TABLE numeracion_nota (
    id         SMALLINT   PRIMARY KEY DEFAULT 1,
    serie      VARCHAR(4) NOT NULL DEFAULT '0001',
    siguiente  BIGINT     NOT NULL DEFAULT 1,

    CONSTRAINT ck_numeracion_unica     CHECK (id = 1),
    CONSTRAINT ck_numeracion_siguiente CHECK (siguiente > 0)
);
INSERT INTO numeracion_nota DEFAULT VALUES;

-- Datos del negocio que aparecen en el encabezado de la nota (una sola fila).
-- Se guardan en la base de datos, no en el código: el repositorio es público.
CREATE TABLE negocio (
    id         SMALLINT     PRIMARY KEY DEFAULT 1,
    nombre     VARCHAR(120) NOT NULL DEFAULT '[Nombre del negocio]',
    direccion  VARCHAR(200),
    telefono   VARCHAR(40),

    CONSTRAINT ck_negocio_unico CHECK (id = 1)
);
INSERT INTO negocio DEFAULT VALUES;

CREATE TABLE pedido (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id        BIGINT        NOT NULL REFERENCES cliente (id),
    cliente_local_id  BIGINT        NOT NULL REFERENCES cliente_local (id),
    -- Porcentaje sobre el costo usado en ESTE pedido (copia del porcentaje del cliente, editable)
    porcentaje        NUMERIC(5, 2) NOT NULL,
    estado            VARCHAR(12)   NOT NULL DEFAULT 'BORRADOR',
    numero            VARCHAR(20),
    nota              VARCHAR(300),
    creado_en         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    despachado_en     TIMESTAMPTZ,

    CONSTRAINT uk_pedido_numero     UNIQUE (numero),
    CONSTRAINT ck_pedido_estado     CHECK (estado IN ('BORRADOR', 'DESPACHADO', 'ANULADO')),
    CONSTRAINT ck_pedido_porcentaje CHECK (porcentaje >= 0)
);
CREATE INDEX ix_pedido_estado ON pedido (estado, id);
CREATE INDEX ix_pedido_cliente ON pedido (cliente_id);

CREATE TABLE pedido_item (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id        BIGINT       NOT NULL REFERENCES pedido (id) ON DELETE CASCADE,
    -- NULL = ítem agregado a mano (producto que aún no está en el catálogo): no mueve stock
    producto_id      BIGINT       REFERENCES producto (id),
    -- Copia de los datos del producto al agregarlo: la nota no cambia si luego se edita el catálogo
    descripcion      VARCHAR(200) NOT NULL,
    presentacion     VARCHAR(80),
    marca            VARCHAR(80),
    cantidad         INTEGER      NOT NULL,
    -- Precio con el que se vende (NULL = falta definirlo). Se guarda en el pedido:
    -- cambiar después el costo o el porcentaje no altera los pedidos ya hechos.
    precio_unitario  NUMERIC(12, 2),
    -- TRUE = lo escribió el usuario; no se recalcula al cambiar el porcentaje del pedido
    precio_manual    BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_pedido_item_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_pedido_item_precio   CHECK (precio_unitario >= 0),
    -- Un producto aparece una sola vez por pedido (los ítems a mano sí pueden repetirse)
    CONSTRAINT uk_pedido_item_producto UNIQUE (pedido_id, producto_id)
);
CREATE INDEX ix_pedido_item_producto ON pedido_item (producto_id);

-- Las salidas de un despacho quedan vinculadas a su ítem: así se sabe qué lotes se entregaron
ALTER TABLE movimiento ADD COLUMN pedido_item_id BIGINT REFERENCES pedido_item (id);
CREATE INDEX ix_movimiento_pedido_item ON movimiento (pedido_item_id);
