-- =====================================================
-- Script de Migración: Módulo de Títulos Electrónicos
-- Versión: 3
-- Fecha: 2025-12-19
-- Descripción: Crea las tablas necesarias para la generación
--              de títulos profesionales electrónicos según
--              estándar SEP
-- =====================================================

-- =====================================================
-- TABLA: configuracion_institucional
-- Descripción: Almacena la configuración de la institución
--              para la emisión de títulos electrónicos
-- =====================================================
CREATE TABLE configuracion_institucional (
    id BIGSERIAL PRIMARY KEY,

    -- Datos de la Institución
    cve_institucion VARCHAR(50) NOT NULL,
    nombre_institucion VARCHAR(255) NOT NULL,

    -- Datos de Entidad Federativa
    id_entidad_federativa VARCHAR(10) NOT NULL,
    entidad_federativa VARCHAR(100) NOT NULL,

    -- Firma Digital (Certificados SAT)
    certificado_path VARCHAR(500),
    llave_privada_path VARCHAR(500),
    password_llave_privada VARCHAR(255),
    no_certificado_sat VARCHAR(50),

    -- Configuración activa (solo puede haber una configuración activa)
    activo BOOLEAN DEFAULT TRUE,

    -- Auditoría
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Crear índice único para configuración activa
CREATE UNIQUE INDEX uk_configuracion_activa
ON configuracion_institucional (activo)
WHERE (activo = TRUE);

-- Comentarios de la tabla
COMMENT ON TABLE configuracion_institucional IS 'Configuración institucional para emisión de títulos electrónicos';
COMMENT ON COLUMN configuracion_institucional.cve_institucion IS 'Clave de la institución según catálogo SEP';
COMMENT ON COLUMN configuracion_institucional.certificado_path IS 'Ruta al archivo .cer del SAT';
COMMENT ON COLUMN configuracion_institucional.llave_privada_path IS 'Ruta al archivo .key del SAT';
COMMENT ON COLUMN configuracion_institucional.password_llave_privada IS 'Contraseña de la llave privada (encriptada)';

-- =====================================================
-- TABLA: responsables_firma
-- Descripción: Almacena los responsables que firmarán
--              los títulos (Director, Secretario, etc.)
-- =====================================================
CREATE TABLE responsables_firma (
    id BIGSERIAL PRIMARY KEY,

    -- Datos personales
    nombre VARCHAR(100) NOT NULL,
    primer_apellido VARCHAR(100) NOT NULL,
    segundo_apellido VARCHAR(100),
    curp VARCHAR(18) UNIQUE NOT NULL,

    -- Cargo
    id_cargo VARCHAR(10) NOT NULL,
    cargo VARCHAR(150) NOT NULL,
    abr_titulo VARCHAR(20),

    -- Firma Digital
    certificado_responsable TEXT,
    no_certificado_responsable VARCHAR(50),

    -- Estado
    activo BOOLEAN DEFAULT TRUE,
    orden_firma INTEGER DEFAULT 1,

    -- Auditoría
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Crear índices
CREATE INDEX idx_responsables_activo ON responsables_firma(activo);
CREATE INDEX idx_responsables_orden ON responsables_firma(orden_firma);
CREATE UNIQUE INDEX uk_responsables_curp ON responsables_firma(curp);

-- Comentarios de la tabla
COMMENT ON TABLE responsables_firma IS 'Responsables autorizados para firmar títulos electrónicos';
COMMENT ON COLUMN responsables_firma.curp IS 'CURP del responsable (único)';
COMMENT ON COLUMN responsables_firma.id_cargo IS 'ID del cargo según catálogo SEP';
COMMENT ON COLUMN responsables_firma.orden_firma IS 'Orden en que aparece la firma en el XML (1=primero, 2=segundo, etc.)';
COMMENT ON COLUMN responsables_firma.certificado_responsable IS 'Certificado digital del responsable en Base64';

-- =====================================================
-- TABLA: titulos_electronicos
-- Descripción: Almacena los títulos electrónicos generados
-- =====================================================
CREATE TABLE titulos_electronicos (
    id BIGSERIAL PRIMARY KEY,

    -- Identificación del título
    folio_control VARCHAR(50) UNIQUE NOT NULL,

    -- Relaciones
    alumno_id BIGINT NOT NULL,
    programa_id BIGINT NOT NULL,

    -- Datos de expedición
    fecha_expedicion DATE NOT NULL,
    id_modalidad_titulacion VARCHAR(10) NOT NULL,
    modalidad_titulacion VARCHAR(150) NOT NULL,
    fecha_examen_profesional DATE,
    fecha_exencion_examen_profesional DATE,

    -- Servicio Social
    cumplio_servicio_social BOOLEAN NOT NULL DEFAULT TRUE,
    id_fundamento_legal_servicio_social VARCHAR(10),
    fundamento_legal_servicio_social VARCHAR(255),

    -- Antecedente (estudios previos del alumno)
    institucion_procedencia VARCHAR(255),
    id_tipo_estudio_antecedente VARCHAR(10),
    tipo_estudio_antecedente VARCHAR(150),
    id_entidad_federativa_antecedente VARCHAR(10),
    entidad_federativa_antecedente VARCHAR(100),
    fecha_inicio_antecedente DATE,
    fecha_terminacion_antecedente DATE,
    no_cedula VARCHAR(50),

    -- Archivos generados
    xml_content TEXT,
    xml_path VARCHAR(500),
    sello_sat TEXT,
    cadena_original TEXT,

    -- Estado del título
    estatus VARCHAR(50) NOT NULL DEFAULT 'GENERADO',

    -- Observaciones
    observaciones TEXT,

    -- Auditoría
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_creacion_id BIGINT,

    -- Foreign Keys
    CONSTRAINT fk_titulos_alumno FOREIGN KEY (alumno_id) REFERENCES alumnos(id) ON DELETE RESTRICT,
    CONSTRAINT fk_titulos_programa FOREIGN KEY (programa_id) REFERENCES programas_educativos(id) ON DELETE RESTRICT,
    CONSTRAINT fk_titulos_usuario FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios(id) ON DELETE SET NULL,

    -- Check Constraints
    CONSTRAINT chk_titulos_estatus CHECK (estatus IN ('GENERADO', 'FIRMADO', 'ENVIADO_SEP', 'VALIDADO_SEP', 'RECHAZADO_SEP', 'ENTREGADO')),
    CONSTRAINT chk_titulos_modalidad CHECK (
        (fecha_examen_profesional IS NOT NULL AND fecha_exencion_examen_profesional IS NULL) OR
        (fecha_examen_profesional IS NULL AND fecha_exencion_examen_profesional IS NOT NULL) OR
        (fecha_examen_profesional IS NULL AND fecha_exencion_examen_profesional IS NULL)
    )
);

-- Crear índices para optimizar consultas
CREATE INDEX idx_titulos_alumno ON titulos_electronicos(alumno_id);
CREATE INDEX idx_titulos_programa ON titulos_electronicos(programa_id);
CREATE INDEX idx_titulos_estatus ON titulos_electronicos(estatus);
CREATE INDEX idx_titulos_fecha_expedicion ON titulos_electronicos(fecha_expedicion);
CREATE UNIQUE INDEX uk_titulos_folio ON titulos_electronicos(folio_control);

-- Comentarios de la tabla
COMMENT ON TABLE titulos_electronicos IS 'Títulos profesionales electrónicos generados según estándar SEP';
COMMENT ON COLUMN titulos_electronicos.folio_control IS 'Folio único del título electrónico';
COMMENT ON COLUMN titulos_electronicos.xml_content IS 'Contenido del XML generado';
COMMENT ON COLUMN titulos_electronicos.sello_sat IS 'Sello digital generado con certificado SAT';
COMMENT ON COLUMN titulos_electronicos.cadena_original IS 'Cadena original usada para generar el sello';
COMMENT ON COLUMN titulos_electronicos.estatus IS 'Estado del título: GENERADO, FIRMADO, ENVIADO_SEP, VALIDADO_SEP, RECHAZADO_SEP, ENTREGADO';

-- =====================================================
-- TRIGGERS para actualización automática de timestamps
-- =====================================================

-- Trigger para configuracion_institucional
CREATE OR REPLACE FUNCTION actualizar_fecha_actualizacion_configuracion()
RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_configuracion_actualizar_fecha
BEFORE UPDATE ON configuracion_institucional
FOR EACH ROW
EXECUTE FUNCTION actualizar_fecha_actualizacion_configuracion();

-- Trigger para responsables_firma
CREATE OR REPLACE FUNCTION actualizar_fecha_actualizacion_responsables()
RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_responsables_actualizar_fecha
BEFORE UPDATE ON responsables_firma
FOR EACH ROW
EXECUTE FUNCTION actualizar_fecha_actualizacion_responsables();

-- Trigger para titulos_electronicos
CREATE OR REPLACE FUNCTION actualizar_fecha_actualizacion_titulos()
RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_titulos_actualizar_fecha
BEFORE UPDATE ON titulos_electronicos
FOR EACH ROW
EXECUTE FUNCTION actualizar_fecha_actualizacion_titulos();

-- =====================================================
-- DATOS DE EJEMPLO (OPCIONAL - Comentado por seguridad)
-- =====================================================

-- Insertar configuración institucional de ejemplo (DESCOMENTARLO CUANDO TENGAS LOS DATOS REALES)
/*
INSERT INTO configuracion_institucional (
    cve_institucion,
    nombre_institucion,
    id_entidad_federativa,
    entidad_federativa,
    activo
) VALUES (
    'IDEE001',
    'Instituto de Especialidades Estomatológicas',
    '09',
    'Ciudad de México',
    TRUE
);
*/

-- Insertar responsables de firma de ejemplo (DESCOMENTARLO CUANDO TENGAS LOS DATOS REALES)
/*
INSERT INTO responsables_firma (
    nombre,
    primer_apellido,
    segundo_apellido,
    curp,
    id_cargo,
    cargo,
    abr_titulo,
    activo,
    orden_firma
) VALUES
(
    'NOMBRE_DIRECTOR',
    'APELLIDO_P',
    'APELLIDO_M',
    'CURP18CARACTERES01',
    '01',
    'Director General',
    'Dr.',
    TRUE,
    1
),
(
    'NOMBRE_SECRETARIO',
    'APELLIDO_P',
    'APELLIDO_M',
    'CURP18CARACTERES02',
    '02',
    'Secretario Académico',
    'Mtro.',
    TRUE,
    2
);
*/

-- =====================================================
-- FIN DEL SCRIPT
-- =====================================================
