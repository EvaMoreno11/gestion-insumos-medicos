-- =====================================================================
-- V5: El vencimiento se maneja por mes y año
-- Un lote "vence 03/2027" se puede usar durante todo marzo, así que la
-- fecha se guarda como el último día de su mes. Esta migración corrige
-- los lotes registrados antes de esa regla (p. ej. 2027-03-15 → 2027-03-31).
-- =====================================================================

UPDATE lote
SET fecha_vencimiento = (date_trunc('month', fecha_vencimiento) + INTERVAL '1 month - 1 day')::date
WHERE fecha_vencimiento IS NOT NULL;
