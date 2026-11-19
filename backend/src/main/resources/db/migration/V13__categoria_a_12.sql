-- =====================================================================
-- V13: La categoría de precio A pasa de 10 % a 12 % (pedido del negocio:
-- "10 % es muy poco"). Las categorías quedan A = 12 %, B = 15 %, C = 20 %.
--
-- No cambia el porcentaje guardado en cada cliente: un cliente que tenía 10
-- sigue con 10 (y ya no muestra letra) hasta que se le cambie la categoría.
-- Los precios de los pedidos ya armados tampoco cambian.
-- =====================================================================

UPDATE categoria_precio SET porcentaje = 12 WHERE letra = 'A' AND porcentaje = 10;
