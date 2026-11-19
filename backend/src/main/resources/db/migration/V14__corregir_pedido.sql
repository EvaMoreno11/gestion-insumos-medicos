-- =====================================================================
-- V14: Corregir un pedido ya despachado
--
-- Si se entregó de más (o de menos), se cambió un precio o hay que agregar o quitar
-- un ítem, el pedido despachado se corrige con un motivo:
--   * Lo que se devuelve vuelve al mismo lote del que salió (movimiento DEVOLUCION,
--     vinculado al ítem, con el costo con que había salido).
--   * Lo que se agrega sale del inventario por FEFO, como un despacho.
--   * Un ítem quitado queda con cantidad 0 (sus movimientos siguen vinculados a él).
-- Cada corrección queda en el historial del pedido.
-- =====================================================================

ALTER TABLE movimiento DROP CONSTRAINT ck_movimiento_tipo;
ALTER TABLE movimiento ADD CONSTRAINT ck_movimiento_tipo
    CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AJUSTE', 'DEVOLUCION'));

ALTER TABLE pedido_item DROP CONSTRAINT ck_pedido_item_cantidad;
ALTER TABLE pedido_item ADD CONSTRAINT ck_pedido_item_cantidad CHECK (cantidad >= 0);

CREATE TABLE pedido_correccion (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id       BIGINT         NOT NULL REFERENCES pedido (id) ON DELETE CASCADE,
    fecha           TIMESTAMPTZ    NOT NULL,
    motivo          VARCHAR(300)   NOT NULL,
    -- Qué cambió, una línea por cambio ("Jeringa 5 ml: 10 → 8, vuelven 2 al lote L1")
    detalle         TEXT           NOT NULL,
    total_anterior  NUMERIC(12, 2) NOT NULL,
    total_nuevo     NUMERIC(12, 2) NOT NULL
);
CREATE INDEX ix_pedido_correccion_pedido ON pedido_correccion (pedido_id);
