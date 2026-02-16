-- ====================================================
-- Script de Datos Iniciales para IDEE Control Escolar
-- ====================================================

-- NOTA: Este script se ejecuta automáticamente si:
-- spring.jpa.hibernate.ddl-auto=create o create-drop
-- spring.sql.init.mode=always

-- ====================================================
-- USUARIOS DE PRUEBA
-- ====================================================
-- IMPORTANTE: Las contraseñas están hasheadas con BCrypt (costo 10)
-- Usuarios de prueba:
-- 1. alumno@ide.edu.mx / alumno123
-- 2. maestro@ide.edu.mx / maestro123
-- 3. admin@ide.edu.mx / admin123
-- 4. secacademica@ide.edu.mx / secacademica123
-- 5. secadmin@ide.edu.mx / secadmin123
--
-- Hash generado con: BCryptPasswordEncoder con strength 10
-- Verificar: https://www.bcryptcalculator.com/

INSERT INTO usuarios (email, password, tipo_usuario, activo, fecha_creacion, fecha_actualizacion)
VALUES
-- alumno123 hasheado
('alumno@ide.edu.mx', '$2a$10$mCE8y9KLsJYj7gJYGYrKCusm6x0gQMrb8c8Df5yvhkMxYqnbTi.Py', 'ALUMNO', true, NOW(), NOW()),
-- maestro123 hasheado
('maestro@ide.edu.mx', '$2a$10$xJ9F/bXkX3Y7kLmNoOpPoeuK5j8RrHqU2wPqN.9vvYqL4zPvN1pya', 'MAESTRO', true, NOW(), NOW()),
-- admin123 hasheado
('admin@ide.edu.mx', '$2a$10$GvXC5B/Vc1RqZvLmZwQ7CePqmN8xPwUMK.F3dJzZlKwQl6NLa6ioC', 'ADMIN', true, NOW(), NOW()),
-- secacademica123 hasheado
('secacademica@ide.edu.mx', '$2a$10$Qp4U.uC7VdWxYzRqSwT.LeX9m7Y2lJxKqN5pV.jFgZ3TsAoB8zH1i', 'SECRETARIA_ACADEMICA', true, NOW(), NOW()),
-- secadmin123 hasheado
('secadmin@ide.edu.mx', '$2a$10$Rx5V/wD8WeXzaZSrTxU0NfY0m8Z3mKyLoO6qW/kGhA4UtBpC9zI2i', 'SECRETARIA_ADMINISTRATIVA', true, NOW(), NOW());

-- ====================================================
-- PROGRAMAS EDUCATIVOS
-- ====================================================
INSERT INTO programas_educativos (clave, nombre, tipo_programa, duracion_periodos, tipo_periodo, modalidad, creditos_totales, rvoe, fecha_rvoe, estatus, descripcion, fecha_creacion, fecha_actualizacion)
VALUES
('LIS-SIS', 'Licenciatura en Ingeniería en Sistemas Computacionales', 'LICENCIATURA', 8, 'SEMESTRE', 'ESCOLARIZADO', 320, 'RVOE-2025-12345', '2025-01-15', 'ACTIVO', 'Programa enfocado en desarrollo de software y sistemas computacionales', NOW(), NOW()),
('ESP-ODON', 'Especialidad en Odontopediatría', 'ESPECIALIDAD', 4, 'SEMESTRE', 'ESCOLARIZADO', 120, 'RVOE-2024-56789', '2024-08-20', 'ACTIVO', 'Especialización en odontología pediátrica', NOW(), NOW()),
('LIC-ADM', 'Licenciatura en Administración', 'LICENCIATURA', 8, 'SEMESTRE', 'ESCOLARIZADO', 300, 'RVOE-2023-11111', '2023-06-10', 'ACTIVO', 'Programa de administración de empresas', NOW(), NOW());

-- ====================================================
-- ASIGNATURAS (Ejemplo para Ing. Sistemas)
-- ====================================================
INSERT INTO asignaturas (clave, nombre, tipo, periodo, creditos, horas_aula, horas_practica, horas_independientes, estatus, programa_id)
VALUES
('PROG-I', 'Programación I', 'OBLIGATORIA', 1, 8, 3, 2, 3, 'ACTIVA', 1),
('CALC-I', 'Cálculo Diferencial', 'OBLIGATORIA', 1, 8, 3, 2, 2, 'ACTIVA', 1),
('PROG-II', 'Programación II', 'OBLIGATORIA', 2, 8, 3, 2, 3, 'ACTIVA', 1),
('EST-DAT', 'Estructura de Datos', 'OBLIGATORIA', 2, 8, 3, 2, 3, 'ACTIVA', 1);

-- ====================================================
-- MAESTROS
-- ====================================================
INSERT INTO maestros (curp, nombre, apellido_paterno, apellido_materno, etiqueta, correo_institucional, correo_personal, telefono, grado_academico, cedula_profesional, area, tipo_maestro, fecha_alta, activo, usuario_id, fecha_creacion, fecha_actualizacion)
VALUES
('CURPXXXX123456', 'Juan', 'Pérez', 'López', 'Mtro.', 'maestro@ide.edu.mx', 'juan.perez@gmail.com', '9991234567', 'MAESTRIA', '12345678', 'Ingenierías', 'TIEMPO_COMPLETO', '2024-01-15', true, 2, NOW(), NOW());

-- ====================================================
-- ALUMNOS
-- ====================================================
INSERT INTO alumnos (matricula, nombre, apellido_paterno, apellido_materno, curp, correo_institucional, correo_personal, telefono, ciclo_escolar, turno, estatus_matricula, programa_id, usuario_id, fecha_creacion, fecha_actualizacion)
VALUES
('IDEE2025001', 'María', 'López', 'García', 'LOGM001234MYNERRXX', 'alumno@ide.edu.mx', 'maria.lopez@gmail.com', '9999876543', '2025-2029', 'MATUTINO', 'ACTIVA', 1, 1, NOW(), NOW());

-- ====================================================
-- NOTA: Las contraseñas mostradas arriba son hash BCrypt
-- Para generar nuevas contraseñas:
-- - Online: https://bcrypt-generator.com/
-- - En Spring: passwordEncoder.encode("contraseña")
-- ====================================================
