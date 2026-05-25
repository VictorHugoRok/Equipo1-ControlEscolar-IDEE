package com.idee.controlescolar.dto;

import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.Personal;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Alta/edición unificada de personal institucional (incluye datos de docencia y varios roles por usuario).
 */
@Data
public class PersonalStaffRequest {

    @Data
    public static class CedulaProfesionalLineaRequest {
        /** null = fila nueva (archivo opcional en multipart por orden) */
        private Long id;
        /** Etiqueta o nombre para identificar el documento (opcional). */
        private String etiqueta;
        private String numero;
    }

    private Long id;

    private String curp;
    private String nombre;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private String etiqueta;

    private String correoInstitucional;
    private String correoPersonal;
    private String telefono;
    private String codigoPostal;

    /** {@link com.idee.controlescolar.model.Alumno.Sexo#name()} */
    private String sexo;

    /** Fecha de nacimiento (yyyy-MM-dd). */
    private LocalDate fechaNacimiento;

    private Personal.GradoAcademico gradoAcademico;
    /** Primera cédula (compatibilidad); preferir {@link #cedulasProfesionales}. */
    private String cedulaProfesional;

    /** Varias cédulas por usuario; los archivos de filas nuevas van en multipart como {@code cedulaProfesionalArchivo}. */
    private List<CedulaProfesionalLineaRequest> cedulasProfesionales;

    /** Código de puesto (p. ej. SECRETARIA_ACADEMICA); si viene vacío se usa el rol principal. */
    private String puesto;
    private String departamento;

    private String area;
    private Maestro.TipoMaestro tipoMaestro;

    private String rfc;
    private String regimenFiscal;
    private LocalDate fechaAlta;
    private Boolean activo;

    private String observaciones;
    private String nombreContactoEmergencia;
    private String telefonoContactoEmergencia;

    /** Nombres de {@link com.idee.controlescolar.model.Usuario.TipoUsuario}. Si viene vacío, el servicio asigna {@code SIN_ROL}. */
    private List<String> roles;

    /** Si en {@link #roles} se incluye ALUMNO en el alta, deben enviarse datos de expediente estudiantil. */
    private DatosComplementoAlumnoRol datosAlumno;

    /**
     * Si en {@link #roles} se incluye {@code COORDINADOR_ACADEMICO}, programa que coordinará (misma semántica que en PATCH de roles).
     */
    private Long programaCoordinadoId;

    /** Contraseña de acceso (solo alta nueva). Si viene vacío se usa la contraseña por defecto del sistema. */
    private String password;
}
