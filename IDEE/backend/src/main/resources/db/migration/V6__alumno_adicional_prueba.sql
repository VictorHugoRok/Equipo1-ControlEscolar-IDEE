-- ================================================================
-- Alumno adicional para prueba de generación de título electrónico
-- Con todos los datos necesarios según estándar DOF
-- ================================================================

-- Insertar segundo alumno de prueba
-- Alumno egresado para poder generar su título
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
    'IDEE2022ESP002',
    'CARLOS ALBERTO',
    'RAMIREZ',
    'LOPEZ',
    'RALC920815HDFRPR03',  -- CURP válido de prueba
    'MASCULINO',
    '1992-08-15',
    'cramirez@idee.edu.mx',
    'carlos.ramirez@example.com',
    '5559876543',
    '03100',
    'Ana María López García',
    '5551237890',
    (SELECT id FROM programas_educativos WHERE clave = 'ESP-ORTO-001'),
    '2022-2024',
    'VESPERTINO',
    'EGRESADO',
    'Alumno egresado - Listo para generación de título electrónico',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (matricula) DO NOTHING;

-- Verificar inserción
DO $$
DECLARE
    v_alumno_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_alumno_count FROM alumnos WHERE matricula = 'IDEE2022ESP002';

    IF v_alumno_count > 0 THEN
        RAISE NOTICE 'Alumno adicional de prueba insertado correctamente: IDEE2022ESP002';
    ELSE
        RAISE WARNING 'No se pudo insertar el alumno de prueba';
    END IF;
END $$;