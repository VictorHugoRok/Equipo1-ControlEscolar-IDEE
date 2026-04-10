package com.idee.controlescolar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_constancia")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SolicitudConstancia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fechaSolicitud;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String documentosSolicitados; // JSON o lista separada por comas

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSolicitud estadoSolicitud = EstadoSolicitud.PENDIENTE;

    private String comprobanteUrl; // URL del comprobante de pago

    @Lob
    @Column(columnDefinition = "TEXT")
    private String motivoRechazo;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @ManyToOne
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    // Enums
    public enum EstadoSolicitud {
        PENDIENTE("Pendiente de Revisión"),
        EN_REVISION("En Revisión - Sec. Admin"),
        APROBADA("Aprobada - Enviada a Sec. Académica"),
        RECHAZADA("Rechazada"),
        EN_PROCESO("En Proceso de Elaboración"),
        LISTA_ENTREGA("Lista para Entrega"),
        ENTREGADA("Entregada al Alumno");

        private final String descripcion;

        EstadoSolicitud(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }
}
