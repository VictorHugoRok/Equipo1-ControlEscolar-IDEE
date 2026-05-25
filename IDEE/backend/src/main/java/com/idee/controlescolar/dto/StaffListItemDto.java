package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffListItemDto {
    private Long personalId;
    private Long maestroId;
    /** Expediente académico (rol ALUMNO). */
    private Long alumnoId;
    private Long usuarioId;
    private String curp;
    private String nombre;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private String etiqueta;
    private String correoInstitucional;
    private String correoPersonal;
    private String telefono;
    private String codigoPostal;
    private String nombreContactoEmergencia;
    private String telefonoContactoEmergencia;
    /** {@link com.idee.controlescolar.model.Alumno.Sexo#name()} */
    private String sexo;
    private LocalDate fechaNacimiento;
    /** Programa académico asociado (si aplica por expediente alumno o coordinador). */
    private Long programaId;
    private String programaNombre;
    private List<String> roles;
    private Boolean activo;
    /** Fila solo desde tabla maestros (sin ficha en personal). */
    private boolean soloMaestroLegacy;
    /** Usuario solo con expediente de alumno (sin ficha de personal). */
    private boolean soloAlumnoSinPersonal;
}
