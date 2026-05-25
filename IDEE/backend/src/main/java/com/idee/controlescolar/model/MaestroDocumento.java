package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "maestro_documentos")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MaestroDocumento {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "maestro_id", nullable = false)
    @JsonIgnoreProperties({"documentos", "asignaturas", "grupos", "horariosImpartidos", "usuario"})
    private Maestro maestro;

    /** Clave lógica estable: p. ej. CURP, ANT_0, LEGACY_12. */
    @Column(name = "tipo_documento", nullable = false, length = 128)
    private String tipoDocumento;

    @Column(nullable = false)
    private String filename;

    private String contentType;

    private Long sizeBytes;

    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @JsonIgnore
    @Column(name = "data", columnDefinition = "bytea")
    private byte[] data;
}
