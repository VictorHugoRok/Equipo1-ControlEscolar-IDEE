package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el nodo FirmaResponsable según estándar DOF de Títulos Electrónicos.
 * Contiene la información de la firma digital del responsable de la institución.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirmaResponsableDTO {

    /**
     * Sello digital generado con la llave privada del responsable.
     * Formato: Base64
     * Corresponde al atributo "sello" del estándar DOF.
     */
    private String sello;

    /**
     * Certificado digital del responsable (.cer) en formato Base64.
     * Corresponde al atributo "certificadoResponsable" del estándar DOF.
     */
    private String certificadoResponsable;

    /**
     * Número de serie del certificado del responsable.
     * Formato: Hexadecimal de 20 dígitos
     * Corresponde al atributo "noCertificadoResponsable" del estándar DOF.
     */
    private String noCertificadoResponsable;

    /**
     * CURP del responsable de firma.
     * Corresponde al nodo <Responsable> atributo "curp".
     */
    private String curp;

    /**
     * Identificador del cargo según catálogo SEP.
     * Corresponde al nodo <Responsable> atributo "idCargo".
     */
    private String idCargo;

    /**
     * Abreviatura del título académico del responsable (Dr., Mtro., Lic., etc.)
     * Corresponde al nodo <Responsable> atributo "abrTitulo".
     */
    private String abrTitulo;

    /**
     * Nombre(s) del responsable.
     * Corresponde al nodo <Responsable> atributo "nombre".
     */
    private String nombre;

    /**
     * Primer apellido del responsable.
     * Corresponde al nodo <Responsable> atributo "primerApellido".
     */
    private String primerApellido;

    /**
     * Segundo apellido del responsable.
     * Corresponde al nodo <Responsable> atributo "segundoApellido".
     */
    private String segundoApellido;
}
