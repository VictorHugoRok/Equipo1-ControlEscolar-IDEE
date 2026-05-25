package com.idee.controlescolar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Ejecuta correcciones de esquema al iniciar la aplicación.
 * Flyway está deshabilitado, por lo que columnas obsoletas (ej. periodo en grupos)
 * pueden quedar en la BD con restricciones que impiden insertar.
 */
@Configuration
public class SchemaFixRunner {

    private static final Logger logger = LoggerFactory.getLogger(SchemaFixRunner.class);

    @Bean
    @Order(-1) // Ejecutar antes que otros runners
    CommandLineRunner corregirEsquemaGrupos(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE grupos DROP COLUMN IF EXISTS periodo");
                logger.info("✓ Esquema grupos: columna 'periodo' eliminada (si existía)");
            } catch (Exception e) {
                logger.warn("No se pudo eliminar columna periodo de grupos (puede que no exista): {}", e.getMessage());
            }
        };
    }

    /**
     * Añade la columna valido_xsd a certificados_electronicos si no existe.
     * Necesaria para el módulo de certificados electrónicos (validación XSD).
     */
    @Bean
    @Order(-1)
    CommandLineRunner agregarValidoXsdCertificados(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE certificados_electronicos ADD COLUMN IF NOT EXISTS valido_xsd BOOLEAN NOT NULL DEFAULT true"
                );
                logger.info("✓ Esquema certificados_electronicos: columna 'valido_xsd' verificada/agregada");
            } catch (Exception e) {
                logger.warn("No se pudo agregar columna valido_xsd a certificados_electronicos: {}", e.getMessage());
            }
        };
    }

    /**
     * Elimina constraints viejas de unicidad en periodo_academico (ej. UNIQUE(anio,numero) o UNIQUE(codigo))
     * que chocan con el modelo actual (unicidad por tipo_periodo + código / año-numero).
     */
    @Bean
    @Order(-1)
    CommandLineRunner quitarUnicidadesAntiguasPeriodoAcademico(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                List<String> uqAnioNumero = jdbcTemplate.queryForList(
                        "SELECT conname " +
                                "FROM pg_constraint " +
                                "WHERE conrelid = 'periodo_academico'::regclass " +
                                "  AND contype = 'u' " +
                                "  AND pg_get_constraintdef(oid) ILIKE '%UNIQUE (anio, numero)%'",
                        String.class
                );
                for (String name : uqAnioNumero) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE periodo_academico DROP CONSTRAINT IF EXISTS " + name);
                        logger.info("✓ periodo_academico: constraint antigua eliminada: {}", name);
                    } catch (Exception e) {
                        logger.warn("No se pudo eliminar constraint {}: {}", name, e.getMessage());
                    }
                }

                List<String> uqCodigo = jdbcTemplate.queryForList(
                        "SELECT conname " +
                                "FROM pg_constraint " +
                                "WHERE conrelid = 'periodo_academico'::regclass " +
                                "  AND contype = 'u' " +
                                "  AND pg_get_constraintdef(oid) ILIKE '%UNIQUE (codigo)%'",
                        String.class
                );
                for (String name : uqCodigo) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE periodo_academico DROP CONSTRAINT IF EXISTS " + name);
                        logger.info("✓ periodo_academico: constraint antigua eliminada: {}", name);
                    } catch (Exception e) {
                        logger.warn("No se pudo eliminar constraint {}: {}", name, e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.warn("No se pudieron revisar/eliminar constraints antiguas de periodo_academico: {}", e.getMessage());
            }
        };
    }

    /**
     * Actualiza el CHECK de {@code usuarios.tipo_usuario} para incluir {@code SIN_ROL}.
     * Hibernate o migraciones antiguas pueden haber dejado solo los valores previos al enum en Java.
     */
    @Bean
    @Order(-1)
    CommandLineRunner permitirTipoUsuarioSinRol(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_tipo_usuario_check");
                jdbcTemplate.execute(
                        "ALTER TABLE usuarios ADD CONSTRAINT usuarios_tipo_usuario_check CHECK (tipo_usuario IN ("
                                + "'ALUMNO','MAESTRO','ADMIN','COORDINADOR_ACADEMICO',"
                                + "'SECRETARIA_ACADEMICA','SECRETARIA_ADMINISTRATIVA','SIN_ROL'))");
                logger.info("✓ usuarios: restricción tipo_usuario actualizada (incluye SIN_ROL)");
            } catch (Exception e) {
                logger.warn("No se pudo actualizar CHECK usuarios.tipo_usuario (H2 u otra BD, o ya aplicado por Flyway): {}",
                        e.getMessage());
            }
        };
    }

    /**
     * Actualiza el CHECK de {@code programas_educativos.tipo_periodo} para incluir nuevos valores del enum
     * (por ejemplo {@code TETRAMESTRE}). En instalaciones viejas, Hibernate dejó el CHECK con la lista previa,
     * y cualquier inserción con el nuevo valor falla con:
     * {@code programas_educativos_tipo_periodo_check}.
     */
    @Bean
    @Order(-1)
    CommandLineRunner permitirTipoPeriodoProgramasActualizado(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE programas_educativos DROP CONSTRAINT IF EXISTS programas_educativos_tipo_periodo_check");
                jdbcTemplate.execute(
                        "ALTER TABLE programas_educativos ADD CONSTRAINT programas_educativos_tipo_periodo_check CHECK (tipo_periodo IN ("
                                + "'SEMANAL','SEMESTRE','CUATRIMESTRE','TETRAMESTRE','TRIMESTRE'))"
                );
                logger.info("✓ programas_educativos: restricción tipo_periodo actualizada (incluye TETRAMESTRE)");
            } catch (Exception e) {
                logger.warn("No se pudo actualizar CHECK programas_educativos.tipo_periodo: {}", e.getMessage());
            }
        };
    }

    /**
     * Añade la columna must_change_password a usuarios si no existe.
     * Necesaria para forzar cambio de contraseña en primer acceso / restablecimiento por admin.
     */
    @Bean
    @Order(-1)
    CommandLineRunner agregarMustChangePasswordUsuarios(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false"
                );
                logger.info("✓ usuarios: columna 'must_change_password' verificada/agregada");
            } catch (Exception e) {
                logger.warn("No se pudo agregar columna must_change_password a usuarios: {}", e.getMessage());
            }
        };
    }

    /**
     * Corrige columnas LOB antiguas (oid) a bytea para Configuración Institucional.
     *
     * Problema típico: una instalación vieja creó {@code certificado_data}/{@code llave_privada_data} como OID (large object),
     * pero el modelo actual los maneja como {@code bytea} (byte[]). PostgreSQL no convierte OID↔BYTEA automáticamente.
     *
     * Conversión: lo_get(oid) -> bytea.
     */
    @Bean
    @Order(-1)
    CommandLineRunner corregirLobsConfiguracionInstitucional(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                String tipoCert = jdbcTemplate.queryForObject(
                        "SELECT data_type FROM information_schema.columns " +
                                "WHERE table_name='configuracion_institucional' AND column_name='certificado_data'",
                        String.class
                );
                String tipoKey = jdbcTemplate.queryForObject(
                        "SELECT data_type FROM information_schema.columns " +
                                "WHERE table_name='configuracion_institucional' AND column_name='llave_privada_data'",
                        String.class
                );

                boolean certEsOid = tipoCert != null && tipoCert.equalsIgnoreCase("oid");
                boolean keyEsOid = tipoKey != null && tipoKey.equalsIgnoreCase("oid");

                if (certEsOid) {
                    try {
                        jdbcTemplate.execute(
                                "ALTER TABLE configuracion_institucional " +
                                        "ALTER COLUMN certificado_data TYPE bytea " +
                                        "USING (CASE WHEN certificado_data IS NULL THEN NULL ELSE lo_get(certificado_data) END)"
                        );
                        logger.info("✓ configuracion_institucional: certificado_data convertido de oid -> bytea");
                    } catch (Exception e) {
                        logger.warn("No se pudo convertir certificado_data oid->bytea: {}", e.getMessage());
                    }
                }
                if (keyEsOid) {
                    try {
                        jdbcTemplate.execute(
                                "ALTER TABLE configuracion_institucional " +
                                        "ALTER COLUMN llave_privada_data TYPE bytea " +
                                        "USING (CASE WHEN llave_privada_data IS NULL THEN NULL ELSE lo_get(llave_privada_data) END)"
                        );
                        logger.info("✓ configuracion_institucional: llave_privada_data convertido de oid -> bytea");
                    } catch (Exception e) {
                        logger.warn("No se pudo convertir llave_privada_data oid->bytea: {}", e.getMessage());
                    }
                }
                if (!certEsOid && !keyEsOid) {
                    logger.info("✓ configuracion_institucional: LOBs ya están en bytea (sin cambios)");
                }
            } catch (Exception e) {
                // En BD sin tabla, o distinta a PostgreSQL, o permisos limitados
                logger.warn("No se pudo verificar/corregir LOBs de configuracion_institucional: {}", e.getMessage());
            }
        };
    }

    /**
     * Permite crear horarios sin docente asignado.
     *
     * En instalaciones existentes, la columna {@code horarios_bloques.maestro_id} puede haber quedado como NOT NULL,
     * pero el flujo actual permite guardar bloques sin maestro y asignarlo después.
     */
    @Bean
    @Order(-1)
    CommandLineRunner permitirMaestroNuloEnHorarios(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE horarios_bloques ALTER COLUMN maestro_id DROP NOT NULL");
                logger.info("✓ horarios_bloques: columna maestro_id permite NULL (DROP NOT NULL)");
            } catch (Exception e) {
                logger.warn("No se pudo modificar horarios_bloques.maestro_id para permitir NULL: {}", e.getMessage());
            }
        };
    }

    /**
     * Cohortes internas (administrativas): permitir que un alumno pertenezca a múltiples cohortes.
     * - Crea tabla puente alumno_cohorte si no existe.
     * - Migra datos legacy desde alumnos.cohorte_id (si existe) a alumno_cohorte.
     *
     * Nota: NO elimina la columna legacy cohorte_id para no perder datos ni romper instalaciones antiguas.
     */
    @Bean
    @Order(-1)
    CommandLineRunner asegurarRelacionMultipleAlumnoCohorte(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS alumno_cohorte (" +
                                "alumno_id BIGINT NOT NULL," +
                                "cohorte_id BIGINT NOT NULL," +
                                "PRIMARY KEY (alumno_id, cohorte_id)" +
                                ")"
                );
                logger.info("✓ alumno_cohorte: tabla puente verificada/creada");
            } catch (Exception e) {
                logger.warn("No se pudo crear/verificar tabla alumno_cohorte: {}", e.getMessage());
            }

            // FKs (pueden fallar si ya existen, o si el usuario no tiene permisos)
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE alumno_cohorte " +
                                "ADD CONSTRAINT alumno_cohorte_alumno_fk FOREIGN KEY (alumno_id) REFERENCES alumnos(id) ON DELETE CASCADE"
                );
            } catch (Exception e) {
                logger.warn("No se pudo agregar FK alumno_cohorte.alumno_id -> alumnos.id: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE alumno_cohorte " +
                                "ADD CONSTRAINT alumno_cohorte_cohorte_fk FOREIGN KEY (cohorte_id) REFERENCES cohortes(id) ON DELETE CASCADE"
                );
            } catch (Exception e) {
                logger.warn("No se pudo agregar FK alumno_cohorte.cohorte_id -> cohortes.id: {}", e.getMessage());
            }

            // Migración desde columna legacy alumnos.cohorte_id (si existe)
            try {
                jdbcTemplate.execute(
                        "INSERT INTO alumno_cohorte(alumno_id, cohorte_id) " +
                                "SELECT a.id, a.cohorte_id " +
                                "FROM alumnos a " +
                                "WHERE a.cohorte_id IS NOT NULL " +
                                "AND NOT EXISTS (" +
                                "  SELECT 1 FROM alumno_cohorte ac WHERE ac.alumno_id = a.id AND ac.cohorte_id = a.cohorte_id" +
                                ")"
                );
                logger.info("✓ alumno_cohorte: migración desde alumnos.cohorte_id aplicada (si existía)");
            } catch (Exception e) {
                logger.warn("No se pudo migrar alumnos.cohorte_id -> alumno_cohorte (puede no existir la columna o no ser compatible): {}", e.getMessage());
            }
        };
    }

    /**
     * Asignación Alumno↔Programa con atributos (periodo ingreso / estatus / progreso) por programa.
     * - Crea tabla puente alumno_programa si no existe.
     * - Migra datos legacy desde alumnos(programa_id, periodo_academico_id, periodo_academico_actual_id, periodo_cursando).
     *   El estatus por programa queda en alumno_programa; filas nuevas migradas aquí usan ACTIVA (columna estatus en alumnos eliminada en V47).
     *
     * Nota: NO elimina columnas legacy en alumnos para no romper instalaciones existentes.
     */
    @Bean
    @Order(-1)
    CommandLineRunner asegurarAlumnoPrograma(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS alumno_programa (" +
                                "alumno_id BIGINT NOT NULL," +
                                "programa_id BIGINT NOT NULL," +
                                "periodo_ingreso_id BIGINT," +
                                "periodo_academico_actual_id BIGINT," +
                                "periodo_cursando INTEGER," +
                                "estatus_matricula VARCHAR(30) NOT NULL DEFAULT 'ACTIVA'," +
                                "fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                                "fecha_actualizacion TIMESTAMP," +
                                "PRIMARY KEY (alumno_id, programa_id)" +
                                ")"
                );
                logger.info("✓ alumno_programa: tabla creada/verificada");
            } catch (Exception e) {
                logger.warn("No se pudo crear/verificar tabla alumno_programa: {}", e.getMessage());
                return;
            }

            // FKs (pueden fallar si ya existen o por permisos)
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE alumno_programa " +
                                "ADD CONSTRAINT alumno_programa_alumno_fk FOREIGN KEY (alumno_id) REFERENCES alumnos(id) ON DELETE CASCADE"
                );
            } catch (Exception e) {
                logger.warn("No se pudo agregar FK alumno_programa.alumno_id -> alumnos.id: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE alumno_programa " +
                                "ADD CONSTRAINT alumno_programa_programa_fk FOREIGN KEY (programa_id) REFERENCES programas_educativos(id) ON DELETE CASCADE"
                );
            } catch (Exception e) {
                logger.warn("No se pudo agregar FK alumno_programa.programa_id -> programas_educativos.id: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE alumno_programa " +
                                "ADD CONSTRAINT alumno_programa_periodo_ing_fk FOREIGN KEY (periodo_ingreso_id) REFERENCES periodo_academico(id) ON DELETE SET NULL"
                );
            } catch (Exception e) {
                logger.warn("No se pudo agregar FK alumno_programa.periodo_ingreso_id -> periodo_academico.id: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE alumno_programa " +
                                "ADD CONSTRAINT alumno_programa_periodo_act_fk FOREIGN KEY (periodo_academico_actual_id) REFERENCES periodo_academico(id) ON DELETE SET NULL"
                );
            } catch (Exception e) {
                logger.warn("No se pudo agregar FK alumno_programa.periodo_academico_actual_id -> periodo_academico.id: {}", e.getMessage());
            }

            // Migrar desde alumnos.* (si existen columnas). Si no, se ignora.
            try {
                jdbcTemplate.execute(
                        "INSERT INTO alumno_programa(alumno_id, programa_id, periodo_ingreso_id, periodo_academico_actual_id, periodo_cursando, estatus_matricula) " +
                                "SELECT a.id, a.programa_id, a.periodo_academico_id, a.periodo_academico_actual_id, a.periodo_cursando, " +
                                "       'ACTIVA' " +
                                "FROM alumnos a " +
                                "WHERE a.programa_id IS NOT NULL " +
                                "AND NOT EXISTS (" +
                                "  SELECT 1 FROM alumno_programa ap WHERE ap.alumno_id = a.id AND ap.programa_id = a.programa_id" +
                                ")"
                );
                logger.info("✓ alumno_programa: migración desde alumnos.* aplicada (si existían datos legacy)");
            } catch (Exception e) {
                logger.warn("No se pudo migrar alumnos.* -> alumno_programa (columnas pueden no existir o no ser compatibles): {}", e.getMessage());
            }
        };
    }

    /**
     * Soporta Evaluación Académica (por Secretaría): la respuesta ya no siempre pertenece a un alumno.
     * - evaluacion_docente_respuesta.alumno_id debe permitir NULL
     * - agregar evaluador_usuario_id (usuarios.id) para identificar a quién respondió (secretaría/admin)
     * - agregar unicidad por (formulario_id, evaluador_usuario_id) para evitar duplicados por usuario
     */
    @Bean
    @Order(-1)
    CommandLineRunner permitirEvaluacionDocentePorUsuario(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE evaluacion_docente_respuesta ALTER COLUMN alumno_id DROP NOT NULL");
                logger.info("✓ evaluacion_docente_respuesta: alumno_id permite NULL (DROP NOT NULL)");
            } catch (Exception e) {
                logger.warn("No se pudo modificar evaluacion_docente_respuesta.alumno_id para permitir NULL: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute("ALTER TABLE evaluacion_docente_respuesta ADD COLUMN IF NOT EXISTS evaluador_usuario_id BIGINT");
                logger.info("✓ evaluacion_docente_respuesta: columna evaluador_usuario_id verificada/agregada");
            } catch (Exception e) {
                logger.warn("No se pudo agregar evaluador_usuario_id a evaluacion_docente_respuesta: {}", e.getMessage());
            }
            try {
                // FK (si la BD/driver lo soporta; en algunos entornos puede fallar por permisos o por ya existir)
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta " +
                                "ADD CONSTRAINT evaluacion_docente_respuesta_evaluador_usuario_fk " +
                                "FOREIGN KEY (evaluador_usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL"
                );
                logger.info("✓ evaluacion_docente_respuesta: FK evaluador_usuario_id -> usuarios.id agregada");
            } catch (Exception e) {
                logger.warn("No se pudo agregar FK evaluacion_docente_respuesta.evaluador_usuario_id: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ed_resp_eval_usuario ON evaluacion_docente_respuesta(evaluador_usuario_id)");
            } catch (Exception e) {
                logger.warn("No se pudo crear índice idx_ed_resp_eval_usuario: {}", e.getMessage());
            }
            try {
                // Ya no usamos UNIQUE(formulario_id, evaluador_usuario_id) porque rompe el modelo por módulos:
                // - Autoevaluación: 1 por (formulario, evaluador, horario_bloque)
                // - Evaluación académica: 1 por (formulario, evaluador, maestro_evaluado)
                // Esos casos se cubren con índices únicos parciales en migrarEvaluacionDocenteRespuestaMultimodulo().
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta DROP CONSTRAINT IF EXISTS uq_ed_resp_form_eval_usuario"
                );
                logger.info("✓ evaluacion_docente_respuesta: constraint uq_ed_resp_form_eval_usuario eliminada/no aplicada");
            } catch (Exception e) {
                logger.warn("No se pudo eliminar constraint uq_ed_resp_form_eval_usuario: {}", e.getMessage());
            }
        };
    }

    /**
     * Calificaciones por criterios: guardar puntos por criterio (captura del docente).
     * Se calcula calificación final sobre 10 a partir del total (0..100) / 10.
     */
    @Bean
    @Order(-1)
    CommandLineRunner asegurarTablaCalificacionCriterioItem(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS calificacion_criterio_item (" +
                                "id BIGSERIAL PRIMARY KEY," +
                                "calificacion_id BIGINT NOT NULL," +
                                "criterio_id BIGINT NOT NULL," +
                                "puntos DOUBLE PRECISION NOT NULL," +
                                "CONSTRAINT fk_calif_crit_cal FOREIGN KEY (calificacion_id) REFERENCES calificaciones(id) ON DELETE CASCADE," +
                                "CONSTRAINT fk_calif_crit_crit FOREIGN KEY (criterio_id) REFERENCES criterios_evaluacion(id) ON DELETE CASCADE" +
                                ")"
                );
                jdbcTemplate.execute(
                        "CREATE UNIQUE INDEX IF NOT EXISTS uq_calif_crit_item ON calificacion_criterio_item(calificacion_id, criterio_id)"
                );
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_calif_crit_cal ON calificacion_criterio_item(calificacion_id)"
                );
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_calif_crit_criterio ON calificacion_criterio_item(criterio_id)"
                );
                logger.info("✓ calificacion_criterio_item: tabla/índices verificados");
            } catch (Exception e) {
                logger.warn("No se pudo crear/verificar calificacion_criterio_item: {}", e.getMessage());
            }
        };
    }

    /**
     * Calificaciones: en instalaciones existentes puede quedar un CHECK viejo
     * que no permite el estado CAPTURADA (flujo: PENDIENTE → CAPTURADA → EN_REVISION → CONFIRMADA).
     * Flyway está deshabilitado, por eso lo forzamos aquí.
     */
    @Bean
    @Order(-1)
    CommandLineRunner permitirEstadoAprobacionCapturadaEnCalificaciones(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                // Compat: si existían registros con estado MODIFICADA (versión vieja), consolidarlos.
                jdbcTemplate.execute("UPDATE calificaciones SET estado_aprobacion = 'CONFIRMADA' WHERE estado_aprobacion = 'MODIFICADA'");
            } catch (Exception e) {
                logger.warn("No se pudo normalizar estado_aprobacion=MODIFICADA en calificaciones: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute("ALTER TABLE calificaciones DROP CONSTRAINT IF EXISTS calificaciones_estado_aprobacion_check");
            } catch (Exception e) {
                logger.warn("No se pudo dropear constraint calificaciones_estado_aprobacion_check: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE calificaciones " +
                                "ADD CONSTRAINT calificaciones_estado_aprobacion_check " +
                                "CHECK (estado_aprobacion IN ('PENDIENTE','CAPTURADA','EN_REVISION','CONFIRMADA'))"
                );
                logger.info("✓ calificaciones: CHECK estado_aprobacion actualizado (incluye CAPTURADA)");
            } catch (Exception e) {
                logger.warn("No se pudo agregar CHECK calificaciones.estado_aprobacion (puede no ser PostgreSQL o ya estar aplicado): {}", e.getMessage());
            }
        };
    }

    /**
     * Compatibilidad: instalaciones viejas pueden tener tabla planteles sin columnas nuevas.
     * La UI de Configuración Institucional depende de estas columnas.
     */
    @Bean
    @Order(-1)
    CommandLineRunner asegurarColumnasPlanteles(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE planteles ADD COLUMN IF NOT EXISTS campus VARCHAR(150)");
                jdbcTemplate.execute("ALTER TABLE planteles ADD COLUMN IF NOT EXISTS clave_cct VARCHAR(10)");
                jdbcTemplate.execute("ALTER TABLE planteles ADD COLUMN IF NOT EXISTS clave_dgair VARCHAR(20)");
                jdbcTemplate.execute("ALTER TABLE planteles ADD COLUMN IF NOT EXISTS id_plantel VARCHAR(50)");
                jdbcTemplate.execute("ALTER TABLE planteles ADD COLUMN IF NOT EXISTS id_entidad_federativa VARCHAR(10)");
                jdbcTemplate.execute("ALTER TABLE planteles ADD COLUMN IF NOT EXISTS entidad_federativa VARCHAR(100)");
                logger.info("✓ planteles: columnas verificada/agregadas (campus, claves, entidad)");
            } catch (Exception e) {
                logger.warn("No se pudieron verificar/agregar columnas de planteles: {}", e.getMessage());
            }
        };
    }

    /**
     * Evaluación docente por módulos (horario): varias respuestas por alumno/formulario (una por horario_bloque).
     * Evaluación académica: varias respuestas por evaluador/formulario (una por docente visitado) con fecha_visita.
     */
    @Bean
    @Order(-2)
    CommandLineRunner migrarEvaluacionDocenteRespuestaMultimodulo(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta DROP CONSTRAINT IF EXISTS evaluacion_docente_respuesta_formulario_id_alumno_id_key");
                logger.info("✓ evaluacion_docente_respuesta: eliminada unicidad antigua (formulario_id, alumno_id) si existía");
            } catch (Exception e) {
                logger.warn("No se pudo eliminar UNIQUE antigua en evaluacion_docente_respuesta: {}", e.getMessage());
            }
            try {
                // Constraint antigua agregada para evaluación académica (1 por usuario),
                // pero choca con el modelo modular/autoevaluación (1 por horario_bloque).
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta DROP CONSTRAINT IF EXISTS uq_ed_resp_form_eval_usuario");
                logger.info("✓ evaluacion_docente_respuesta: eliminada unicidad antigua (formulario_id, evaluador_usuario_id) si existía");
            } catch (Exception e) {
                logger.warn("No se pudo eliminar constraint uq_ed_resp_form_eval_usuario: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute("ALTER TABLE evaluacion_docente_respuesta ALTER COLUMN alumno_id DROP NOT NULL");
                logger.info("✓ evaluacion_docente_respuesta: alumno_id permite NULL");
            } catch (Exception e) {
                logger.warn("No se pudo ALTER alumno_id NULL: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta ADD COLUMN IF NOT EXISTS horario_bloque_id BIGINT");
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta ADD COLUMN IF NOT EXISTS maestro_evaluado_id BIGINT");
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta ADD COLUMN IF NOT EXISTS fecha_visita TIMESTAMP");
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta ADD COLUMN IF NOT EXISTS informe_para_docente TEXT");
                logger.info("✓ evaluacion_docente_respuesta: columnas horario_bloque_id / maestro_evaluado_id / fecha_visita / informe_para_docente");
            } catch (Exception e) {
                logger.warn("No se pudieron agregar columnas nuevas en evaluacion_docente_respuesta: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta ADD CONSTRAINT evaluacion_docente_respuesta_horario_fk "
                                + "FOREIGN KEY (horario_bloque_id) REFERENCES horarios_bloques(id) ON DELETE SET NULL");
                logger.info("✓ evaluacion_docente_respuesta: FK horario_bloque_id");
            } catch (Exception e) {
                logger.warn("No se pudo agregar FK horario_bloque_id (puede existir): {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta ADD CONSTRAINT evaluacion_docente_respuesta_maestro_eval_fk "
                                + "FOREIGN KEY (maestro_evaluado_id) REFERENCES maestros(id) ON DELETE SET NULL");
                logger.info("✓ evaluacion_docente_respuesta: FK maestro_evaluado_id");
            } catch (Exception e) {
                logger.warn("No se pudo agregar FK maestro_evaluado_id (puede existir): {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(
                        "CREATE UNIQUE INDEX IF NOT EXISTS uq_ed_resp_form_alum_horario "
                                + "ON evaluacion_docente_respuesta(formulario_id, alumno_id, horario_bloque_id) "
                                + "WHERE alumno_id IS NOT NULL AND horario_bloque_id IS NOT NULL");
                // Sustituido por evaluación académica por clase (horario): varias visitas por docente vía uq_ed_resp_autoeval_horario.
                jdbcTemplate.execute("DROP INDEX IF EXISTS uq_ed_resp_form_eval_maestro_visita");
                jdbcTemplate.execute(
                        "CREATE UNIQUE INDEX IF NOT EXISTS uq_ed_resp_autoeval_horario "
                                + "ON evaluacion_docente_respuesta(formulario_id, evaluador_usuario_id, horario_bloque_id) "
                                + "WHERE evaluador_usuario_id IS NOT NULL AND horario_bloque_id IS NOT NULL AND alumno_id IS NULL");
                logger.info("✓ evaluacion_docente_respuesta: índices únicos parciales (módulo / eval. académica por horario / autoevaluación)");
            } catch (Exception e) {
                logger.warn("No se pudieron crear índices únicos parciales: {}", e.getMessage());
            }

            // Observaciones por bloque (Evaluación Académica)
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS evaluacion_docente_respuesta_obs_bloque ("
                                + "respuesta_id BIGINT NOT NULL,"
                                + "bloque VARCHAR(200) NOT NULL,"
                                + "texto TEXT,"
                                + "CONSTRAINT fk_ed_resp_obs_resp FOREIGN KEY (respuesta_id) "
                                + "REFERENCES evaluacion_docente_respuesta(id) ON DELETE CASCADE"
                                + ")");
                logger.info("✓ evaluacion_docente_respuesta_obs_bloque: tabla creada/validada");
            } catch (Exception e) {
                logger.warn("No se pudo crear tabla evaluacion_docente_respuesta_obs_bloque: {}", e.getMessage());
            }
        };
    }

    @Bean
    @Order(-3)
    CommandLineRunner agregarNombreCortoInstitucion(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE configuracion_institucional ADD COLUMN IF NOT EXISTS nombre_corto VARCHAR(30)");
                logger.info("✓ configuracion_institucional: columna nombre_corto verificada/agregada");
            } catch (Exception e) {
                logger.warn("No se pudo agregar columna nombre_corto en configuracion_institucional: {}", e.getMessage());
            }
        };
    }

    /**
     * Marca de lectura del informe institucional en portal docente (evaluación académica).
     */
    @Bean
    @Order(-1)
    CommandLineRunner agregarInformeLeidoEnEvaluacionDocenteRespuesta(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE evaluacion_docente_respuesta ADD COLUMN IF NOT EXISTS informe_leido_en TIMESTAMP");
                logger.info("✓ evaluacion_docente_respuesta: columna informe_leido_en verificada/agregada");
            } catch (Exception e) {
                logger.warn("No se pudo agregar informe_leido_en en evaluacion_docente_respuesta: {}", e.getMessage());
            }
        };
    }
}
