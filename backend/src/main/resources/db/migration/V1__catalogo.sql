-- =====================================================================
-- V1: Catálogo de productos
-- Categorías, productos (cada marca/presentación es un producto distinto)
-- y sus códigos de barras (un producto puede tener varios).
-- =====================================================================

CREATE TABLE categoria (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  VARCHAR(80) NOT NULL
);
-- Sin duplicados aunque cambien mayúsculas: "Guantes" = "guantes"
CREATE UNIQUE INDEX uk_categoria_nombre ON categoria (lower(nombre));

-- Secuencia para generar códigos internos automáticos (JER-00001, GUA-00002, ...)
CREATE SEQUENCE producto_codigo_seq;

CREATE TABLE producto (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo          VARCHAR(40)  NOT NULL,
    nombre          VARCHAR(200) NOT NULL,
    marca           VARCHAR(80)  NOT NULL,
    presentacion    VARCHAR(80)  NOT NULL,
    unidad_medida   VARCHAR(20)  NOT NULL,
    stock_minimo    INTEGER      NOT NULL DEFAULT 0,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    categoria_id    BIGINT       NOT NULL REFERENCES categoria (id),
    -- Texto normalizado (minúsculas, sin tildes) para el buscador
    texto_busqueda  VARCHAR(600) NOT NULL,
    -- nombre|marca|presentación normalizados: evita registrar dos veces el mismo producto
    clave_unica     VARCHAR(400) NOT NULL,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_producto_codigo      UNIQUE (codigo),
    CONSTRAINT uk_producto_clave_unica UNIQUE (clave_unica),
    CONSTRAINT ck_producto_stock_minimo CHECK (stock_minimo >= 0)
);
CREATE INDEX ix_producto_categoria ON producto (categoria_id);

CREATE TABLE producto_codigo_barras (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id  BIGINT      NOT NULL REFERENCES producto (id) ON DELETE CASCADE,
    codigo       VARCHAR(50) NOT NULL,

    -- Un código de barras identifica a un solo producto
    CONSTRAINT uk_codigo_barras UNIQUE (codigo)
);
CREATE INDEX ix_codigo_barras_producto ON producto_codigo_barras (producto_id);
