-- =====================================================================
-- V9: Entregas parciales y pedidos "Solo tabla"
--
-- documento: con qué documento se entrega el pedido.
--   NOTA        nota de pedido con el nombre del negocio, el cliente y un número correlativo.
--   SOLO_TABLA  solo la tabla de productos con el total: sin nombres, sin fecha y sin número
--               (no consume la numeración de las notas).
--
-- origen_id: si un pedido se entregó incompleto, lo que faltaba pasa a un pedido
--   nuevo (el "saldo"), que apunta al pedido original. Todos los saldos de un pedido
--   apuntan al mismo original, y conservan su antigüedad para repartir el stock.
-- =====================================================================

ALTER TABLE pedido ADD COLUMN documento VARCHAR(12) NOT NULL DEFAULT 'NOTA';
ALTER TABLE pedido ADD CONSTRAINT ck_pedido_documento CHECK (documento IN ('NOTA', 'SOLO_TABLA'));

ALTER TABLE pedido ADD COLUMN origen_id BIGINT REFERENCES pedido (id);
CREATE INDEX ix_pedido_origen ON pedido (origen_id);
