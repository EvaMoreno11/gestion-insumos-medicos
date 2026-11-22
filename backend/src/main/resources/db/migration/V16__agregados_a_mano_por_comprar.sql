-- =====================================================================
-- V16: Productos agregados a mano a "Por comprar"
--
-- Un recordatorio para no olvidarse de comprar un producto aunque ningún pedido lo espere
-- (por ejemplo, uno de Stock bajo que urge). Uno por producto: agregarlo de nuevo cambia su
-- cantidad y su nota. La cantidad es opcional (a veces aún no se sabe cuántos). Se quita solo
-- al registrar una entrada de ese producto, o a mano.
-- Solo se agrega una tabla: no cambia nada de lo que ya existe.
-- =====================================================================

CREATE TABLE recordatorio_compra (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id  BIGINT        NOT NULL REFERENCES producto (id),
    cantidad     INTEGER,
    nota         VARCHAR(300),
    creado_en    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_recordatorio_compra_producto UNIQUE (producto_id),
    CONSTRAINT ck_recordatorio_compra_cantidad CHECK (cantidad IS NULL OR cantidad > 0)
);
