-- Esquema de la base de datos de EPIs. Es idempotente: la API lo ejecuta al arrancar.
--
-- Cómo se sincroniza: cada cambio en la tabla "epis" recibe un número de revisión creciente
-- (columna "revision"). Los móviles piden "dame todo lo que tenga revisión mayor que la última
-- que vi". El número lo asigna un TRIGGER, así que funciona igual si escribe la API o si tu web
-- modifica la tabla directamente con SQL.

CREATE SEQUENCE IF NOT EXISTS epis_revision_seq;

CREATE TABLE IF NOT EXISTS epis (
    uid            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    parte_cuerpo   text        NOT NULL,
    subcategoria   text        NOT NULL DEFAULT '',
    nombre_epi     text        NOT NULL,
    marca          text        NOT NULL DEFAULT '',
    modelo         text        NOT NULL DEFAULT '',
    normativa      text        NOT NULL DEFAULT '',
    simbolos       text        NOT NULL DEFAULT '',
    ficha_tecnica  text        NOT NULL DEFAULT '',
    distribuidor   text        NOT NULL DEFAULT '',
    observaciones  text        NOT NULL DEFAULT '',
    -- Identificadores de las fotos (tabla epi_fotos); la primera es la miniatura
    fotos          text[]      NOT NULL DEFAULT '{}',
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    revision       bigint      NOT NULL DEFAULT 0,
    -- Borrado lógico: así los móviles se enteran de que la ficha ya no existe
    eliminado      boolean     NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS epis_revision_idx ON epis (revision);

CREATE TABLE IF NOT EXISTS epi_fotos (
    id        text        PRIMARY KEY,
    contenido bytea       NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now()
);

-- Documentos PDF adjuntos (fichas técnicas del distribuidor). En la ficha se guarda una lista
-- [{"id": "...", "nombre": "..."}]; el contenido va en epi_documentos, igual que las fotos.
ALTER TABLE epis ADD COLUMN IF NOT EXISTS documentos jsonb NOT NULL DEFAULT '[]';

CREATE TABLE IF NOT EXISTS epi_documentos (
    id        text        PRIMARY KEY,
    contenido bytea       NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now()
);

-- Zonas del cuerpo y subcategorías válidas (la API lo rellena al arrancar). Úsalo para los desplegables de la web.
CREATE TABLE IF NOT EXISTS catalogo_subcategorias (
    parte        text NOT NULL,
    subcategoria text NOT NULL,
    orden        int  NOT NULL,
    PRIMARY KEY (parte, subcategoria)
);

-- Cada INSERT o UPDATE recibe la siguiente revisión. El bloqueo hace que las escrituras se
-- serialicen: así el orden de revisión coincide con el orden de confirmación y ningún móvil
-- puede "saltarse" un cambio que se confirmó tarde.
CREATE OR REPLACE FUNCTION epis_antes_de_escribir() RETURNS trigger AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(7311001);
    NEW.revision := nextval('epis_revision_seq');
    NEW.actualizado_en := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS epis_revision ON epis;
CREATE TRIGGER epis_revision
    BEFORE INSERT OR UPDATE ON epis
    FOR EACH ROW EXECUTE FUNCTION epis_antes_de_escribir();

-- Un DELETE directo (por ejemplo desde la web) se convierte en borrado lógico, para que los
-- móviles reciban el borrado en su siguiente sincronización.
CREATE OR REPLACE FUNCTION epis_borrado_logico() RETURNS trigger AS $$
BEGIN
    UPDATE epis SET eliminado = true WHERE uid = OLD.uid;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS epis_borrar ON epis;
CREATE TRIGGER epis_borrar
    BEFORE DELETE ON epis
    FOR EACH ROW EXECUTE FUNCTION epis_borrado_logico();

-- Vista cómoda para la web: solo las fichas vivas.
CREATE OR REPLACE VIEW epis_activos AS
    SELECT * FROM epis WHERE NOT eliminado;
