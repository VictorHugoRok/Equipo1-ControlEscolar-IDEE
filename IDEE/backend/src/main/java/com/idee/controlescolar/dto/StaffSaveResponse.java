package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta segura para altas/actualizaciones de staff.
 * Evita serializar entidades JPA completas (y ciclos) en endpoints multipart.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffSaveResponse {
    private Long personalId;
    private Long usuarioId;
    private String mensaje;
    private List<DocumentoBasicoMeta> documentos;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentoBasicoMeta {
        private String tipo;     // CURP_ARCHIVO | INE
        private String filename; // nombre original
    }
}

