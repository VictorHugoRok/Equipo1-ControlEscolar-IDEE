-- ================================================================
-- Datos COMPLETOS de prueba para el módulo de Títulos Electrónicos
-- Versión: 7
-- Descripción: Inserta configuración institucional y responsables de firma
-- ================================================================

-- ===============================================================
-- 1. CONFIGURACIÓN INSTITUCIONAL
-- ===============================================================
-- Solo se permite UNA configuración activa a la vez
INSERT INTO configuracion_institucional (
    cve_institucion,
    nombre_institucion,
    id_entidad_federativa,
    entidad_federativa,
    activo,
    fecha_creacion,
    fecha_actualizacion
) VALUES (
    'IDEE001',
    'Instituto de Especialidades Estomatológicas IDEE',
    '09',
    'Ciudad de México',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT ON CONSTRAINT uk_configuracion_activa
DO UPDATE SET
    cve_institucion = EXCLUDED.cve_institucion,
    nombre_institucion = EXCLUDED.nombre_institucion,
    id_entidad_federativa = EXCLUDED.id_entidad_federativa,
    entidad_federativa = EXCLUDED.entidad_federativa,
    fecha_actualizacion = CURRENT_TIMESTAMP;

-- ===============================================================
-- 2. RESPONSABLES DE FIRMA
-- ===============================================================
-- Insertar Director General (orden 1)
INSERT INTO responsables_firma (
    nombre,
    primer_apellido,
    segundo_apellido,
    curp,
    id_cargo,
    cargo,
    abr_titulo,
    activo,
    orden_firma,
    fecha_creacion,
    fecha_actualizacion
) VALUES (
    'ROBERTO',
    'MENDEZ',
    'SANCHEZ',
    'MESR750420HDFLNT01',
    '01',
    'Director General',
    'Dr.',
    TRUE,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (curp)
DO UPDATE SET
    nombre = EXCLUDED.nombre,
    primer_apellido = EXCLUDED.primer_apellido,
    segundo_apellido = EXCLUDED.segundo_apellido,
    id_cargo = EXCLUDED.id_cargo,
    cargo = EXCLUDED.cargo,
    abr_titulo = EXCLUDED.abr_titulo,
    activo = EXCLUDED.activo,
    orden_firma = EXCLUDED.orden_firma,
    fecha_actualizacion = CURRENT_TIMESTAMP;

-- Insertar Secretario Académico (orden 2)
INSERT INTO responsables_firma (
    nombre,
    primer_apellido,
    segundo_apellido,
    curp,
    id_cargo,
    cargo,
    abr_titulo,
    activo,
    orden_firma,
    fecha_creacion,
    fecha_actualizacion
) VALUES (
    'ANA PATRICIA',
    'LOPEZ',
    'RAMIREZ',
    'LORA820615MDFLMN05',
    '02',
    'Secretaria Académica',
    'Mtra.',
    TRUE,
    2,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (curp)
DO UPDATE SET
    nombre = EXCLUDED.nombre,
    primer_apellido = EXCLUDED.primer_apellido,
    segundo_apellido = EXCLUDED.segundo_apellido,
    id_cargo = EXCLUDED.id_cargo,
    cargo = EXCLUDED.cargo,
    abr_titulo = EXCLUDED.abr_titulo,
    activo = EXCLUDED.activo,
    orden_firma = EXCLUDED.orden_firma,
    fecha_actualizacion = CURRENT_TIMESTAMP;

-- ===============================================================
-- 3. ACTUALIZAR ALUMNO EXISTENTE A ESTATUS EGRESADO
-- ===============================================================
-- Cambiar el alumno de V5 a EGRESADO para que pueda generar título
UPDATE alumnos
SET estatus_matricula = 'EGRESADO'
WHERE matricula = 'IDEE2024ESP001';

-- ===============================================================
-- 4. INSERTAR MÁS ALUMNOS EGRESADOS PARA PRUEBAS
-- ===============================================================

-- Alumno 2: EGRESADO
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
    'IDEE2024ESP002',
    'CARLOS ALBERTO',
    'RAMIREZ',
    'TORRES',
    'RATC920825HDFMRR03',
    'MASCULINO',
    '1992-08-25',
    'caramirez@idee.edu.mx',
    'carlos.ramirez@example.com',
    '5551122334',
    '06700',
    'Martha Torres Vega',
    '5559876543',
    (SELECT id FROM programas_educativos WHERE clave = 'ESP-ORTO-001'),
    '2022-2024',
    'MATUTINO',
    'EGRESADO',
    'Alumno de prueba - EGRESADO listo para titular',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (matricula) DO NOTHING;

-- Alumno 3: EGRESADO
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
    'IDEE2024ESP003',
    'LAURA PATRICIA',
    'HERNANDEZ',
    'GARCIA',
    'HEGL931010MDFRRR01',
    'FEMENINO',
    '1993-10-10',
    'lphernandez@idee.edu.mx',
    'laura.hernandez@example.com',
    '5552233445',
    '06700',
    'Roberto García Pérez',
    '5558765432',
    (SELECT id FROM programas_educativos WHERE clave = 'ESP-ORTO-001'),
    '2022-2024',
    'VESPERTINO',
    'EGRESADO',
    'Alumno de prueba - EGRESADO listo para titular',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (matricula) DO NOTHING;

-- Alumno 4: ACTIVO (NO puede generar título todavía)
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
    'IDEE2024ESP004',
    'DIEGO FERNANDO',
    'CASTRO',
    'MORALES',
    'CAMD940212HDFSSR02',
    'MASCULINO',
    '1994-02-12',
    'dfcastro@idee.edu.mx',
    'diego.castro@example.com',
    '5553344556',
    '06700',
    'Carmen Morales Soto',
    '5557654321',
    (SELECT id FROM programas_educativos WHERE clave = 'ESP-ORTO-001'),
    '2024-2026',
    'MATUTINO',
    'ACTIVA',
    'Alumno de prueba - ACTIVO (aún cursando)',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (matricula) DO NOTHING;

-- ===============================================================
-- 5. VERIFICACIÓN Y RESUMEN
-- ===============================================================
DO $$
DECLARE
    v_config_count INTEGER;
    v_responsables_count INTEGER;
    v_alumnos_egresados_count INTEGER;
    v_programa_count INTEGER;
BEGIN
    -- Contar configuraciones activas
    SELECT COUNT(*) INTO v_config_count
    FROM configuracion_institucional
    WHERE activo = TRUE;

    -- Contar responsables activos
    SELECT COUNT(*) INTO v_responsables_count
    FROM responsables_firma
    WHERE activo = TRUE;

    -- Contar alumnos EGRESADOS
    SELECT COUNT(*) INTO v_alumnos_egresados_count
    FROM alumnos
    WHERE estatus_matricula = 'EGRESADO';

    -- Contar programas
    SELECT COUNT(*) INTO v_programa_count
    FROM programas_educativos
    WHERE clave = 'ESP-ORTO-001';

    -- Mostrar resumen
    RAISE NOTICE '========================================';
    RAISE NOTICE 'RESUMEN DE DATOS DE PRUEBA INSERTADOS';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Configuraciones institucionales activas: %', v_config_count;
    RAISE NOTICE 'Responsables de firma activos: %', v_responsables_count;
    RAISE NOTICE 'Alumnos con estatus EGRESADO: %', v_alumnos_egresados_count;
    RAISE NOTICE 'Programas educativos disponibles: %', v_programa_count;
    RAISE NOTICE '========================================';

    -- Verificar que todo esté correcto
    IF v_config_count = 0 THEN
        RAISE WARNING 'No hay configuración institucional activa';
    END IF;

    IF v_responsables_count < 2 THEN
        RAISE WARNING 'Se recomienda tener al menos 2 responsables de firma';
    END IF;

    IF v_alumnos_egresados_count = 0 THEN
        RAISE WARNING 'No hay alumnos EGRESADOS para generar títulos';
    ELSE
        RAISE NOTICE 'Sistema listo para generar títulos electrónicos';
    END IF;
END $$;

-- ===============================================================
-- DATOS PARA REFERENCIA
-- ===============================================================
--
-- CONFIGURACIÓN INSTITUCIONAL:
--   - Clave: IDEE001
--   - Nombre: Instituto de Especialidades Estomatológicas IDEE
--   - Entidad: Ciudad de México (09)
--
-- RESPONSABLES DE FIRMA:
--   1. Dr. Roberto Méndez Sánchez (Director General)
--      CURP: MESR750420HDFLNT01
--   2. Mtra. Ana Patricia López Ramírez (Secretaria Académica)
--      CURP: LORA820615MDFLMN05
--
-- ALUMNOS EGRESADOS (pueden generar título):
--   1. María Fernanda González Martínez (IDEE2024ESP001)
--      CURP: GOMF950315MDFNRR08
--   2. Carlos Alberto Ramírez Torres (IDEE2024ESP002)
--      CURP: RATC920825HDFMRR03
--   3. Laura Patricia Hernández García (IDEE2024ESP003)
--      CURP: HEGL931010MDFRRR01
--
-- ALUMNO ACTIVO (NO puede generar título):
--   4. Diego Fernando Castro Morales (IDEE2024ESP004)
--      CURP: CAMD940212HDFSSR02
--
-- ===============================================================

-- FIN DEL SCRIPT
