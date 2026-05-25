package com.idee.controlescolar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluacionDocenteObservacionBloque {

    @Column(name = "bloque", nullable = false, length = 200)
    private String bloque;

    @Column(name = "texto", columnDefinition = "TEXT")
    private String texto;
}

