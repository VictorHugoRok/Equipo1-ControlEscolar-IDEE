package com.idee.controlescolar.config;

import com.idee.controlescolar.model.Personal;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.UsuarioRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicializador de datos maestros para el sistema IDEE-Virtual.
 */
@Configuration
public class DataLoader {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    /** Único usuario inicial: el resto se da de alta desde Usuarios (personal unificado). */
    private static final String EMAIL_ADMIN = "sistemas@idee.edu.mx";
    private static final String PASSWORD_ADMIN = "idee1234";

    @Bean
    @Order(0)
    CommandLineRunner crearUsuariosIniciales(UsuarioRepository usuarioRepository,
                                            PasswordEncoder passwordEncoder) {
        return args -> {
            try {
                logger.info("Verificando existencia de usuarios iniciales...");
                
                crearSiNoExiste(usuarioRepository, passwordEncoder,
                        EMAIL_ADMIN, PASSWORD_ADMIN, Usuario.TipoUsuario.ADMIN,
                        "Sistemas", "Administración", "TI",
                        "SIST000000HDFXXX01", "Administrador de sistemas", "Sistemas");

                logger.info("Finalización de carga de usuarios iniciales (solo administrador).");
            } catch (Exception e) {
                logger.error("CRÍTICO: Error al crear usuarios iniciales: {}", e.getMessage(), e);
            }
        };
    }

    @Transactional // Asegura que se guarde Usuario y Personal como una sola unidad
    private void crearSiNoExiste(UsuarioRepository usuarioRepository,
                                 PasswordEncoder passwordEncoder,
                                 String email,
                                 String passwordPlain,
                                 Usuario.TipoUsuario tipo,
                                 String nombre,
                                 String apellidoPaterno,
                                 String apellidoMaterno,
                                 String curp,
                                 String puesto,
                                 String departamento) {
        
        if (usuarioRepository.existsByEmail(email)) {
            logger.info("Usuario {} ya existe. Omitiendo creación.", email);
            return;
        }

        logger.info("Iniciando creación de usuario: {}...", email);

        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        // ENCRIPTACIÓN: Esta es la parte que evita el 403
        usuario.setPassword(passwordEncoder.encode(passwordPlain));
        usuario.aplicarRolesMultiples(java.util.Collections.singleton(tipo));
        usuario.setActivo(true);

        Personal personal = new Personal();
        personal.setNombre(nombre);
        personal.setApellidoPaterno(apellidoPaterno);
        personal.setApellidoMaterno(apellidoMaterno);
        personal.setCurp(curp);
        personal.setPuesto(puesto);
        personal.setDepartamento(departamento);
        personal.setCorreoInstitucional(email);
        personal.setTelefono("0000000000"); // No dejar nulo por si la DB lo requiere
        
        // Relación bidireccional
        personal.setUsuario(usuario);
        usuario.setPersonal(personal);

        usuarioRepository.save(usuario);
        logger.info("✓ USUARIO CREADO CON ÉXITO: {} | Rol: {}", email, tipo);
    }
}