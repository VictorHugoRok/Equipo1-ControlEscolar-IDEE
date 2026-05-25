package com.idee.controlescolar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "planteles", uniqueConstraints = @UniqueConstraint(columnNames = "clave_dgp"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plantel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clave_dgp", nullable = false, unique = true, length = 50)
    private String claveDgp;

    /**
     * ID del plantel según catálogo SEP. Se usa como idNombreInstitucion en el XML de certificados electrónicos.
     */
    @Column(name = "id_plantel", length = 50)
    private String idPlantel;

    /**
     * Clave CCT del plantel (10 caracteres alfanuméricos, sin espacios).
     * Se mantiene nullable=true para compatibilidad con registros existentes;
     * el controlador valida estos campos al crear/actualizar.
     */
    @Column(name = "clave_cct", nullable = true, length = 10)
    private String claveCct;

    /**
     * Clave DGAIR asociada al plantel (hasta 20 caracteres).
     */
    @Column(name = "clave_dgair", nullable = true, length = 20)
    private String claveDgair;

    @Column(name = "nombre_plantel", nullable = false, length = 255)
    private String nombrePlantel;

    /**
     * Nombre del campus (usado en XML cuando el plantel es emisor).
     */
    @Column(name = "campus", length = 150)
    private String campus;

    /**
     * Nombre corto/abreviatura para folios (mismo tamaño que campus).
     */
    @Column(name = "nombre_corto", length = 150)
    private String nombreCorto;

    /**
     * Entidad federativa del plantel (catálogo SEP). Se usa para documentos donde el plantel es emisor/base.
     * (En el frontend, la configuración institucional deriva esta información desde el plantel base.)
     */
    @Column(name = "id_entidad_federativa", length = 10)
    private String idEntidadFederativa;

    @Column(name = "entidad_federativa", length = 100)
    private String entidadFederativa;
}
