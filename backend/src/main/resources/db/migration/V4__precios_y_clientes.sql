-- =====================================================================
-- V4: Precios y clientes
-- El precio de venta = costo del producto + el porcentaje del cliente.
-- Las categorías de precio (A, B, C...) son solo una etiqueta interna
-- para un porcentaje; nunca se muestran al cliente.
-- =====================================================================

-- Costo de compra del producto (hasta 4 decimales, como en la lista de precios)
ALTER TABLE producto
    ADD COLUMN costo NUMERIC(12, 4),
    ADD CONSTRAINT ck_producto_costo CHECK (costo >= 0);

CREATE TABLE categoria_precio (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    letra       VARCHAR(10)   NOT NULL,
    porcentaje  NUMERIC(5, 2) NOT NULL,

    CONSTRAINT uk_categoria_precio_letra      UNIQUE (letra),
    CONSTRAINT uk_categoria_precio_porcentaje UNIQUE (porcentaje),
    CONSTRAINT ck_categoria_precio_porcentaje CHECK (porcentaje >= 0)
);

INSERT INTO categoria_precio (letra, porcentaje) VALUES
    ('A', 10),
    ('B', 15),
    ('C', 20);

CREATE TABLE cliente (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          VARCHAR(200) NOT NULL,
    ruc_dni         VARCHAR(11),
    -- Incremento sobre el costo con el que se le vende. NULL = aún no definido
    -- (la app lo pide en su primer pedido).
    porcentaje      NUMERIC(5, 2),
    observaciones   VARCHAR(300),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Nombre normalizado: evita registrar dos veces al mismo cliente
    clave_unica     VARCHAR(250) NOT NULL,
    texto_busqueda  VARCHAR(300) NOT NULL,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_cliente_clave_unica UNIQUE (clave_unica),
    CONSTRAINT ck_cliente_porcentaje  CHECK (porcentaje >= 0)
);
-- Un RUC o DNI identifica a un solo cliente (cuando se conoce)
CREATE UNIQUE INDEX uk_cliente_ruc_dni ON cliente (ruc_dni) WHERE ruc_dni IS NOT NULL;

-- Puntos de entrega del cliente. Todo cliente tiene al menos uno;
-- si tiene un solo local, su nombre queda vacío.
CREATE TABLE cliente_local (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id  BIGINT NOT NULL REFERENCES cliente (id) ON DELETE CASCADE,
    nombre      VARCHAR(80),
    direccion   VARCHAR(200),
    contacto    VARCHAR(80),
    telefono    VARCHAR(30)
);
CREATE INDEX ix_cliente_local_cliente ON cliente_local (cliente_id);
