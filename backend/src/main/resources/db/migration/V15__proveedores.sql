-- =====================================================================
-- V15: Pagos a proveedores
--
-- Un control de lo que se le debe a cada proveedor por las compras que no se pagan al
-- contado, y de los pagos que se le van haciendo. No se relaciona con las entradas de
-- mercadería ni cambia el Resumen: las compras ya se cuentan con las entradas.
--
-- proveedor: solo el nombre. nombre_comparable (sin tildes, mayúsculas ni signos) evita
--   que el mismo proveedor se cree dos veces escrito distinto.
-- deuda_proveedor: lo que se le debe por una compra: monto, fecha y una nota opcional
--   (por ejemplo, el N° de factura).
-- pago_proveedor: cada pago hecho para una deuda (puede haber varios, en partes).
-- Solo se agregan tablas: no cambia nada de lo que ya existe.
-- =====================================================================

CREATE TABLE proveedor (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre             VARCHAR(120)   NOT NULL,
    nombre_comparable  VARCHAR(120)   NOT NULL,
    creado_en          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT uk_proveedor_nombre UNIQUE (nombre_comparable)
);

CREATE TABLE deuda_proveedor (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proveedor_id  BIGINT         NOT NULL REFERENCES proveedor (id),
    fecha         DATE           NOT NULL,
    monto         NUMERIC(12, 2) NOT NULL,
    nota          VARCHAR(200),
    creado_en     TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT ck_deuda_proveedor_monto CHECK (monto > 0)
);
CREATE INDEX ix_deuda_proveedor_proveedor ON deuda_proveedor (proveedor_id);

CREATE TABLE pago_proveedor (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    deuda_id   BIGINT         NOT NULL REFERENCES deuda_proveedor (id) ON DELETE CASCADE,
    fecha      DATE           NOT NULL,
    monto      NUMERIC(12, 2) NOT NULL,
    medio      VARCHAR(15)    NOT NULL,
    nota       VARCHAR(200),
    creado_en  TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT ck_pago_proveedor_monto CHECK (monto > 0),
    CONSTRAINT ck_pago_proveedor_medio CHECK (medio IN ('EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA'))
);
CREATE INDEX ix_pago_proveedor_deuda ON pago_proveedor (deuda_id);
