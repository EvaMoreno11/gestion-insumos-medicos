-- =====================================================================
-- V10: Costo de las compras
--
-- movimiento.costo_unitario: en una ENTRADA es el costo de compra de esas unidades
--   (de la boleta). En una SALIDA o un AJUSTE es el costo promedio del lote en ese
--   momento: así se sabe cuánto le costó al negocio lo que se vendió o se dio de baja.
-- lote.costo_promedio: promedio ponderado de las entradas del lote. Si un lote recibe
--   unidades a distintos costos, el promedio se recalcula con cada entrada.
--
-- Cada entrada también actualiza producto.costo (el último costo de compra), que es
-- el que se usa para calcular los precios de los pedidos nuevos.
-- =====================================================================

ALTER TABLE movimiento ADD COLUMN costo_unitario NUMERIC(12, 4);
ALTER TABLE movimiento ADD CONSTRAINT ck_movimiento_costo CHECK (costo_unitario >= 0);

ALTER TABLE lote ADD COLUMN costo_promedio NUMERIC(12, 4);
ALTER TABLE lote ADD CONSTRAINT ck_lote_costo CHECK (costo_promedio >= 0);

-- Lotes y entradas que ya existían: se toma el costo actual del producto (si lo tiene)
UPDATE lote l SET costo_promedio = p.costo FROM producto p WHERE p.id = l.producto_id;
UPDATE movimiento m SET costo_unitario = l.costo_promedio FROM lote l WHERE l.id = m.lote_id;
