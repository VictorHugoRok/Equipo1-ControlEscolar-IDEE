package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Resumen de generación batch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificadoElectronicoBatchResponse {

    private int solicitados;
    private int procesados;
    private int creados;
    private int fallidos;

    private List<CertificadoElectronicoResponse> certificadosCreados;
    private List<FalloItem> errores;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FalloItem {
        private Long alumnoId;
        private Long programaId;
        private Long plantelId;
        private String mensaje;
    }
}

