-- =====================================================================
-- V7: Usuarios para el inicio de sesión
-- Hoy hay una sola cuenta compartida, pero la tabla admite varias.
-- La contraseña nunca se guarda: solo su hash BCrypt.
-- =====================================================================

CREATE TABLE usuario (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario            VARCHAR(40)  NOT NULL,
    clave_hash         VARCHAR(100) NOT NULL,
    activo             BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Protección contra quien intenta adivinar la contraseña
    intentos_fallidos  INTEGER      NOT NULL DEFAULT 0,
    bloqueado_hasta    TIMESTAMPTZ,
    creado_en          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ultimo_ingreso     TIMESTAMPTZ,

    CONSTRAINT ck_usuario_intentos CHECK (intentos_fallidos >= 0)
);
-- "Eva" y "eva" son el mismo usuario
CREATE UNIQUE INDEX uk_usuario ON usuario (lower(usuario));
