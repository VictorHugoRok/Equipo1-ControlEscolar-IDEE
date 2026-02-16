CREATE TABLE IF NOT EXISTS maestro_documentos (
    id BIGSERIAL PRIMARY KEY,
    maestro_id BIGINT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    size_bytes BIGINT,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data BYTEA,
    CONSTRAINT fk_maestro_documentos_maestro FOREIGN KEY (maestro_id) REFERENCES maestros(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_maestro_documentos_maestro ON maestro_documentos(maestro_id);
