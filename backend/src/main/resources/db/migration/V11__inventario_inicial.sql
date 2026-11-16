-- =====================================================================
-- V11: Entradas de inventario inicial
--
-- Las entradas que vienen de importar el Excel (o de los datos de demostración)
-- son el stock con el que se empezó, no compras de ese mes: el resumen no las
-- cuenta en "Compras". Las demás entradas (las que se registran en la app) sí.
-- =====================================================================

ALTER TABLE movimiento ADD COLUMN inicial BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE movimiento SET inicial = TRUE
WHERE tipo = 'ENTRADA'
  AND (nota LIKE 'Importación de inventario%' OR nota = 'Datos de demostración');
