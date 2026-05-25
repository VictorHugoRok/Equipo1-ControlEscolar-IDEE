package com.idee.controlescolar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlumnoProgramaId implements Serializable {

    @Column(name = "alumno_id")
    private Long alumnoId;

    @Column(name = "programa_id")
    private Long programaId;
}

