package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    /**
     * Rol con mayor privilegio (denormalizado). Se mantiene alineado con {@link #roles} en {@code @PrePersist}/{@code @PreUpdate}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario tipoUsuario;

    @Column(nullable = false)
    private Boolean activo = true;

    /**
     * Si es true, el usuario debe cambiar su contraseña al iniciar sesión.
     * Útil cuando se crea con contraseña genérica o cuando un admin la restablece.
     */
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @OneToOne(mappedBy = "usuario", cascade = CascadeType.ALL)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Alumno alumno;

    @OneToOne(mappedBy = "usuario", cascade = CascadeType.ALL)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Maestro maestro;

    @OneToOne(mappedBy = "usuario", cascade = CascadeType.ALL)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Personal personal;

    /**
     * Programas asignados para usuarios que deben operar acotados por programa
     * (ej. COORDINADOR_ACADEMICO).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "usuario_programa_asignado",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "programa_id")
    )
    @JsonIgnore
    private Set<ProgramaEducativo> programasAsignados = new HashSet<>();

    /**
     * Todos los roles del usuario (relación N:N en tabla {@code usuario_rol}).
     * {@link #tipoUsuario} es el rol de mayor privilegio dentro de este conjunto.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "rol")
    @Enumerated(EnumType.STRING)
    private Set<TipoUsuario> roles = new HashSet<>();

    @PrePersist
    @PreUpdate
    public void sincronizarRolesYColumnaPrincipal() {
        if (roles == null) {
            roles = new HashSet<>();
        }
        if (roles.isEmpty() && tipoUsuario != null) {
            roles.add(tipoUsuario);
        }
        if (!roles.isEmpty()) {
            TipoUsuario primero = roles.stream()
                    .filter(r -> r != null)
                    .min(Comparator.comparingInt(Usuario::ordenPrivilegio))
                    .orElse(null);
            if (primero != null) {
                this.tipoUsuario = primero;
            }
        }
    }

    /** Orden de privilegio (menor = más privilegio). Usado al persistir varios roles. */
    public static int ordenPrivilegio(TipoUsuario t) {
        if (t == null) {
            return 99;
        }
        return switch (t) {
            case ADMIN -> 0;
            case SECRETARIA_ACADEMICA -> 1;
            case COORDINADOR_ACADEMICO -> 2;
            case SECRETARIA_ADMINISTRATIVA -> 3;
            case MAESTRO -> 4;
            case ALUMNO -> 5;
            case SIN_ROL -> 6;
        };
    }

    /**
     * Asigna el conjunto completo de roles (tabla {@code usuario_rol}) y actualiza {@link #tipoUsuario}
     * al de mayor privilegio.
     */
    public void aplicarRolesMultiples(Set<TipoUsuario> seleccion) {
        if (seleccion == null || seleccion.isEmpty()) {
            throw new IllegalArgumentException("Debe indicarse al menos un rol");
        }
        LinkedHashSet<TipoUsuario> distintos = seleccion.stream()
                .filter(r -> r != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distintos.isEmpty()) {
            throw new IllegalArgumentException("Debe indicarse al menos un rol válido");
        }
        List<TipoUsuario> ordenados = distintos.stream()
                .sorted(Comparator.comparingInt(Usuario::ordenPrivilegio))
                .toList();
        this.tipoUsuario = ordenados.get(0);
        this.roles = new HashSet<>(ordenados);
    }

    public Set<TipoUsuario> getRolesEfectivos() {
        if (roles != null && !roles.isEmpty()) {
            return roles.stream()
                    .filter(r -> r != null)
                    .sorted(Comparator.comparingInt(Usuario::ordenPrivilegio))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        LinkedHashSet<TipoUsuario> s = new LinkedHashSet<>();
        if (tipoUsuario != null) {
            s.add(tipoUsuario);
        }
        return s;
    }

    public boolean tieneRol(TipoUsuario r) {
        if (r == null) {
            return false;
        }
        if (roles != null && roles.contains(r)) {
            return true;
        }
        return tipoUsuario != null && tipoUsuario.equals(r);
    }

    // Métodos de UserDetails
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return getRolesEfectivos().stream()
                .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol.name()))
                .toList();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }

    public enum TipoUsuario {
        ALUMNO,
        MAESTRO,
        ADMIN,
        COORDINADOR_ACADEMICO,
        SECRETARIA_ACADEMICA,
        SECRETARIA_ADMINISTRATIVA,
        /** Usuario creado sin perfil operativo hasta que un administrador asigne roles. */
        SIN_ROL
    }
}
