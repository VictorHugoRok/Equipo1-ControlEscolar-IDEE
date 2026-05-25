package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "personal_documentos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonalDocumento {

    public enum Tipo {
        CURP_ARCHIVO,
        INE,
        CSF
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "personal_id", nullable = false)
    @JsonIgnoreProperties({"usuario", "documentos"})
    private Personal personal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Tipo tipo;

    @Column(nullable = false)
    private String filename;

    private String contentType;

    private Long sizeBytes;

    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @JsonIgnore
    @Column
    private byte[] data;
}
