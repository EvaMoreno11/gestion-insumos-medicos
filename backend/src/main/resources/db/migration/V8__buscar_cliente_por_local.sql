-- =====================================================================
-- V8: El buscador de clientes también encuentra por el nombre del local
-- (p. ej. el nombre de la botica) y por la persona de contacto.
-- Cada local guarda ese texto ya normalizado; la aplicación lo calcula
-- (TextoBusqueda.normalizar) cada vez que se guarda un local.
-- =====================================================================

ALTER TABLE cliente_local ADD COLUMN texto_busqueda VARCHAR(400) NOT NULL DEFAULT '';

-- Locales ya registrados: misma normalización hecha en SQL (minúsculas, sin tildes,
-- números separados de letras, solo letras y números, espacios al inicio y al final)
UPDATE cliente_local
SET texto_busqueda = ' ' || trim(regexp_replace(
        regexp_replace(
            regexp_replace(
                translate(lower(coalesce(nombre, '') || ' ' || coalesce(contacto, '')),
                          'áàäâéèëêíìïîóòöôúùüûñç', 'aaaaeeeeiiiioooouuuunc'),
                '([0-9])([a-z])', '\1 \2', 'g'),
            '([a-z])([0-9])', '\1 \2', 'g'),
        '[^a-z0-9]+', ' ', 'g')) || ' ';
