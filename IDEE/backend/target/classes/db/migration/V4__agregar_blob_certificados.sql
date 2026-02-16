-- Migración para agregar soporte de carga de archivos de certificados
-- Agrega columnas BLOB para almacenar los archivos .cer y .key directamente en la BD
-- PostgreSQL: Usa BYTEA en lugar de LONGBLOB (MySQL)

ALTER TABLE configuracion_institucional
    ADD COLUMN certificado_data BYTEA,
    ADD COLUMN certificado_filename VARCHAR(255),
    ADD COLUMN llave_privada_data BYTEA,
    ADD COLUMN llave_privada_filename VARCHAR(255);

-- Comentarios para documentación
COMMENT ON COLUMN configuracion_institucional.certificado_data IS 'Contenido binario del certificado .cer';
COMMENT ON COLUMN configuracion_institucional.certificado_filename IS 'Nombre original del archivo .cer';
COMMENT ON COLUMN configuracion_institucional.llave_privada_data IS 'Contenido binario de la llave privada .key';
COMMENT ON COLUMN configuracion_institucional.llave_privada_filename IS 'Nombre original del archivo .key';

-- Marcar columnas antiguas como deprecadas
COMMENT ON COLUMN configuracion_institucional.certificado_path IS 'Ruta al archivo .cer (DEPRECATED - usar certificado_data)';
COMMENT ON COLUMN configuracion_institucional.llave_privada_path IS 'Ruta al archivo .key (DEPRECATED - usar llave_privada_data)';