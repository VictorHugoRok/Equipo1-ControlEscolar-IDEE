package com.idee.controlescolar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la configuración institucional.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionInstitucionalDTO {

    private Long id;

    @NotBlank(message = "La clave de institución es requerida")
    private String cveInstitucion;

    @NotBlank(message = "El nombre de la institución es requerido")
    private String nombreInstitucion;

    @NotBlank(message = "El ID de entidad federativa es requerido")
    private String idEntidadFederativa;

    @NotBlank(message = "El nombre de la entidad federativa es requerido")
    private String entidadFederativa;

    private String certificadoPath;
    private String llavePrivadaPath;
    private String noCertificadoSat;
    private Boolean activo;
    private boolean tieneCertificados;
}
