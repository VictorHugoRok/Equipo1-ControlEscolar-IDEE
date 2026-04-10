package com.idee.controlescolar.config;

import com.idee.controlescolar.model.*;
import com.idee.controlescolar.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
public class DataLoader {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            try {
                // Verificar si ya existen datos
                if (usuarioRepository.count() > 0) {
                    logger.info("La base de datos ya contiene usuarios. Omitiendo carga de datos inicial.");
                    return;
                }

                logger.info("Iniciando carga de datos de prueba...");

            // Crear usuario ADMIN
            Usuario admin = new Usuario();
            admin.setEmail("admin@idee.edu.mx");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setTipoUsuario(Usuario.TipoUsuario.ADMIN);
            admin.setActivo(true);
            usuarioRepository.save(admin);
            logger.info("✓ Usuario admin creado: admin@idee.edu.mx / admin123");

            // Crear usuario ALUMNO con datos completos
            Usuario usuarioAlumno = new Usuario();
            usuarioAlumno.setEmail("alumno@idee.edu.mx");
            usuarioAlumno.setPassword(passwordEncoder.encode("alumno123"));
            usuarioAlumno.setTipoUsuario(Usuario.TipoUsuario.ALUMNO);
            usuarioAlumno.setActivo(true);

            Alumno alumno = new Alumno();
            alumno.setMatricula("2025001");
            alumno.setNombre("Juan");
            alumno.setApellidoPaterno("Pérez");
            alumno.setApellidoMaterno("García");
            alumno.setCurp("PEGJ000101HDFRXN01");
            alumno.setCorreoInstitucional("alumno@idee.edu.mx");
            alumno.setCorreoPersonal("juan.perez@gmail.com");
            alumno.setTelefono("5551234567");
            alumno.setFechaNacimiento(LocalDate.of(2000, 1, 1));
            alumno.setSexo(Alumno.Sexo.MASCULINO);
            alumno.setTurno(Alumno.Turno.MATUTINO);
            alumno.setEstatusMatricula(Alumno.EstatusMatricula.ACTIVA);
            alumno.setUsuario(usuarioAlumno);

            usuarioAlumno.setAlumno(alumno);
            usuarioRepository.save(usuarioAlumno);
            logger.info("✓ Usuario alumno creado: alumno@idee.edu.mx / alumno123");

            // Crear usuario MAESTRO con datos completos
            Usuario usuarioMaestro = new Usuario();
            usuarioMaestro.setEmail("maestro@idee.edu.mx");
            usuarioMaestro.setPassword(passwordEncoder.encode("maestro123"));
            usuarioMaestro.setTipoUsuario(Usuario.TipoUsuario.MAESTRO);
            usuarioMaestro.setActivo(true);

            Maestro maestro = new Maestro();
            maestro.setNombre("María");
            maestro.setApellidoPaterno("López");
            maestro.setApellidoMaterno("Martínez");
            maestro.setCurp("LOMM750515MDFRXR02");
            maestro.setCorreoInstitucional("maestro@idee.edu.mx");
            maestro.setTelefono("5559876543");
            maestro.setGradoAcademico(Maestro.GradoAcademico.MAESTRIA);
            maestro.setTipoMaestro(Maestro.TipoMaestro.TIEMPO_COMPLETO);
            maestro.setUsuario(usuarioMaestro);

            usuarioMaestro.setMaestro(maestro);
            usuarioRepository.save(usuarioMaestro);
            logger.info("✓ Usuario maestro creado: maestro@idee.edu.mx / maestro123");

            // Crear usuario SECRETARIA_ACADEMICA
            Usuario secAcademica = new Usuario();
            secAcademica.setEmail("secacademica@idee.edu.mx");
            secAcademica.setPassword(passwordEncoder.encode("secacad123"));
            secAcademica.setTipoUsuario(Usuario.TipoUsuario.SECRETARIA_ACADEMICA);
            secAcademica.setActivo(true);

            Personal personalAcad = new Personal();
            personalAcad.setNombre("Ana");
            personalAcad.setApellidoPaterno("Rodríguez");
            personalAcad.setApellidoMaterno("Sánchez");
            personalAcad.setCurp("ROSA850320MDFNXN03");
            personalAcad.setPuesto("Secretaria Académica");
            personalAcad.setDepartamento("Secretaría Académica");
            personalAcad.setCorreoInstitucional("secacademica@idee.edu.mx");
            personalAcad.setTelefono("5551112222");
            personalAcad.setUsuario(secAcademica);

            secAcademica.setPersonal(personalAcad);
            usuarioRepository.save(secAcademica);
            logger.info("✓ Usuario secretaria académica creado: secacademica@idee.edu.mx / secacad123");

            // Crear usuario SECRETARIA_ADMINISTRATIVA
            Usuario secAdmin = new Usuario();
            secAdmin.setEmail("secadmin@idee.edu.mx");
            secAdmin.setPassword(passwordEncoder.encode("secadmin123"));
            secAdmin.setTipoUsuario(Usuario.TipoUsuario.SECRETARIA_ADMINISTRATIVA);
            secAdmin.setActivo(true);

            Personal personalAdmin = new Personal();
            personalAdmin.setNombre("Carlos");
            personalAdmin.setApellidoPaterno("González");
            personalAdmin.setApellidoMaterno("Hernández");
            personalAdmin.setCurp("GOHC800610HDFNXR04");
            personalAdmin.setPuesto("Secretario Administrativo");
            personalAdmin.setDepartamento("Secretaría Administrativa");
            personalAdmin.setCorreoInstitucional("secadmin@idee.edu.mx");
            personalAdmin.setTelefono("5553334444");
            personalAdmin.setUsuario(secAdmin);

            secAdmin.setPersonal(personalAdmin);
            usuarioRepository.save(secAdmin);
            logger.info("✓ Usuario secretaria administrativa creado: secadmin@idee.edu.mx / secadmin123");

            logger.info("========================================");
            logger.info("Datos de prueba cargados exitosamente!");
            logger.info("========================================");
            logger.info("Usuarios disponibles:");
            logger.info("  Admin:      admin@idee.edu.mx / admin123");
            logger.info("  Alumno:     alumno@idee.edu.mx / alumno123");
            logger.info("  Maestro:    maestro@idee.edu.mx / maestro123");
            logger.info("  Sec.Acad:   secacademica@idee.edu.mx / secacad123");
            logger.info("  Sec.Admin:  secadmin@idee.edu.mx / secadmin123");
            logger.info("========================================");

            } catch (Exception e) {
                logger.error("❌ ERROR CRÍTICO durante la carga de datos iniciales:", e);
                logger.error("Mensaje: {}", e.getMessage());
                logger.error("Tipo de error: {}", e.getClass().getSimpleName());
                
                // Imprimir stack trace completo
                e.printStackTrace();
                
                // Log detallado por tipo de error
                String errorType = e.getClass().getName();
                if (errorType.contains("CannotGetJdbcConnectionException") || 
                    errorType.contains("JdbcConnectionException")) {
                    logger.error("🔴 ERROR DE CONEXIÓN: No se puede conectar a PostgreSQL");
                    logger.error("   - Verifica que PostgreSQL está ejecutando en localhost:5432");
                    logger.error("   - Verifica la base de datos: idee_control_escolar");
                    logger.error("   - Verifica credenciales: usuario=postgres, contraseña=malixita");
                } else if (errorType.contains("PersistenceException")) {
                    logger.error("🔴 ERROR DE PERSISTENCIA: Problema con la base de datos");
                    logger.error("   - Verifica que la base de datos existe");
                    logger.error("   - Verifica permisos del usuario postgres");
                } else if (errorType.contains("DataAccessException")) {
                    logger.error("🔴 ERROR DE ACCESO A DATOS: Problema al acceder a la base de datos");
                    logger.error("   - Verifica la conexión y los permisos");
                }
                
                logger.warn("⚠️  Continuando sin datos de prueba. La aplicación seguirá ejecutándose.");
            }
        };
    }
}
