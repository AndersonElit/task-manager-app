-- ============================================================
-- Task Manager - Modelo de Base de Datos
-- Motor: PostgreSQL 15+
-- ============================================================

-- ============================================================
-- FUNCIÓN auxiliar para actualizar updated_at automáticamente
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- FUNCIÓN auxiliar para registrar historial de cambios de estado
-- Se dispara cuando tasks.status_id cambia, insertando una fila
-- en tasks_status para preservar la traza completa de transiciones.
-- ============================================================

CREATE OR REPLACE FUNCTION log_task_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') OR (OLD.status_id IS DISTINCT FROM NEW.status_id) THEN
        INSERT INTO tasks_status (task_id, status_id)
        VALUES (NEW.id, NEW.status_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- TABLA: status
-- Catálogo cerrado de estados posibles para una tarea.
-- Tabla de referencia en lugar de ENUM para permitir agregar
-- nuevos estados sin migraciones DDL.
--
-- 1FN: atributos atómicos, PK definida.
-- 2FN/3FN: un solo atributo descriptivo, sin dependencias transitivas.
-- ============================================================

CREATE TABLE status (
    id   SMALLINT    PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(50) NOT NULL UNIQUE,

    CONSTRAINT status_name_not_blank CHECK (TRIM(name) <> '')
);

COMMENT ON TABLE  status      IS 'Catálogo de estados posibles de una tarea.';
COMMENT ON COLUMN status.id   IS 'Identificador numérico del estado.';
COMMENT ON COLUMN status.name IS 'Nombre único del estado (ej: pendiente, completada).';

INSERT INTO status (name) VALUES ('pendiente'), ('completada');

-- ============================================================
-- TABLA: tasks
--
-- 1FN: atributos atómicos, clave primaria UUID.
-- 2FN: PK simple, sin dependencias parciales.
-- 3FN: cada columna depende únicamente de la PK.
--      status_id referencia a la tabla status (sin transitividad).
-- ============================================================

CREATE TABLE tasks (
    id          UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255)             NOT NULL,
    description TEXT,
    status_id   SMALLINT                 NOT NULL REFERENCES status(id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT tasks_title_not_blank CHECK (TRIM(title) <> '')
);

COMMENT ON TABLE  tasks             IS 'Unidad de trabajo gestionada por el sistema.';
COMMENT ON COLUMN tasks.id         IS 'Identificador único universal de la tarea.';
COMMENT ON COLUMN tasks.title      IS 'Título descriptivo, obligatorio y no en blanco.';
COMMENT ON COLUMN tasks.description IS 'Detalle opcional de la tarea.';
COMMENT ON COLUMN tasks.status_id  IS 'Estado actual de la tarea (FK a status).';
COMMENT ON COLUMN tasks.created_at IS 'Timestamp de creación (UTC).';
COMMENT ON COLUMN tasks.updated_at IS 'Timestamp de última modificación (UTC), gestionado por trigger.';

-- ============================================================
-- TABLA: tasks_status
-- Historial de transiciones de estado de cada tarea.
-- Permite reconstruir la línea de tiempo completa de cambios.
--
-- 1FN: atributos atómicos, PK propia.
-- 2FN/3FN: task_id y status_id son FKs independientes, date
--           depende únicamente del id del registro.
-- ============================================================

CREATE TABLE tasks_status (
    id        UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id   UUID                     NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    status_id SMALLINT                 NOT NULL REFERENCES status(id),
    date      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  tasks_status           IS 'Historial de cambios de estado de cada tarea.';
COMMENT ON COLUMN tasks_status.id        IS 'Identificador del registro de historial.';
COMMENT ON COLUMN tasks_status.task_id   IS 'Tarea a la que pertenece el cambio.';
COMMENT ON COLUMN tasks_status.status_id IS 'Estado que tomó la tarea en este momento.';
COMMENT ON COLUMN tasks_status.date      IS 'Fecha y hora en que se registró el cambio (UTC).';

-- ============================================================
-- TRIGGERS
-- ============================================================

CREATE TRIGGER trg_tasks_updated_at
BEFORE UPDATE ON tasks
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- Registra en tasks_status tanto la creación como cada cambio de estado
CREATE TRIGGER trg_log_task_status
AFTER INSERT OR UPDATE OF status_id ON tasks
FOR EACH ROW
EXECUTE FUNCTION log_task_status_change();

-- ============================================================
-- ÍNDICES
-- ============================================================

CREATE INDEX idx_tasks_status_id  ON tasks (status_id);
CREATE INDEX idx_tasks_created_at ON tasks (created_at DESC);

-- Consultas de historial por tarea ordenadas cronológicamente
CREATE INDEX idx_tasks_status_task_id ON tasks_status (task_id, date DESC);
