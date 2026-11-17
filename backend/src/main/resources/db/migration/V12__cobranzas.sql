-- =====================================================================
-- V12: Cobranzas
--
-- Un pedido despachado queda "por cobrar" por su total. Después se anota cómo paga
-- el cliente (el plazo) y los pagos que van llegando.
--
-- pedido.plazo_dias: NULL = nadie anotó todavía cómo paga ("sin plazo"); 0 = al contado;
--   N = crédito a N días desde el despacho. Con el plazo vencido y saldo pendiente,
--   el cobro está vencido.
-- cliente.plazo_dias: el plazo habitual del cliente. Se guarda al ponerle plazo a uno de
--   sus pedidos, y sus siguientes pedidos lo toman al despacharse.
-- pago: cada pago recibido por un pedido (puede haber varios pagos parciales).
-- =====================================================================

ALTER TABLE pedido ADD COLUMN plazo_dias INTEGER;
ALTER TABLE pedido ADD CONSTRAINT ck_pedido_plazo CHECK (plazo_dias BETWEEN 0 AND 365);

ALTER TABLE cliente ADD COLUMN plazo_dias INTEGER;
ALTER TABLE cliente ADD CONSTRAINT ck_cliente_plazo CHECK (plazo_dias BETWEEN 0 AND 365);

CREATE TABLE pago (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id  BIGINT         NOT NULL REFERENCES pedido (id) ON DELETE CASCADE,
    fecha      DATE           NOT NULL,
    monto      NUMERIC(12, 2) NOT NULL,
    medio      VARCHAR(15)    NOT NULL,
    nota       VARCHAR(200),
    creado_en  TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT ck_pago_monto CHECK (monto > 0),
    CONSTRAINT ck_pago_medio CHECK (medio IN ('EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA'))
);
CREATE INDEX ix_pago_pedido ON pago (pedido_id);
CREATE INDEX ix_pago_fecha ON pago (fecha);
