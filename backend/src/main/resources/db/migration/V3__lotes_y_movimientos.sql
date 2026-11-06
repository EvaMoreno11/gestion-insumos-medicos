-- =====================================================================
-- V3: Stock por lote y movimientos de inventario
-- El stock de un producto = suma de las cantidades de sus lotes.
-- Cada cambio de cantidad queda registrado en "movimiento" (historial).
-- =====================================================================

CREATE TABLE lote (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id        BIGINT      NOT NULL REFERENCES producto (id),
    numero_lote        VARCHAR(50),             -- opcional: hay productos sin lote
    fecha_vencimiento  DATE,                    -- opcional: hay productos que no vencen
    cantidad_actual    INTEGER     NOT NULL DEFAULT 0,
    fecha_ingreso      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_lote_cantidad CHECK (cantidad_actual >= 0),
    -- Un número de lote no se repite dentro del mismo producto.
    -- NULLS NOT DISTINCT: solo puede haber UN lote "sin número" por producto.
    CONSTRAINT uk_lote_producto_numero UNIQUE NULLS NOT DISTINCT (producto_id, numero_lote)
);
CREATE INDEX ix_lote_producto_vencimiento ON lote (producto_id, fecha_vencimiento);

CREATE TABLE movimiento (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lote_id           BIGINT       NOT NULL REFERENCES lote (id),
    tipo              VARCHAR(10)  NOT NULL,
    -- ENTRADA y SALIDA: cantidad positiva. AJUSTE: diferencia con signo (+3 o -2).
    cantidad          INTEGER      NOT NULL,
    -- Cantidad que quedó en el lote después del movimiento
    stock_resultante  INTEGER      NOT NULL,
    nota              VARCHAR(300),
    fecha             TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_movimiento_tipo CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AJUSTE')),
    CONSTRAINT ck_movimiento_cantidad CHECK (cantidad <> 0 AND (tipo = 'AJUSTE' OR cantidad > 0)),
    CONSTRAINT ck_movimiento_stock_resultante CHECK (stock_resultante >= 0)
);
CREATE INDEX ix_movimiento_lote_fecha ON movimiento (lote_id, fecha DESC);
