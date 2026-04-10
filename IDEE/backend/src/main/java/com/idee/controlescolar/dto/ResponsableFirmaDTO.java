package com.idee.controlescolar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para responsables de firma de títulos electrónicos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponsableFirmaDTO {

    private Long id;

    @NotBlank(message = "El nombre es requerido")
    private String nombre;

    @NotBlank(message = "El primer apellido es requerido")
    private String primerApellido;

    private String segundoApellido;

    @NotBlank(message = "El CURP es requerido")
    @Size(min = 18, max = 18, message = "El CURP debe tener 18 caracteres")
    private String curp;

    @NotBlank(message = "El ID del cargo es requerido")
    private String idCargo;

    @NotBlank(message = "El cargo es requerido")
    private String cargo;

    private String abrTitulo;

    private String certificadoResponsable;
    private String noCertificadoResponsable;

    @NotNull(message = "El estado activo es requerido")
    private Boolean activo;

    @NotNull(message = "El orden de firma es requerido")
    private Integer ordenFirma;

    private String nombreCompleto;
    private boolean tieneCertificado;
}
