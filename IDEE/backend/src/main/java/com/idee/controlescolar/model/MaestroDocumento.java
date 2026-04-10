package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "maestro_documentos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaestroDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "maestro_id", nullable = false)
    @JsonIgnoreProperties({"documentos", "asignaturas", "grupos", "horariosImpartidos", "usuario"})
    private Maestro maestro;

    @Column(nullable = false)
    private String filename;

    private String contentType;

    private Long sizeBytes;

    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Lob
    @JsonIgnore
    private byte[] data;
}
