-- ================================================================
-- Datos de prueba para generar títulos electrónicos
-- Incluye: Programa Educativo y Alumno
-- ================================================================

-- 1. Insertar Programa Educativo de Prueba
-- Especialidad en Ortodoncia (especialidad médica/odontológica)
INSERT INTO programas_educativos (
    clave,
    nombre,
    tipo_programa,
    duracion_periodos,
    tipo_periodo,
    creditos_totales,
    rvoe,
    fecha_rvoe,
    modalidad,
    estatus,
    descripcion,
    fecha_creacion,
    fecha_actualizacion
) VALUES (
    'ESP-ORTO-001',
    'Especialidad en Ortodoncia',
    'ESPECIALIDAD',
    4,
    'CUATRIMESTRE',
    80,
    'RVOE-ESP-2023-001',
    '2023-01-15',
    'ESCOLARIZADO',
    'ACTIVO',
    'Programa de especialidad en ortodoncia para profesionales de la odontología',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (clave) DO NOTHING;

-- 2. Insertar Alumno de Prueba
-- IMPORTANTE: El CURP debe ser válido y único
INSERT INTO alumnos (
    matricula,
    nombre,
    apellido_paterno,
    apellido_materno,
    curp,
    sexo,
    fecha_nacimiento,
    correo_institucional,
    correo_personal,
    telefono,
    codigo_postal,
    nombre_contacto_emergencia,
    telefono_contacto_emergencia,
    programa_id,
    ciclo_escolar,
    turno,
    estatus_matricula,
    observaciones,
    fecha_creacion,
    fecha_actualizacion
) VALUES (
    'IDEE2024ESP001',
    'MARIA FERNANDA',
    'GONZALEZ',
    'MARTINEZ',
    'GOMF950315MDFNRR08',  -- CURP válido de prueba
    'FEMENINO',
    '1995-03-15',
    'mfgonzalez@idee.edu.mx',
    'maria.gonzalez@example.com',
    '5551234567',
    '06700',
    'Juan González López',
    '5557654321',
    (SELECT id FROM programas_educativos WHERE clave = 'ESP-ORTO-001'),
    '2024-2026',
    'MATUTINO',
    'ACTIVA',
    'Alumno de prueba para generación de títulos electrónicos',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (matricula) DO NOTHING;

-- 3. Comentarios informativos
COMMENT ON TABLE programas_educativos IS 'Programas educativos ofrecidos por la institución';
COMMENT ON TABLE alumnos IS 'Registro de alumnos inscritos';

-- Verificar que se insertaron correctamente
DO $$
DECLARE
    v_programa_count INTEGER;
    v_alumno_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_programa_count FROM programas_educativos WHERE clave = 'ESP-ORTO-001';
    SELECT COUNT(*) INTO v_alumno_count FROM alumnos WHERE matricula = 'IDEE2024ESP001';

    RAISE NOTICE 'Programas insertados: %', v_programa_count;
    RAISE NOTICE 'Alumnos insertados: %', v_alumno_count;

    IF v_programa_count = 0 OR v_alumno_count = 0 THEN
        RAISE WARNING 'No se pudieron insertar todos los datos de prueba';
    ELSE
        RAISE NOTICE 'Datos de prueba insertados correctamente';
    END IF;
END $$;
